package app.calorietraining

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PeriodDetailsTest {
    @Test fun recordsLeadToWorkoutAndIgnoreWarmupsAndBodyweight() {
        val date = LocalDate.now()
        val heavier = SessionHistory(3, date, listOf(PreviousSet("20", "8"), PreviousSet("60", "8", true)))
        val earlier = SessionHistory(1, date.minusDays(4), listOf(PreviousSet("15", "8"), PreviousSet("", "10")))
        val record = repetitionRecords(listOf(heavier, earlier)).single()
        assertEquals(8, record.reps)
        assertEquals("20", record.weight.display())
        assertEquals(3L, record.session.id)
    }

    @Test fun proteinCoverageAndBenchRecords() {
        val today = LocalDate.now()
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            val bench = SetDraft(exercise="Жим штанги лёжа", weight="60", reps="8")
            val session = store.newWorkout(today)
            store.saveSet(session, bench)
            store.saveSet(session, bench.copy(weight="62,5"))
            store.saveSet(session, bench.copy(weight="70", warmup=true))
            store.saveSet(session, bench.copy(weight="80"), planned=true)
            store.saveSet(session, bench.copy(reps="5", weight="65"))
            val food = FoodDraft(name="Продукт", weighted=true, grams="150", energy="90", macros=Macros(protein="10"))
            store.save(today,food)
            store.save(today.minusDays(1),food.copy(macros=Macros(protein="0")))
            store.save(today.minusDays(2),food)
            store.save(today.minusDays(2),food.copy(macros=Macros()))
            val stats = store.profileStats(7).current
            assertEquals("7,5",stats.averageProtein?.display())
            assertEquals(2,stats.proteinDays)
            assertEquals(3,stats.foodDays)
            assertEquals(mapOf(5 to "65", 8 to "62,5"),stats.benchRecords.mapValues { it.value.display() })
        }
    }
}
