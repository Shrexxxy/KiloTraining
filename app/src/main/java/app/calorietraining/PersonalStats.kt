package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val clockInput = Regex("""(?:[01][0-9]|2[0-3]):[0-5][0-9]""")
fun validMealTime(value: String) = value.isEmpty() || clockInput.matches(value)
fun defaultMealTime(day: LocalDate) = if (day == LocalDate.now()) LocalTime.now().format(clockFormat) else ""

fun createMeasurements(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE measurement(day TEXT PRIMARY KEY, weight TEXT NOT NULL, fat TEXT NOT NULL)")
}

data class Measurement(val day: LocalDate, val weight: String = "", val fat: String = "") {
    val valid: Boolean get() {
        val percent = fat.amount()
        return (weight.isNotEmpty() || fat.isNotEmpty()) &&
            (weight.isEmpty() || weight.amount()?.signum() == 1) &&
            (fat.isEmpty() || (percent != null && percent > BigDecimal.ZERO && percent <= 100.toBigDecimal()))
    }
}

fun FoodStore.saveMeasurement(record: Measurement) {
    require(record.valid)
    writableDatabase.insertWithOnConflict("measurement", null, ContentValues().apply {
        put("day", record.day.toString())
        put("weight", record.weight)
        put("fat", record.fat)
    }, SQLiteDatabase.CONFLICT_REPLACE).also { if (it == -1L) throw android.database.SQLException("measurement") }
}

/** Замер за день или `null`, если за этот день ничего не записано. */
internal fun FoodStore.measurementOn(day: LocalDate): Measurement? =
    readableDatabase.rawQuery("SELECT weight,fat FROM measurement WHERE day=?", arrayOf(day.toString())).use {
        if (it.moveToFirst()) Measurement(day, it.getString(0), it.getString(1)) else null
    }

/**
 * Сохраняет правку замера из формы: поле, которое пользователь не менял, берётся из базы,
 * чтобы форма, открытая до записи на другом экране, не затёрла чужое значение.
 */
internal fun FoodStore.saveEdited(draft: Measurement, original: Measurement?) {
    val fresh = measurementOn(draft.day)
    val merged = if (fresh == null || original == null) draft else Measurement(
        draft.day,
        if (draft.weight != original.weight) draft.weight else fresh.weight,
        if (draft.fat != original.fat) draft.fat else fresh.fat,
    )
    saveMeasurement(if (merged.valid) merged else draft)
}

/** Записывает только вес дня; жир этого дня остаётся как был. */
fun FoodStore.saveWeight(day: LocalDate, weight: String) {
    require(weight.amount()?.signum() == 1)
    saveField(day, "weight", weight)
}

/** Записывает только жир дня; вес этого дня остаётся как был. */
fun FoodStore.saveFat(day: LocalDate, fat: String) {
    require(Measurement(day, fat = fat).valid && fat.isNotEmpty())
    saveField(day, "fat", fat)
}

// Колонка берётся из двух своих вызовов выше, не из ввода пользователя.
private fun FoodStore.saveField(day: LocalDate, column: String, value: String) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val changed = db.update("measurement", ContentValues().apply { put(column, value) }, "day=?", arrayOf(day.toString()))
        if (changed == 0) {
            val inserted = db.insert("measurement", null, ContentValues().apply {
                put("day", day.toString())
                put("weight", if (column == "weight") value else "")
                put("fat", if (column == "fat") value else "")
            })
            if (inserted == -1L) throw android.database.SQLException("measurement")
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

data class PeriodStats(
    val workouts: Int, val sets: Int, val reps: Long, val volume: BigDecimal,
    val benchSets: Int, val benchAverage: BigDecimal?, val benchMax: BigDecimal?,
    val foodDays: Int, val averageCalories: BigDecimal?,
    val proteinDays: Int = 0, val averageProtein: BigDecimal? = null, val benchRecords: Map<Int, BigDecimal> = emptyMap(),
    val totalMinutes: Long = 0, val timedWorkouts: Int = 0
) {
    val averageMinutes get() = totalMinutes.toBigDecimal().averageBy(timedWorkouts)
}
data class ProfileStats(val current: PeriodStats, val previous: PeriodStats?, val end: LocalDate,
    val hours: List<Int>, val measurements: List<Measurement>, val todayEntries: List<FoodEntry>, val series: ProfileSeries)

private fun BigDecimal.averageBy(count: Int) =
    if (count == 0) null else divide(count.toBigDecimal(), 2, RoundingMode.HALF_UP)

private val benchNames = setOf("жим штанги лежа", "жим лежа", "жим лежа штанги", "жим лежа со штангой")

internal fun isBenchExercise(name: String) = name.trim().lowercase().replace('ё', 'е') in benchNames

private fun FoodStore.periodStats(range: Array<String>): PeriodStats {
    val db = readableDatabase
    var totalMinutes = 0L
    var timedWorkouts = 0
    var workouts = 0
    var lastSession = -1L
    var sets = 0
    var reps = 0L
    var volume = BigDecimal.ZERO
    var benchSum = BigDecimal.ZERO
    var benchCount = 0
    val records = sortedMapOf<Int, BigDecimal>()
    var best: BigDecimal? = null
    db.rawQuery("""SELECT w.id,l.exercise,l.weight,l.reps,l.warmup,w.minutes FROM lifting l JOIN workout w ON w.id=l.session
        WHERE l.planned=0 AND w.day BETWEEN ? AND ? ORDER BY w.id""", range).use { rows ->
        while (rows.moveToNext()) {
            val session = rows.getLong(0)
            if (session != lastSession) {
                workouts++
                lastSession = session
                val minutes = rows.getInt(5)
                if (minutes > 0) {
                    totalMinutes += minutes
                    timedWorkouts++
                }
            }
            val count = rows.getLong(3)
            sets++
            reps += count
            val weight = rows.getString(2).amount() ?: continue
            volume += weight * count.toBigDecimal()
            if (rows.getInt(4) == 0 && isBenchExercise(rows.getString(1))) {
                val repetitions = count.toInt()
                records[repetitions] = records[repetitions]?.max(weight) ?: weight
                benchSum += weight
                benchCount++
                best = best?.max(weight) ?: weight
            }
        }
    }
    var calories = BigDecimal.ZERO
    val proteinByDay = mutableMapOf<String, BigDecimal?>()
    var lastDay = ""
    var foodDays = 0
    db.rawQuery("SELECT day,calories,protein,grams FROM food WHERE day BETWEEN ? AND ? ORDER BY day",range).use { rows ->
        while (rows.moveToNext()) {
            if (lastDay != rows.getString(0)) { lastDay = rows.getString(0); foodDays++ }
            calories += rows.getString(1).toBigDecimal()
            val grams = rows.getString(3)
            val portion = Macros(protein = rows.getString(2)).forPortion(
                if (grams.isEmpty()) BigDecimal.ONE else grams.amount()?.movePointLeft(2))
            val previous = proteinByDay.getOrDefault(lastDay, BigDecimal.ZERO)
            val protein = portion.protein.amount()
            proteinByDay[lastDay] = if (previous == null || protein == null) null else previous + protein
        }
    }
    val completeDays = proteinByDay.values.filterNotNull()
    return PeriodStats(workouts, sets, reps, volume, benchCount, benchSum.averageBy(benchCount), best,
        foodDays, calories.averageBy(foodDays), completeDays.size,
        completeDays.fold(BigDecimal.ZERO, BigDecimal::add).averageBy(completeDays.size), records, totalMinutes, timedWorkouts)
}

fun FoodStore.profileStats(days: Int): ProfileStats {
    val today = LocalDate.now()
    fun rangeEnding(end: LocalDate) = arrayOf(
        if (days == 0) "0001-01-01" else end.minusDays(days - 1L).toString(), end.toString())
    val range = rangeEnding(today)
    val current = periodStats(range)
    val previous = if (days > 0) periodStats(rangeEnding(today.minusDays(days.toLong()))) else null
    val db = readableDatabase
    val hours = MutableList(24) { 0 }
    db.rawQuery("""SELECT hour,COUNT(*) FROM (
        SELECT DISTINCT day,meal,substr(meal_time,1,2) AS hour FROM food
        WHERE meal_time<>'' AND day BETWEEN ? AND ?) GROUP BY hour""",range).use { rows ->
        while (rows.moveToNext()) hours[rows.getInt(0)] = rows.getInt(1)
    }
    val measurements = db.rawQuery("SELECT day,weight,fat FROM measurement ORDER BY day DESC",null).use { rows ->
        buildList { while (rows.moveToNext()) add(Measurement(LocalDate.parse(rows.getString(0)),rows.getString(1),rows.getString(2))) }
    }
    return ProfileStats(current, previous, today, hours, measurements, entries(today), profileSeries(days, today))
}
