package app.calorietraining

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileSeriesTest {
    private val sunday = LocalDate.of(2026, 11, 22)

    @Test fun weekRetainsMissingCalorieDaysAndGoalHistory() = withStore {
        val monday = sunday.minusDays(6)
        seedProfileCalories(this, monday)
        val points = profileSeries(7, sunday).calories
        assertEquals((0L..6L).map(monday::plusDays), points.map { it.day })
        assertEquals(listOf("1800", null, "2400", null, "2000", null, null), points.map { it.kcal?.display() })
        assertEquals(listOf(2200, 2200, 2000, 2000, 2000, 2000, 2000), points.map { it.goal })
    }

    @Test fun macrosAveragePortionsAndRoundSharesToOneHundred() = withStore {
        save(sunday.minusDays(1), FoodDraft(name = "Обед", energy = "1650", macros = Macros("80", "40", "160")))
        save(sunday, FoodDraft(name = "Ужин", energy = "1650", macros = Macros("120", "60", "240")))
        val macros = profileSeries(7, sunday).macros
        assertEquals(listOf("100", "50", "200"), listOf(macros.protein, macros.fat, macros.carbs).map { it?.display() })
        assertEquals(listOf(24, 27, 49), macros.calorieShares)
    }

    @Test fun monthCountsWorkoutsByCalendarWeekIncludingEmptyWeeks() = withStore {
        listOf(26L, 24L, 8L).forEach { addBench(sunday.minusDays(it), "50") }
        val points = profileSeries(30, sunday).workouts
        assertEquals((4L downTo 0L).map { sunday.minusDays(6).minusWeeks(it) }, points.map { it.day })
        assertEquals(listOf(0, 2, 0, 1, 0), points.map { it.count })
    }

    @Test fun longHistoryUsesCalendarMonths() = withStore {
        val start = sunday.minusWeeks(40)
        save(start, FoodDraft(name = "Обед", energy = "1000"))
        save(sunday, FoodDraft(name = "Обед", energy = "2000"))
        val points = profileSeries(0, sunday).calories
        assertEquals(
            generateSequence(start.withDayOfMonth(1)) { it.plusMonths(1) }
                .takeWhile { it <= sunday }.toList(),
            points.map { it.day }
        )
    }

    @Test fun benchUsesHighestWorkingSetInEachSessionInDateOrder() = withStore {
        addBench(sunday.minusDays(1), "52.5")
        val middle = addBench(sunday.minusDays(3), "55")
        saveSet(middle, benchSet("53"))
        saveSet(middle, benchSet("80").copy(warmup = true))
        saveSet(middle, benchSet("90"), planned = true)
        addBench(sunday.minusDays(5), "50")
        val points = profileSeries(7, sunday).bench
        assertEquals(listOf(5L, 3L, 1L).map(sunday::minusDays), points.map { it.day })
        assertEquals(listOf("50", "55", "52,5"), points.map { it.weight.display() })
    }

    @Test fun bmiUsesLatestMeasuredWeightAndProfileHeight() = withStore {
        saveOnboarding(
            UserProfile(Sex.MALE, sunday.year - 32, 182, ActivityLevel.LIGHT, GoalKind.MAINTAIN),
            Measurement(sunday.minusDays(2), "95"), DailyGoals(2200)
        )
        saveMeasurement(Measurement(sunday.minusDays(1), "82"))
        saveMeasurement(Measurement(sunday, fat = "20"))
        val bmi = requireNotNull(profileSeries(7, sunday).bmi)
        assertEquals("24,8", bmi.value.display())
        assertEquals(BigDecimal("18.5") to BigDecimal("25"), bmi.zone)
    }

    private fun withStore(test: FoodStore.() -> Unit) {
        FoodStore(RuntimeEnvironment.getApplication()).use(test)
    }

    private fun FoodStore.addBench(day: LocalDate, weight: String): Long =
        newWorkout(day).also { saveSet(it, benchSet(weight)) }

    private fun benchSet(weight: String) = SetDraft(exercise = "Жим штанги лёжа", weight = weight, reps = "5")
}
