package app.calorietraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    private fun names(query: String) = searchExercises(query).map { it.name }.toSet()

    @Test fun catalogHasUniqueNamesAndTwoFramesEach() {
        assertEquals(57, exerciseCatalog.size)
        val keys = exerciseCatalog.map { it.name.lowercase().replace('ё', 'е') }
        assertEquals(keys.size, keys.toSet().size)
        exerciseCatalog.forEach { assertNotEquals(it.name, it.first, it.second) }
        assertEquals(exerciseCatalog.size * 2, exerciseCatalog.flatMap { listOf(it.first, it.second) }.toSet().size)
    }

    @Test fun everyExerciseBelongsToKnownGroup() {
        val groups = setOf("Грудь", "Спина", "Ноги", "Плечи", "Руки", "Пресс")
        exerciseCatalog.forEach { assertTrue(it.name, it.muscle in groups) }
        assertTrue(exerciseCatalog.any { it.muscle == "Пресс" })
    }

    @Test fun synonymsFindExercises() {
        assertTrue("Сгибание рук молотком" in names("молоточки"))
        assertEquals(setOf("Разгибание рук на блоке с канатом", "Разгибание рук на блоке с V-рукоятью"),
            names("сверху вниз"))
        assertTrue("Сведение рук в кроссовере" in names("кроссовер"))
        assertTrue("Французский жим EZ-штангой" in names("французский"))
        assertEquals(setOf("Отжимания на брусьях на грудь", "Отжимания на брусьях на трицепс"), names("брусья"))
        assertEquals(exerciseCatalog.filter { it.muscle == "Пресс" }.map { it.name }.toSet(), names("пресс"))
        assertEquals(exerciseCatalog.size, searchExercises("  ").size)
    }

    @Test fun dumbbellExercisesAskForOneDumbbell() {
        listOf("Разводка гантелей лёжа", "Шраги с гантелями", "Подъём гантелей на бицепс поочерёдно")
            .forEach { assertTrue(it, usesDumbbell(it)) }
        assertFalse(usesDumbbell("Сведение рук в кроссовере"))
    }
}
