package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.Reader
import java.io.Writer
import java.time.LocalDate
import java.time.LocalDateTime

private val tables = linkedMapOf(
    "recipe" to listOf("id","name","cooked"),
    "ingredient" to listOf("id","recipe_id","name","grams","energy","protein","fat","carbs"),
    "food" to listOf("id", "day", "name", "energy", "grams", "calories", "meal", "meal_time", "protein", "fat", "carbs"),
    "my_product" to listOf("id", "name", "brand", "barcode", "weighted", "energy", "protein", "fat", "carbs", "source", "search_key", "updated_at"),
    "workout" to listOf("id", "day", "title", "minutes", "started_at", "finished_at", "live"),
    "lifting" to listOf("id", "session", "exercise_group", "exercise", "weight", "reps", "planned", "warmup", "done_at"),
    "workout_template" to listOf("id", "title"),
    "template_set" to listOf("id", "template", "exercise_group", "exercise", "weight", "reps", "warmup"),
    "goal" to listOf("start_day", "kcal", "protein", "fat", "carbs", "carbs_zero"),
    "measurement" to listOf("day", "weight", "fat"),
    "user_profile" to listOf("id", "sex", "birth_year", "height_cm", "activity", "goal_kind", "updated_at")
)

private val legacyProductColumns = listOf("code", "name", "energy", "protein", "fat", "carbs")

private val legacyGoalTables = mapOf(
    "macro_goal" to listOf("id", "protein", "fat", "carbs"),
    "daily_goal" to listOf("id", "kcal"),
)

private val workoutTimeFields = mapOf(
    "workout" to listOf("started_at", "finished_at", "live"),
    "lifting" to listOf("done_at"),
)
private val nullableTimeFields = setOf("started_at", "finished_at", "done_at")

private const val FORMAT_VERSION = 15
private const val FORMAT = "calorietraining/$FORMAT_VERSION"

internal inline fun SQLiteDatabase.atomic(block: () -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally { endTransaction() }
}

private fun SQLiteDatabase.eachRow(table: String, columns: List<String>, consume: (List<String?>) -> Unit) {
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
    if (table != "product" && table != "measurement" && table != "goal") require(field("id").toLong() > 0)
    if (table == "food" || table == "workout") LocalDate.parse(field("day"))
    if (table == "template_set" || table == "lifting") require(field("warmup") in setOf("0", "1"))
    when (table) {
        "recipe" -> require(field("name").isNotBlank() && field("cooked").amount()?.signum() == 1)
        "ingredient" -> require(field("recipe_id").toLong() > 0 && FoodDraft(name=field("name"), weighted=true,
            grams=field("grams"), energy=field("energy"), macros=Macros(field("protein"),field("fat"),field("carbs"))).calories != null)
        "workout" -> {
            require(field("minutes") == "0" || field("minutes").toIntOrNull() in workoutMinutesRange)
            require(field("live") in setOf("0", "1"))
            val start = field("started_at")?.let(LocalDateTime::parse)
            require(start == null || start.toLocalDate().toString() == field("day"))
            require(field("live") == "0" || start != null)
            field("finished_at")?.let(LocalDateTime::parse)
        }
        "workout_template" -> require(field("title").isNotBlank())
        "template_set" -> require(field("template").toLong() > 0 && field("exercise_group").isNotBlank() &&
            SetDraft(exercise = field("exercise"), weight = field("weight"), reps = field("reps")).valid)
        "measurement" -> require(Measurement(LocalDate.parse(field("day")), field("weight"), field("fat")).valid)
        "goal" -> {
            require(LocalDate.parse(field("start_day")).toString() == field("start_day"))
            require(listOf("kcal", "protein", "fat", "carbs").all { field(it).toInt() >= 0 })
            require(field("carbs_zero") == "0" || (field("carbs_zero") == "1" && field("carbs") == "0"))
        }
        "user_profile" -> {
            require(field("id") == "1" && field("height_cm").toInt() in 120..230)
            require(field("birth_year").toInt() >= 1)
            Sex.valueOf(field("sex"))
            ActivityLevel.valueOf(field("activity"))
            GoalKind.valueOf(field("goal_kind"))
            java.time.Instant.parse(field("updated_at"))
        }
        "macro_goal" -> require(field("id") == "1" &&
            listOf("protein", "fat", "carbs").all { field(it).toInt() >= 0 })
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
        "my_product" -> {
            require(field("barcode")?.let(::validBarcode) != false)
            require(field("weighted") in listOf("0", "1"))
            require(field("source") in listOf("manual", "openfoodfacts"))
            require(field("search_key") == field("name").searchableName())
            require(listOf("energy", "protein", "fat", "carbs").all {
                field(it).isEmpty() || field(it).amount() != null
            })
            java.time.Instant.parse(field("updated_at"))
        }
        "lifting" -> {
            require(field("planned") in setOf("0", "1") && field("session").toLong() > 0 && field("exercise_group").isNotBlank() &&
                SetDraft(exercise = field("exercise"), weight = field("weight"), reps = field("reps")).valid)
            field("done_at")?.let {
                require(field("planned") == "0")
                LocalDateTime.parse(it)
            }
        }
    }
}

// До подтверждения данные записываются только в пустую временную базу.
fun FoodStore.readBackup(input: Reader): String {
    val db = writableDatabase
    val seen = mutableSetOf<String>()
    val counts = mutableMapOf<String, Int>()
    val legacyGoals = ContentValues()
    var version = 0
    var missingDuration = false
    var missingWorkoutTitle = false
    var missingMeal = false
    var missingPlan = false
    var missingTime = false
    var missingWarmup = false
    var missingMacros = false
    var missingWorkoutTimes = false
    var hasWorkoutTimes = false
    JsonReader(input).use { json ->
        db.atomic {
            json.beginObject()
            while (json.hasNext()) {
                val key = json.nextName()
                require(seen.add(key))
                if (key == "format") {
                    val format = json.nextString()
                    version = format.substringAfterLast('/').toInt()
                    require(version in 1..FORMAT_VERSION && format == "calorietraining/$version")
                    continue
                }
                val columns = requireNotNull(if (key == "product") legacyProductColumns else tables[key] ?: legacyGoalTables[key])
                if (key == "product") db.execSQL("""CREATE TEMP TABLE legacy_product (
                    code TEXT PRIMARY KEY, name TEXT NOT NULL, energy TEXT NOT NULL,
                    protein TEXT NOT NULL, fat TEXT NOT NULL, carbs TEXT NOT NULL)""")
                json.beginArray()
                var count = 0
                while (json.hasNext()) {
                    val row = ContentValues()
                    json.beginObject()
                    while (json.hasNext()) {
                        val field = json.nextName()
                        require(field in columns && !row.containsKey(field))
                        if ((key == "my_product" && field == "barcode" || field in nullableTimeFields) &&
                            json.peek() == JsonToken.NULL) {
                            json.nextNull()
                            row.putNull(field)
                        } else {
                            require(json.peek() == JsonToken.STRING)
                            row.put(field, json.nextString())
                        }
                    }
                    json.endObject()
                    workoutTimeFields[key].orEmpty().forEach { field ->
                        if (row.containsKey(field)) hasWorkoutTimes = true
                        else {
                            missingWorkoutTimes = true
                            if (field == "live") row.put(field, "0") else row.putNull(field)
                        }
                    }
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
                    if (key == "goal" && !row.containsKey("carbs_zero")) row.put("carbs_zero", "0")
                    require(row.size() == columns.size)
                    validateRow(key, row)
                    if (key in legacyGoalTables) {
                        require(count == 0)
                        legacyGoals.putAll(row)
                    } else db.insertOrThrow(if (key == "product") "legacy_product" else key, null, row)
                    count++
                }
                json.endArray()
                counts[key] = count
            }
            json.endObject()
            val expected = tables.keys.toMutableSet()
            if (version < 15) expected.remove("user_profile")
            if (version < 13) {
                expected.remove("my_product")
                expected.add("product")
            }
            if (version < 12) {
                expected.remove("goal")
                if (version >= 2) expected.add("daily_goal")
                if (version == 11) expected.add("macro_goal")
            }
            if (version < 9) expected.removeAll(listOf("recipe", "ingredient"))
            if (version < 8) expected.removeAll(listOf("workout_template", "template_set"))
            if (version < 6) expected.remove("measurement")
            require(version < 10 || !missingDuration)
            require(version < 8 || !missingWarmup)
            require(version < 7 || !missingMacros)
            require(version < 6 || !missingTime)
            require(version < 5 || !missingPlan)
            require(version < 4 || !missingMeal)
            require(version < 3 || !missingWorkoutTitle)
            require(if (version >= 14) !missingWorkoutTimes else !hasWorkoutTimes)
            require(json.peek() == JsonToken.END_DOCUMENT && seen == expected + "format")
            if (version < 13) {
                db.transferLegacyProducts("legacy_product")
                counts["my_product"] = db.rawQuery("SELECT COUNT(*) FROM my_product", null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
            }
            if (version < 12) {
                fun value(name: String) = legacyGoals.getAsInteger(name) ?: 0
                db.importLegacyGoals(value("kcal"), value("protein"), value("fat"), value("carbs"))
            }
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
    return "Записей питания: ${counts["food"]}. Продуктов: ${counts["my_product"]}. Тренировок: ${counts["workout"]}. Подходов: ${counts["lifting"]}. Замеров: ${counts["measurement"] ?: 0}. Шаблонов: ${counts["workout_template"] ?: 0}. Рецептов: ${counts["recipe"] ?: 0}."
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
