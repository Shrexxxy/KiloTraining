package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.Reader
import java.io.Writer
import java.time.LocalDate

private val tables = linkedMapOf(
    "recipe" to listOf("id","name","cooked"),
    "ingredient" to listOf("id","recipe_id","name","grams","energy","protein","fat","carbs"),
    "food" to listOf("id", "day", "name", "energy", "grams", "calories", "meal", "meal_time", "protein", "fat", "carbs"),
    "product" to listOf("code", "name", "energy", "protein", "fat", "carbs"),
    "workout" to listOf("id", "day", "title", "minutes"),
    "lifting" to listOf("id", "session", "exercise_group", "exercise", "weight", "reps", "planned", "warmup"),
    "workout_template" to listOf("id", "title"),
    "template_set" to listOf("id", "template", "exercise_group", "exercise", "weight", "reps", "warmup"),
    "daily_goal" to listOf("id", "kcal"),
    "measurement" to listOf("day", "weight", "fat")
)

private const val FORMAT = "calorietraining/10"
private const val RECIPE_FORMAT = "calorietraining/9"
private const val TEMPLATE_FORMAT = "calorietraining/8"
private const val MACRO_FORMAT = "calorietraining/7"
private const val TIME_FORMAT = "calorietraining/6"
private const val PLAN_FORMAT = "calorietraining/5"
private const val MEAL_FORMAT = "calorietraining/4"
private const val TITLE_FORMAT = "calorietraining/3"
private const val GOAL_FORMAT = "calorietraining/2"
private const val LEGACY_FORMAT = "calorietraining/1"

internal inline fun SQLiteDatabase.atomic(block: () -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally { endTransaction() }
}

private fun SQLiteDatabase.eachRow(table: String, columns: List<String>, consume: (List<String>) -> Unit) {
    query(table, columns.toTypedArray(), null, null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) consume(columns.indices.map { cursor.getString(it) })
    }
}

fun FoodStore.exportDiary(output: Writer) {
    val db = readableDatabase
    JsonWriter(output).use { json ->
        db.atomic {
            json.beginObject().name("format").value(FORMAT)
            tables.forEach { (table, columns) ->
                json.name(table).beginArray()
                db.eachRow(table, columns) { values ->
                    json.beginObject()
                    columns.zip(values).forEach { (column, value) -> json.name(column).value(value) }
                    json.endObject()
                }
                json.endArray()
            }
            json.endObject()
        }
    }
}

private fun validateRow(table: String, row: ContentValues) {
    fun field(name: String) = row.getAsString(name)
    if (table != "product" && table != "measurement") require(field("id").toLong() > 0)
    if (table == "food" || table == "workout") LocalDate.parse(field("day"))
    if (table == "template_set" || table == "lifting") require(field("warmup") in setOf("0", "1"))
    when (table) {
        "recipe" -> require(field("name").isNotBlank() && field("cooked").amount()?.signum() == 1)
        "ingredient" -> require(field("recipe_id").toLong() > 0 && FoodDraft(name=field("name"), weighted=true,
            grams=field("grams"), energy=field("energy"), macros=Macros(field("protein"),field("fat"),field("carbs"))).calories != null)
        "workout" -> require(field("minutes") == "0" || field("minutes").toIntOrNull() in workoutMinutesRange)
        "workout_template" -> require(field("title").isNotBlank())
        "template_set" -> require(field("template").toLong() > 0 && field("exercise_group").isNotBlank() &&
            SetDraft(exercise = field("exercise"), weight = field("weight"), reps = field("reps")).valid)
        "measurement" -> require(Measurement(LocalDate.parse(field("day")), field("weight"), field("fat")).valid)
        "daily_goal" -> require(field("id") == "1" && field("kcal").toInt() > 0)
        "food" -> {
            Meal.valueOf(field("meal"))
            require(validMealTime(field("meal_time")))
            val grams = field("grams")
            val kcal = FoodDraft(name = field("name"), weighted = grams.isNotEmpty(),
                energy = field("energy"), grams = grams).calories
            require(kcal != null && field("calories").amount()?.compareTo(kcal) == 0)
        }
        "product" -> require(validBarcode(field("code")) && field("energy").amount() != null)
        "lifting" -> require(field("planned") in setOf("0", "1") && field("session").toLong() > 0 && field("exercise_group").isNotBlank() &&
            SetDraft(exercise = field("exercise"), weight = field("weight"), reps = field("reps")).valid)
    }
}

// До подтверждения данные записываются только в пустую временную базу.
fun FoodStore.readBackup(input: Reader): String {
    val db = writableDatabase
    val seen = mutableSetOf<String>()
    val counts = mutableMapOf<String, Int>()
    var format = ""
    var missingDuration = false
    var missingWorkoutTitle = false
    var missingMeal = false
    var missingPlan = false
    var missingTime = false
    var missingWarmup = false
    var missingMacros = false
    JsonReader(input).use { json ->
        db.atomic {
            json.beginObject()
            while (json.hasNext()) {
                val key = json.nextName()
                require(seen.add(key))
                if (key == "format") {
                    format = json.nextString()
                    require(format in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT, TIME_FORMAT, PLAN_FORMAT, MEAL_FORMAT, TITLE_FORMAT, GOAL_FORMAT, LEGACY_FORMAT))
                    continue
                }
                val columns = requireNotNull(tables[key])
                json.beginArray()
                var count = 0
                while (json.hasNext()) {
                    val row = ContentValues()
                    json.beginObject()
                    while (json.hasNext()) {
                        val field = json.nextName()
                        require(field in columns && !row.containsKey(field) && json.peek() == JsonToken.STRING)
                        row.put(field, json.nextString())
                    }
                    json.endObject()
                    if (key == "food" && !row.containsKey("meal_time")) {
                        row.put("meal_time", "")
                        missingTime = true
                    }
                    if (key == "lifting" && !row.containsKey("warmup")) {
                        missingWarmup = true
                        row.put("warmup", "0")
                    }
                    if (key == "lifting" && !row.containsKey("planned")) {
                        missingPlan = true
                        row.put("planned", "0")
                    }
                    if (key == "workout" && !row.containsKey("minutes")) {
                        row.put("minutes", "0")
                        missingDuration = true
                    }
                    if (key == "workout" && !row.containsKey("title")) {
                        missingWorkoutTitle = true
                        row.put("title", "")
                    }
                    if (key == "food" && !row.containsKey("meal")) {
                        missingMeal = true
                        row.put("meal", Meal.UNSPECIFIED.name)
                    }
                    if (key == "food" || key == "product") {
                        for (field in listOf("protein","fat","carbs")) {
                            if (!row.containsKey(field)) {
                                row.put(field, "")
                                missingMacros = true
                            }
                            val value = row.getAsString(field)
                            require(value.isEmpty() || value.amount() != null)
                        }
                    }
                    require(row.size() == columns.size)
                    validateRow(key, row)
                    db.insertOrThrow(key, null, row)
                    count++
                }
                json.endArray()
                counts[key] = count
            }
            json.endObject()
            val expected = tables.keys.toMutableSet()
            if (format == LEGACY_FORMAT) expected.remove("daily_goal")
            if (format !in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT, TIME_FORMAT)) expected.remove("measurement")
            if (format !in setOf(FORMAT, RECIPE_FORMAT)) expected.removeAll(listOf("recipe","ingredient"))
            require(format != FORMAT || !missingDuration)
            if (format !in setOf(FORMAT, RECIPE_FORMAT,TEMPLATE_FORMAT)) expected.removeAll(listOf("workout_template", "template_set"))
            require(format !in setOf(FORMAT, RECIPE_FORMAT,TEMPLATE_FORMAT) || !missingWarmup)
            require(format !in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT) || !missingMacros)
            require(format !in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT, TIME_FORMAT) || !missingTime)
            require(format !in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT, TIME_FORMAT, PLAN_FORMAT, MEAL_FORMAT, TITLE_FORMAT) || !missingWorkoutTitle)
            require(format !in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT, TIME_FORMAT, PLAN_FORMAT, MEAL_FORMAT) || !missingMeal)
            require(format !in setOf(FORMAT, RECIPE_FORMAT, TEMPLATE_FORMAT, MACRO_FORMAT, TIME_FORMAT, PLAN_FORMAT) || !missingPlan)
            require(json.peek() == JsonToken.END_DOCUMENT && seen == expected + "format")
            db.rawQuery("""SELECT 1 FROM ingredient i LEFT JOIN recipe r ON r.id=i.recipe_id WHERE r.id IS NULL
                UNION ALL SELECT 1 FROM recipe r WHERE NOT EXISTS (SELECT 1 FROM ingredient i WHERE i.recipe_id=r.id) LIMIT 1""",
                null).use { require(!it.moveToFirst()) }
            db.rawQuery("""SELECT 1 FROM template_set s LEFT JOIN workout_template t ON t.id=s.template
                WHERE t.id IS NULL LIMIT 1""", null).use { require(!it.moveToFirst()) }
            db.rawQuery("""SELECT 1 FROM workout_template t WHERE NOT EXISTS
                (SELECT 1 FROM template_set s WHERE s.template=t.id) LIMIT 1""", null).use { require(!it.moveToFirst()) }
            db.rawQuery("""SELECT 1 FROM lifting l LEFT JOIN workout w ON w.id=l.session
                WHERE w.id IS NULL LIMIT 1""", null).use { require(!it.moveToFirst()) }
        }
    }
    return "Записей питания: ${counts["food"]}. Продуктов: ${counts["product"]}. Тренировок: ${counts["workout"]}. Подходов: ${counts["lifting"]}. Замеров: ${counts["measurement"] ?: 0}. Шаблонов: ${counts["workout_template"] ?: 0}. Рецептов: ${counts["recipe"] ?: 0}."
}

fun FoodStore.replaceFrom(staged: FoodStore) {
    val db = writableDatabase
    db.atomic {
        tables.keys.reversed().forEach { db.delete(it, null, null) }
        tables.forEach { (name, columns) ->
            staged.readableDatabase.eachRow(name, columns) { values ->
                val row = ContentValues()
                columns.zip(values).forEach { (column, value) -> row.put(column, value) }
                db.insertOrThrow(name, null, row)
            }
        }
    }
}
