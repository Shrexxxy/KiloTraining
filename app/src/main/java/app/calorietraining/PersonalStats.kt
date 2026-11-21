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

data class PeriodStats(
    val workouts: Int, val sets: Int, val reps: Long, val volume: BigDecimal,
    val benchSets: Int, val benchAverage: BigDecimal?, val benchMax: BigDecimal?,
    val foodDays: Int, val averageCalories: BigDecimal?
)
data class ProfileStats(val current: PeriodStats, val previous: PeriodStats?, val end: LocalDate,
    val hours: List<Int>, val measurements: List<Measurement>, val todayEntries: List<FoodEntry> = emptyList())

private fun BigDecimal.averageBy(count: Int) =
    if (count == 0) null else divide(count.toBigDecimal(), 2, RoundingMode.HALF_UP)

private val benchNames = setOf("жим штанги лежа", "жим лежа", "жим лежа штанги", "жим лежа со штангой")

private fun FoodStore.periodStats(range: Array<String>): PeriodStats {
    val db = readableDatabase
    var workouts = 0
    var lastSession = -1L
    var sets = 0
    var reps = 0L
    var volume = BigDecimal.ZERO
    var benchSum = BigDecimal.ZERO
    var benchCount = 0
    var best: BigDecimal? = null
    db.rawQuery("""SELECT w.id,l.exercise,l.weight,l.reps FROM lifting l JOIN workout w ON w.id=l.session
        WHERE l.planned=0 AND w.day BETWEEN ? AND ? ORDER BY w.id""", range).use { rows ->
        while (rows.moveToNext()) {
            val session = rows.getLong(0)
            if (session != lastSession) { workouts++; lastSession = session }
            val count = rows.getLong(3)
            sets++
            reps += count
            val weight = rows.getString(2).amount() ?: continue
            volume += weight * count.toBigDecimal()
            if (rows.getString(1).trim().lowercase().replace('ё','е') in benchNames) {
                benchSum += weight
                benchCount++
                best = best?.max(weight) ?: weight
            }
        }
    }
    var calories = BigDecimal.ZERO
    var lastDay = ""
    var foodDays = 0
    db.rawQuery("SELECT day,calories FROM food WHERE day BETWEEN ? AND ? ORDER BY day",range).use { rows ->
        while (rows.moveToNext()) {
            if (lastDay != rows.getString(0)) { lastDay = rows.getString(0); foodDays++ }
            calories += rows.getString(1).toBigDecimal()
        }
    }
    return PeriodStats(workouts, sets, reps, volume, benchCount, benchSum.averageBy(benchCount), best,
        foodDays, calories.averageBy(foodDays))
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
    return ProfileStats(current, previous, today, hours, measurements, entries(today))
}
