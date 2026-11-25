package app.calorietraining

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleLoadTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val bench = SetDraft(exercise = "Жим штанги лёжа", weight = "60", reps = "8")
    private val custom = bench.copy(exercise = "Мой жим")

    @Test fun threeBenchSetsCountPrimaryAndSecondaryMuscles() {
        val load = muscleLoad(listOf(Workout(1, List(3) { bench }, day = today)), today) {
            photoForExercise(it)?.technique
        }

        assertEquals(mapOf(Muscle.Chest to BigDecimal("3"), Muscle.FrontDelts to BigDecimal("1.5"),
            Muscle.Triceps to BigDecimal("1.5")), load.scores)
        assertEquals(0, load.unknownSets)
    }

    @Test fun weekIncludesSixDaysAgoAndOnlyCompletedWorkingSets() {
        val workouts = listOf(
            Workout(1, listOf(bench), day = today.minusDays(6)),
            Workout(2, listOf(custom.copy(warmup = true), custom.copy(planned = true)), day = today),
            Workout(3, listOf(custom), day = today.minusDays(7)),
            Workout(4, listOf(custom), day = today.plusDays(1)),
        )
        val load = muscleLoad(workouts, today) { photoForExercise(it)?.technique }

        assertEquals(mapOf(Muscle.Chest to BigDecimal.ONE, Muscle.FrontDelts to BigDecimal("0.5"),
            Muscle.Triceps to BigDecimal("0.5")), load.scores)
        assertEquals(0, load.unknownSets)
    }

    @Test fun customSetsUseTheSuppliedTechniqueLookup() {
        val workouts = listOf(Workout(1, List(2) { custom }, day = today))

        val unknown = muscleLoad(workouts, today) { photoForExercise(it)?.technique }
        assertEquals(emptyMap<Muscle, BigDecimal>(), unknown.scores)
        assertEquals(2, unknown.unknownSets)

        val known = muscleLoad(workouts, today) {
            if (it == "Мой жим") ExerciseTechnique(listOf(Muscle.Chest), emptyList(), emptyList()) else null
        }
        assertEquals(mapOf(Muscle.Chest to BigDecimal("2")), known.scores)
        assertEquals(0, known.unknownSets)
    }
}
