package app.calorietraining

import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.previousOrSame

data class CaloriePoint(val day: LocalDate, val kcal: BigDecimal?, val goal: Int?)
data class WorkoutPoint(val day: LocalDate, val count: Int)
data class BenchPoint(val day: LocalDate, val weight: BigDecimal)
data class Bmi(val value: BigDecimal, val zone: Pair<BigDecimal?, BigDecimal?>)
data class ProfileMacros(val protein: BigDecimal?, val fat: BigDecimal?, val carbs: BigDecimal?) {
    val calorieShares: List<Int> get() {
        val calories = listOf(protein to 4, fat to 9, carbs to 4)
            .map { (grams, factor) -> (grams ?: BigDecimal.ZERO) * factor.toBigDecimal() }
        val total = calories.sumOf { it }
        if (total.signum() == 0) return emptyList()
        val shares = calories.map { it.movePointRight(2).divideAndRemainder(total) }
        val rounded = shares.map { it[0].toInt() }.toMutableList()
        shares.indices.sortedByDescending { shares[it][1] }.take(100 - rounded.sum()).forEach { rounded[it]++ }
        return rounded
    }
}

data class ProfileSeries(
    val calories: List<CaloriePoint>, val macros: ProfileMacros, val macroGoals: MacroGoals,
    val workouts: List<WorkoutPoint>, val bench: List<BenchPoint>, val bmi: Bmi?,
    val aboveGoalDays: Int,
)

internal val bmiBoundaries = listOf("18.5", "25", "30").map(String::toBigDecimal)

internal fun profileBmi(heightCm: Int?, weight: BigDecimal?): Bmi? {
    if (heightCm == null || heightCm <= 0 || weight == null) return null
    val value = weight.divide(heightCm.toBigDecimal().movePointLeft(2).pow(2), 1, HALF_UP)
    val index = bmiBoundaries.indexOfFirst { value < it }.let { if (it == -1) bmiBoundaries.size else it }
    return Bmi(value, bmiBoundaries.getOrNull(index - 1) to bmiBoundaries.getOrNull(index))
}

private enum class ProfileBucket {
    DAY, WEEK, MONTH;

    fun start(day: LocalDate): LocalDate = when (this) {
        DAY -> day
        WEEK -> day.with(previousOrSame(DayOfWeek.MONDAY))
        MONTH -> day.withDayOfMonth(1)
    }

    fun next(day: LocalDate): LocalDate = when (this) {
        DAY -> day.plusDays(1)
        WEEK -> day.plusWeeks(1)
        MONTH -> day.plusMonths(1)
    }

    fun dates(from: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(start(from), ::next).takeWhile { it <= end }.toList()
}

private fun List<BigDecimal>.mean(): BigDecimal? =
    takeIf { it.isNotEmpty() }?.let { sumOf { value -> value }.divide(size.toBigDecimal(), 2, HALF_UP) }

internal fun FoodStore.profileSeries(days: Int, end: LocalDate = LocalDate.now()): ProfileSeries {
    val db = readableDatabase
    val from = if (days == 0) LocalDate.of(1, 1, 1) else end.minusDays(days - 1L)
    val range = arrayOf(from.toString(), end.toString())
    val calories = sortedMapOf<LocalDate, BigDecimal>()
    val dailyMacros = mutableMapOf<LocalDate, List<BigDecimal?>>()
    db.rawQuery("SELECT day,calories,grams,protein,fat,carbs FROM food WHERE day BETWEEN ? AND ?", range).use { rows ->
        while (rows.moveToNext()) {
            val day = LocalDate.parse(rows.getString(0))
            calories[day] = (calories[day] ?: BigDecimal.ZERO) + rows.getString(1).toBigDecimal()
            val grams = rows.getString(2)
            val portion = Macros(rows.getString(3), rows.getString(4), rows.getString(5)).forPortion(
                if (grams.isEmpty()) BigDecimal.ONE else grams.amount()?.movePointLeft(2)
            )
            val previous = dailyMacros[day] ?: List(3) { BigDecimal.ZERO }
            dailyMacros[day] = listOf(portion.protein, portion.fat, portion.carbs).mapIndexed { index, text ->
                previous[index]?.let { sum -> text.amount()?.let(sum::add) }
            }
        }
    }

    val sessions = linkedMapOf<Long, LocalDate>()
    val bench = linkedMapOf<Long, BenchPoint>()
    db.rawQuery("""SELECT w.id,w.day,l.exercise,l.weight,l.warmup FROM workout w
        JOIN lifting l ON l.session=w.id WHERE w.day BETWEEN ? AND ? AND l.planned=0
        ORDER BY w.day,w.started_at,w.id""", range).use { rows ->
        while (rows.moveToNext()) {
            val id = rows.getLong(0)
            val day = sessions.getOrPut(id) { LocalDate.parse(rows.getString(1)) }
            if (rows.getInt(4) != 0 || !isBenchExercise(rows.getString(2))) continue
            val weight = rows.getString(3).amount() ?: continue
            val best = bench[id]?.weight
            if (best == null || weight > best) bench[id] = BenchPoint(day, weight)
        }
    }

    val personal = onboardingData(end)
    val first = if (days > 0) from else listOfNotNull(
        calories.keys.firstOrNull(), sessions.values.firstOrNull(),
        db.rawQuery("SELECT MIN(day) FROM measurement WHERE day<=?", arrayOf(end.toString())).use {
            if (it.moveToFirst()) it.getString(0)?.let(LocalDate::parse) else null
        }
    ).minOrNull() ?: end
    val allTimeBucket = if (first.plusWeeks(26) < end) ProfileBucket.MONTH else ProfileBucket.WEEK
    val calorieBucket = if (days == 0) allTimeBucket else ProfileBucket.DAY
    val workoutBucket = when (days) {
        0 -> allTimeBucket
        7 -> ProfileBucket.DAY
        else -> ProfileBucket.WEEK
    }

    val goalChanges = db.rawQuery("SELECT start_day,kcal FROM goal WHERE start_day<=? ORDER BY start_day",
        arrayOf(end.toString())).use { rows ->
        buildList { while (rows.moveToNext()) add(LocalDate.parse(rows.getString(0)) to rows.getInt(1)) }
    }.iterator()
    var nextGoal = if (goalChanges.hasNext()) goalChanges.next() else null
    var goal: Int? = null
    val history = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= end }.map { day ->
        while (true) {
            val change = nextGoal ?: break
            if (change.first > day) break
            goal = change.second.takeIf { it > 0 }
            nextGoal = if (goalChanges.hasNext()) goalChanges.next() else null
        }
        CaloriePoint(day, calories[day], goal)
    }.toList()
    val caloriePoints = history.groupBy { calorieBucket.start(it.day) }.map { (day, points) ->
        CaloriePoint(day, points.mapNotNull { it.kcal }.mean(), points.mapNotNull { it.goal?.toBigDecimal() }
            .mean()?.setScale(0, HALF_UP)?.toInt())
    }
    val workouts = sessions.values.groupingBy(workoutBucket::start).eachCount()
    fun macroAverage(index: Int) = dailyMacros.values.mapNotNull { it[index] }.mean()
    return ProfileSeries(
        caloriePoints, ProfileMacros(macroAverage(0), macroAverage(1), macroAverage(2)), goalsFor(end).macros,
        workoutBucket.dates(first, end).map { WorkoutPoint(it, workouts[it] ?: 0) },
        sessions.keys.mapNotNull(bench::get), profileBmi(personal.profile?.heightCm, personal.measurement?.weight?.amount()),
        history.count { point -> point.kcal != null && point.goal != null && point.kcal > point.goal.toBigDecimal() },
    )
}
