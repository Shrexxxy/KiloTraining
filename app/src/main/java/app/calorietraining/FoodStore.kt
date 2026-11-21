package app.calorietraining

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val decimalInput = Regex("""[0-9]+(?:[.,][0-9]+)?""")

internal fun String.amount(): BigDecimal? =
    trim().takeIf(decimalInput::matches)?.replace(',', '.')?.toBigDecimalOrNull()

fun BigDecimal.display(): String = stripTrailingZeros().toPlainString().replace('.', ',')

enum class Meal(val label: String) {
    BREAKFAST("Завтрак"), LUNCH("Обед"), DINNER("Ужин"), SNACK("Перекус"), UNSPECIFIED("Без категории")
}

data class Macros(val protein: String = "", val fat: String = "", val carbs: String = "") {
    val valid get() = listOf(protein, fat, carbs).all { it.isBlank() || it.amount() != null }

    fun forPortion(factor: BigDecimal?): Macros {
        fun scale(value: String) = factor?.let { value.amount()?.multiply(it) }
            ?.setScale(2, RoundingMode.HALF_UP)?.display().orEmpty()
        return Macros(scale(protein), scale(fat), scale(carbs))
    }

    fun label(): String {
        fun grams(value: String) = if (value.isBlank()) "—" else "$value г"
        return "Б ${grams(protein)} · Ж ${grams(fat)} · У ${grams(carbs)}"
    }
}

fun List<FoodEntry>.dailyMacros(): Macros {
    val portions = map { it.edit().portionMacros }
    fun total(field: (Macros) -> String): String {
        if (portions.isEmpty()) return ""
        var sum = BigDecimal.ZERO
        for (portion in portions) sum += field(portion).amount() ?: return ""
        return sum.display()
    }
    return Macros(total(Macros::protein), total(Macros::fat), total(Macros::carbs))
}

data class FoodDraft(
    val id: Long? = null,
    val name: String = "",
    val weighted: Boolean = false,
    val energy: String = "",
    val grams: String = "",
    val barcode: String? = null,
    val meal: Meal = Meal.UNSPECIFIED,
    val mealTime: String = "",
    val macros: Macros = Macros()
) {
    val portionMacros get() = macros.forPortion(if (weighted) grams.amount()?.movePointLeft(2) else BigDecimal.ONE)
    val calories: BigDecimal?
        get() {
            if (!macros.valid || name.isBlank() || !validMealTime(mealTime)) return null
            val kcal = energy.amount() ?: return null
            val portion = if (weighted) {
                val weight = grams.amount()?.takeIf { it.signum() > 0 } ?: return null
                (weight * kcal).movePointLeft(2)
            } else kcal
            return portion.setScale(2, RoundingMode.HALF_UP)
        }
}

data class FoodEntry(
    val id: Long,
    val name: String,
    val energy: String,
    val grams: String,
    val calories: BigDecimal,
    val meal: Meal = Meal.UNSPECIFIED,
    val mealTime: String = "",
    val macros: Macros = Macros()
) {
    fun edit() = FoodDraft(id, name, grams.isNotEmpty(), energy, grams, meal = meal, mealTime = mealTime, macros = macros)
}

class FoodStore(context: Context, databaseName: String = "food.db") : SQLiteOpenHelper(context, databaseName, null, 8) {
    private fun createGoalTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE daily_goal (id INTEGER PRIMARY KEY CHECK(id=1), kcal INTEGER NOT NULL CHECK(kcal>0))")
    }

    fun dailyGoal(): Int? = readableDatabase.rawQuery("SELECT kcal FROM daily_goal WHERE id=1", null).use {
        if (it.moveToFirst()) it.getInt(0) else null
    }

    fun setDailyGoal(kcal: Int?) {
        if (kcal == null) writableDatabase.delete("daily_goal", null, null)
        else {
            require(kcal > 0)
            writableDatabase.execSQL("INSERT OR REPLACE INTO daily_goal(id,kcal) VALUES(1,?)", arrayOf(kcal))
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE food (
            id INTEGER PRIMARY KEY, day TEXT NOT NULL, name TEXT NOT NULL,
            energy TEXT NOT NULL, grams TEXT NOT NULL, calories TEXT NOT NULL, meal TEXT NOT NULL DEFAULT 'UNSPECIFIED', meal_time TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("CREATE INDEX food_day ON food(day)")
        createJournalTables(db)
        createGoalTable(db)
        createMeasurements(db)
        addMacroColumns(db)
    }

    private fun addMacroColumns(db: SQLiteDatabase) {
        for (table in listOf("food", "product")) for (field in listOf("protein", "fat", "carbs")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $field TEXT NOT NULL DEFAULT ''")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE food ADD COLUMN meal_time TEXT NOT NULL DEFAULT ''")
            createMeasurements(db)
        }
        if (oldVersion < 2) createJournalTables(db)
        if (oldVersion < 3) createGoalTable(db)
        if (oldVersion in 2..3) db.execSQL("ALTER TABLE workout ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 5) db.execSQL("ALTER TABLE food ADD COLUMN meal TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
        if (oldVersion in 2..5) db.execSQL("ALTER TABLE lifting ADD COLUMN planned INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 8) addMacroColumns(db)
    }

    fun entries(day: LocalDate): List<FoodEntry> = readableDatabase.rawQuery(
        "SELECT id, name, energy, grams, calories, meal, meal_time,protein,fat,carbs FROM food WHERE day = ? ORDER BY id",
        arrayOf(day.toString())
    ).use { rows ->
        buildList {
            while (rows.moveToNext()) {
                add(FoodEntry(rows.getLong(0), rows.getString(1), rows.getString(2),
                    rows.getString(3), rows.getString(4).toBigDecimal(), Meal.valueOf(rows.getString(5)), rows.getString(6),
                    Macros(rows.getString(7),rows.getString(8),rows.getString(9))))
            }
        }
    }

    fun foodSuggestions(day: LocalDate? = null, limit: Int = Int.MAX_VALUE): List<FoodDraft> = readableDatabase.query(
        "food", arrayOf("name", "energy", "grams", "protein", "fat", "carbs"), day?.let { "day <= ?" },
        day?.let { arrayOf(it.toString()) }, null, null, "day DESC, id DESC"
    ).use { rows ->
        val seen = mutableSetOf<Pair<String, Boolean>>()
        buildList {
            while (size < limit && rows.moveToNext()) {
                val name = rows.getString(0)
                val weighted = rows.getString(2).isNotEmpty()
                if (seen.add(name.trim().lowercase() to weighted)) {
                    add(FoodDraft(name = name, weighted = weighted, energy = rows.getString(1),
                        macros = Macros(rows.getString(3),rows.getString(4),rows.getString(5))))
                }
            }
        }
    }

    fun save(day: LocalDate, draft: FoodDraft) {
        val values = ContentValues().apply {
            put("protein", draft.macros.protein.trim())
            put("fat", draft.macros.fat.trim())
            put("carbs", draft.macros.carbs.trim())
            put("day", day.toString())
            put("name", draft.name.trim())
            put("meal", draft.meal.name)
            put("meal_time", draft.mealTime)
            put("energy", draft.energy.trim())
            put("grams", if (draft.weighted) draft.grams.trim() else "")
            put("calories", requireNotNull(draft.calories).toPlainString())
        }
        draft.barcode?.let { code -> cacheProduct(code, Product(draft.name.trim(), draft.energy.trim(), draft.macros)) }
        if (draft.id == null) writableDatabase.insertOrThrow("food", null, values)
        else writableDatabase.update("food", values, "id = ? AND day = ?", arrayOf(draft.id.toString(), day.toString()))
    }

    fun copyMeal(source: LocalDate, destination: LocalDate, meal: Meal) {
        writableDatabase.execSQL(
            """INSERT INTO food(day, name, energy, grams, calories, meal, protein, fat, carbs)
               SELECT ?, name, energy, grams, calories, meal, protein, fat, carbs FROM food
               WHERE day = ? AND meal = ? ORDER BY id""",
            arrayOf(destination.toString(), source.toString(), meal.name)
        )
    }

    fun delete(day: LocalDate, id: Long) {
        writableDatabase.delete("food", "id = ? AND day = ?", arrayOf(id.toString(), day.toString()))
    }
}
