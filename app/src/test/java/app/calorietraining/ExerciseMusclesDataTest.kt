package app.calorietraining

import org.junit.Assert.*
import org.junit.Test

class ExerciseMusclesDataTest {
    @Test fun allCatalogExercisesHaveMusclesAndShortInstructions() {
        assertEquals(57, exerciseCatalog.size)
        exerciseCatalog.forEach { exercise ->
            assertTrue(exercise.name, exercise.technique.primary.isNotEmpty())
            assertTrue(exercise.name, exercise.technique.steps.size in 3..5)
            assertTrue(exercise.name, exercise.technique.steps.none(String::isBlank))
        }
    }

    @Test fun benchPressMatchesFreeExerciseDb() {
        val technique = exerciseCatalog.single { it.name == "Жим штанги лёжа" }.technique
        assertEquals(listOf(Muscle.Chest), technique.primary)
        assertEquals(listOf(Muscle.Shoulders, Muscle.Triceps), technique.secondary)
    }
}
