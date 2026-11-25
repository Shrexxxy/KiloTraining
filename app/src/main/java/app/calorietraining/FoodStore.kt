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

internal fun foodSuggestionKey(name: String, weighted: Boolean) = name.trim().lowercase() to weighted

enum class Meal(val label: String) {
    BREAKFAST("Завтрак"), LUNCH("Обед"), DINNER("Ужин"), SNACK("Перекус"), UNSPECIFIED("Без категории")
}

/** Необязательные дневные цели по макронутриентам в граммах. */
data class MacroGoals(val protein: Int? = null, val fat: Int? = null, val carbs: Int? = null)

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
    val macros: Macros = Macros(),
    val brand: String = "",
    val origin: ProductOrigin? = null,
    val fromRecipe: Boolean = false,
    val venueName: String = "",
    val photoPath: String? = null,
    val photoHash: String? = null
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
    val macros: Macros = Macros(),
    val venueName: String = "",
    val photoPath: String? = null,
    val photoHash: String? = null
) {
    fun edit() = FoodDraft(id, name, grams.isNotEmpty(), energy, grams, meal = meal, mealTime = mealTime,
        macros = macros, venueName = venueName, photoPath = photoPath, photoHash = photoHash)
}

/** Дневные цели по калориям и макронутриентам. */
data class DailyGoals(val kcal: Int? = null, val macros: MacroGoals = MacroGoals())

private fun SQLiteDatabase.hasTable(name: String): Boolean =
    rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

internal fun SQLiteDatabase.importLegacyGoals(kcal: Int, protein: Int, fat: Int, carbs: Int) {
    if (kcal == 0 && protein == 0 && fat == 0 && carbs == 0) return
    val firstDay = if (hasTable("food")) rawQuery("SELECT MIN(day) FROM food", null).use {
        it.moveToFirst()
        it.getString(0)
    } else null
    execSQL("INSERT INTO goal(start_day,kcal,protein,fat,carbs) VALUES(?,?,?,?,?)",
        arrayOf<Any>(firstDay ?: LocalDate.now().toString(), kcal, protein, fat, carbs))
}

class FoodStore(internal val context: Context, databaseName: String = "food.db",
    internal val clock: java.time.Clock = java.time.Clock.systemDefaultZone(),
) : SQLiteOpenHelper(context, databaseName, null, 21) {
    internal val unusedPhotos = mutableSetOf<String>()
    private fun createLegacyCalorieTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE daily_goal (id INTEGER PRIMARY KEY CHECK(id=1), kcal INTEGER NOT NULL CHECK(kcal>0))")
    }

    private fun createLegacyMacroTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE macro_goal (
            id INTEGER PRIMARY KEY CHECK(id=1),
            protein INTEGER NOT NULL DEFAULT 0 CHECK(protein>=0),
            fat INTEGER NOT NULL DEFAULT 0 CHECK(fat>=0),
            carbs INTEGER NOT NULL DEFAULT 0 CHECK(carbs>=0)
        )""")
    }

    private fun createGoalTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE goal (
            start_day TEXT PRIMARY KEY,
            kcal INTEGER NOT NULL DEFAULT 0 CHECK(kcal>=0),
            protein INTEGER NOT NULL DEFAULT 0 CHECK(protein>=0),
            fat INTEGER NOT NULL DEFAULT 0 CHECK(fat>=0),
            carbs INTEGER NOT NULL DEFAULT 0 CHECK(carbs>=0)
        )""")
    }

    /** Возвращает цели, действовавшие в выбранный день. */
    fun goalsFor(day: LocalDate): DailyGoals = readableDatabase.rawQuery(
        "SELECT kcal,protein,fat,carbs,carbs_zero FROM goal WHERE start_day <= ? ORDER BY start_day DESC LIMIT 1",
        arrayOf(day.toString())
    ).use { row ->
        fun value(index: Int) = row.getInt(index).takeIf { it > 0 }
        if (row.moveToFirst()) DailyGoals(value(0), MacroGoals(value(1), value(2), if (row.getInt(4) == 1) 0 else value(3))) else DailyGoals()
    }

    /** Устанавливает цели с указанного дня. Пустая запись завершает действие предыдущих целей. */
    fun setGoals(from: LocalDate, goals: DailyGoals) {
        val (protein, fat, carbs) = goals.macros
        require(listOfNotNull(goals.kcal, protein, fat).all { it > 0 } && (carbs == null || carbs >= 0))
        writableDatabase.execSQL("INSERT OR REPLACE INTO goal(start_day,kcal,protein,fat,carbs,carbs_zero) VALUES(?,?,?,?,?,?)",
            arrayOf<Any>(from.toString(), goals.kcal ?: 0, protein ?: 0, fat ?: 0, carbs ?: 0, if (carbs == 0) 1 else 0))
    }

    private fun migrateGoals(db: SQLiteDatabase) {
        createGoalTable(db)
        val kcal = if (db.hasTable("daily_goal")) db.rawQuery("SELECT kcal FROM daily_goal WHERE id=1", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } else 0
        db.rawQuery("SELECT protein,fat,carbs FROM macro_goal WHERE id=1", null).use {
            if (it.moveToFirst()) db.importLegacyGoals(kcal, it.getInt(0), it.getInt(1), it.getInt(2))
            else db.importLegacyGoals(kcal, 0, 0, 0)
        }
        db.execSQL("DROP TABLE IF EXISTS daily_goal")
        db.execSQL("DROP TABLE macro_goal")
    }

    private fun SQLiteDatabase.createExerciseMuscles() {
        execSQL("""CREATE TABLE exercise_muscle (
            exercise_key TEXT NOT NULL,
            muscle TEXT NOT NULL,
            is_primary INTEGER NOT NULL CHECK(is_primary IN (0,1)),
            PRIMARY KEY(exercise_key, muscle)
        )""")
    }

    /** Заменяет прежние «плечи» тремя дельтами с той же ролью; запись остаётся в одной транзакции обновления. */
    private fun SQLiteDatabase.splitShoulders() {
        execSQL("""INSERT OR IGNORE INTO exercise_muscle(exercise_key, muscle, is_primary)
            SELECT e.exercise_key, d.muscle, e.is_primary FROM exercise_muscle e
            CROSS JOIN (SELECT 'FrontDelts' AS muscle UNION ALL SELECT 'SideDelts' UNION ALL SELECT 'RearDelts') d
            WHERE e.muscle = 'Shoulders'""")
        execSQL("DELETE FROM exercise_muscle WHERE muscle = 'Shoulders'")
    }

    /** Возвращает выбранные мышцы своих упражнений по нормализованным названиям. */
    internal fun customMuscles(): Map<String, ExerciseTechnique> = readableDatabase.rawQuery(
        "SELECT exercise_key, muscle, is_primary FROM exercise_muscle ORDER BY exercise_key, muscle", null,
    ).use { rows ->
        val byExercise = mutableMapOf<String, MutableMap<Muscle, Boolean>>()
        while (rows.moveToNext()) {
            val key = rows.getString(0)
            if (photoForExercise(key) != null) continue
            byExercise.getOrPut(key) { mutableMapOf() }[Muscle.valueOf(rows.getString(1))] = rows.getInt(2) == 1
        }
        byExercise.mapValues { (_, roles) ->
            ExerciseTechnique(
                primary = Muscle.entries.filter { roles[it] == true },
                secondary = Muscle.entries.filter { roles[it] == false },
                steps = emptyList(),
            )
        }
    }

    /** Заменяет выбор мышц для своего упражнения. Пустой выбор удаляет запись. */
    internal fun saveCustomMuscles(name: String, primary: List<Muscle>, secondary: List<Muscle>) {
        val key = name.searchKey()
        if (key.isEmpty() || photoForExercise(key) != null) return
        val roles = secondary.associateWith { 0 } + primary.associateWith { 1 }
        val db = writableDatabase
        db.atomic {
            db.delete("exercise_muscle", "exercise_key=?", arrayOf(key))
            roles.forEach { (muscle, isPrimary) ->
                db.execSQL("INSERT INTO exercise_muscle(exercise_key, muscle, is_primary) VALUES(?,?,?)",
                    arrayOf<Any>(key, muscle.name, isPrimary))
            }
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
        createTemplateTables(db)
        createRecipeTables(db)
        addDuration(db)
        createMyProductsOnOpen(db)
        addWorkoutTimes(db)
        createUserProfileAndZeroCarbs(db)
        db.createVenues()
        db.createExerciseMuscles()
        db.addPhotoHashes()
    }

    private fun createUserProfileAndZeroCarbs(db: SQLiteDatabase) {
        db.createUserProfile()
        // В существующих целях 0 обозначает отсутствие цели по этому макронутриенту.
        db.execSQL("ALTER TABLE goal ADD COLUMN carbs_zero INTEGER NOT NULL DEFAULT 0 CHECK(carbs_zero IN (0,1))")
    }

    private fun createMyProductsOnOpen(db: SQLiteDatabase) {
        db.createMyProducts()
        db.transferLegacyProducts("product")
    }

    private fun addDuration(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE workout ADD COLUMN minutes INTEGER NOT NULL DEFAULT 0 CHECK(minutes BETWEEN 0 AND 1440)")
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
        if (oldVersion < 3) createLegacyCalorieTable(db)
        if (oldVersion in 2..3) db.execSQL("ALTER TABLE workout ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 5) db.execSQL("ALTER TABLE food ADD COLUMN meal TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
        if (oldVersion in 2..5) db.execSQL("ALTER TABLE lifting ADD COLUMN planned INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 8) addMacroColumns(db)
        if (oldVersion < 9) createTemplateTables(db)
        if (oldVersion < 10) createRecipeTables(db)
        if (oldVersion < 11) addDuration(db)
        if (oldVersion < 12) createLegacyMacroTable(db)
        if (oldVersion < 13) migrateGoals(db)
        if (oldVersion < 14) createMyProductsOnOpen(db)
        if (oldVersion == 14) db.execSQL("ALTER TABLE my_product ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 1")
        if (oldVersion < 16) addWorkoutTimes(db)
        if (oldVersion < 17) createUserProfileAndZeroCarbs(db)
        if (oldVersion < 18) db.createVenues()
        if (oldVersion < 19) db.createExerciseMuscles()
        if (oldVersion < 20) db.addPhotoHashes()
        if (oldVersion < 21) db.splitShoulders()
    }

    fun entries(day: LocalDate): List<FoodEntry> = readableDatabase.rawQuery(
        """SELECT f.id, f.name, energy, grams, calories, meal, meal_time, protein, fat, carbs, v.name, photo_path, photo_hash
           FROM food f LEFT JOIN venue v ON f.venue_id = v.id WHERE day = ? ORDER BY f.id""",
        arrayOf(day.toString())
    ).use { rows ->
        buildList {
            while (rows.moveToNext()) {
                add(FoodEntry(rows.getLong(0), rows.getString(1), rows.getString(2),
                    rows.getString(3), rows.getString(4).toBigDecimal(), Meal.valueOf(rows.getString(5)), rows.getString(6),
                    Macros(rows.getString(7),rows.getString(8),rows.getString(9)), rows.getString(10).orEmpty(), rows.getString(11), rows.getString(12)))
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
                if (seen.add(foodSuggestionKey(name, weighted))) {
                    add(FoodDraft(name = name, weighted = weighted, energy = rows.getString(1),
                        macros = Macros(rows.getString(3),rows.getString(4),rows.getString(5))))
                }
            }
        }
    }

    fun frequentFoods(day: LocalDate, limit: Int = 5): List<FoodDraft> = readableDatabase.query(
        "food", arrayOf("name", "energy", "grams", "protein", "fat", "carbs"),
        "day BETWEEN ? AND ?", arrayOf(day.minusDays(59).toString(), day.toString()),
        null, null, "day DESC, id DESC"
    ).use { rows ->
        val foods = linkedMapOf<Pair<String, Boolean>, MutableList<FoodDraft>>()
        while (rows.moveToNext()) {
            val name = rows.getString(0)
            val weighted = rows.getString(2).isNotEmpty()
            val matches = foods.getOrPut(foodSuggestionKey(name, weighted)) { mutableListOf() }
            matches.add(FoodDraft(name = name, weighted = weighted, energy = rows.getString(1),
                macros = Macros(rows.getString(3), rows.getString(4), rows.getString(5))))
        }
        foods.values.filter { it.size > 1 }.sortedByDescending { it.size }.take(limit).map { it.first() }
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
        val db = writableDatabase
        db.atomic {
            val oldPhoto = draft.id?.let { id ->
                db.rawQuery("SELECT photo_path, photo_hash FROM food WHERE id = ? AND day = ?", arrayOf(id.toString(), day.toString()))
                    .use { rows ->
                        if (!rows.moveToFirst()) return@atomic
                        rows.getString(0) to rows.getString(1)
                    }
            }
            values.put("venue_id", db.findOrCreateVenue(draft.venueName, clock.millis()))
            val path = draft.photoPath ?: oldPhoto?.first.takeIf {
                draft.photoHash != null && draft.photoHash == oldPhoto?.second
            }
            values.put("photo_path", path)
            values.put("photo_hash", context.photoHash(path) ?: when {
                oldPhoto != null && oldPhoto.first == path -> oldPhoto.second
                draft.id == null -> draft.photoHash?.takeIf(::validPhotoHash)
                else -> null
            })
            val saved = if (draft.id == null) {
                db.insertOrThrow("food", null, values)
                true
            } else db.update("food", values, "id = ? AND day = ?", arrayOf(draft.id.toString(), day.toString())) > 0
            if (saved) oldPhoto?.first?.takeIf { it != path }?.let(unusedPhotos::add)
            if (saved && !draft.fromRecipe) {
                val origin = draft.origin
                val source = if (origin != null && draft.name == origin.name &&
                    draft.energy == origin.energy && draft.macros == origin.macros &&
                    draft.brand == origin.brand && draft.barcode == origin.barcode && draft.weighted == origin.weighted) origin.source else "manual"
                db.upsertCard(MyProduct(name = draft.name, brand = draft.brand, barcode = draft.barcode,
                    weighted = draft.weighted, energy = draft.energy, macros = draft.macros, source = source))
            }
        }
        cleanUnusedPhotos()
    }

    fun copyMeal(source: LocalDate, destination: LocalDate, meal: Meal) {
        writableDatabase.execSQL(
            """INSERT INTO food(day, name, energy, grams, calories, meal, protein, fat, carbs, venue_id, photo_path, photo_hash)
               SELECT ?, name, energy, grams, calories, meal, protein, fat, carbs, venue_id, photo_path, photo_hash FROM food
               WHERE day = ? AND meal = ? ORDER BY id""",
            arrayOf(destination.toString(), source.toString(), meal.name)
        )
    }

    fun delete(day: LocalDate, id: Long) {
        val db = writableDatabase
        db.atomic {
            db.entryPhoto(day, id)?.let(unusedPhotos::add)
            db.delete("food", "id = ? AND day = ?", arrayOf(id.toString(), day.toString()))
        }
        cleanUnusedPhotos()
    }
}
