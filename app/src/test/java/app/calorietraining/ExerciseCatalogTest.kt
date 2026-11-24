package app.calorietraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    private fun names(query: String) = searchExercises(query).map { it.name }.toSet()

    @Test fun catalogHasUniqueNamesAndTwoFramesEach() {
        assertEquals(80, exerciseCatalog.size)
        val keys = exerciseCatalog.map { it.name.searchKey() }
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

    @Test fun existingExerciseNamesStayInOrder() {
        val original = listOf(
            "Тяга вертикального блока",
            "Жим штанги лёжа",
            "Жим гантелей лёжа",
            "Жим гантелей на наклонной скамье",
            "Тяга горизонтального блока",
            "Тяга гантели одной рукой",
            "Подтягивания",
            "Становая тяга",
            "Приседания со штангой",
            "Жим ногами",
            "Разгибание ног сидя",
            "Сгибание ног лёжа",
            "Жим гантелей сидя",
            "Подъём гантелей в стороны",
            "Сгибание рук молотком",
            "Разгибание рук на блоке с канатом",
            "Сведение рук в кроссовере",
            "Жим штанги на наклонной скамье",
            "Разводка гантелей лёжа",
            "Отжимания на брусьях на грудь",
            "Отжимания от пола",
            "Жим в тренажёре сидя",
            "Тяга штанги в наклоне",
            "Т-тяга",
            "Тяга верхнего блока узким хватом",
            "Гиперэкстензия",
            "Пуловер на блоке прямыми руками",
            "Шраги с гантелями",
            "Румынская тяга",
            "Выпады с гантелями",
            "Болгарские сплит-приседания",
            "Подъём на носки стоя",
            "Подъём на носки сидя",
            "Ягодичный мост со штангой",
            "Гоблет-присед",
            "Гакк-присед",
            "Сведение ног в тренажёре",
            "Разведение ног в тренажёре",
            "Приседания в Смите",
            "Армейский жим стоя",
            "Разведение гантелей в наклоне",
            "Подъём гантелей перед собой",
            "Тяга штанги к подбородку",
            "Тяга каната к лицу",
            "Подъём штанги на бицепс",
            "Подъём гантелей на бицепс поочерёдно",
            "Сгибание рук на нижнем блоке",
            "Сгибание рук на скамье Скотта",
            "Разгибание рук на блоке с V-рукоятью",
            "Разгибание из-за головы на блоке с канатом",
            "Французский жим EZ-штангой",
            "Жим штанги узким хватом",
            "Отжимания на брусьях на трицепс",
            "Скручивания",
            "Подъём ног в висе",
            "Планка",
            "Скручивания на блоке",
        )
        assertEquals(original, exerciseCatalog.take(original.size).map { it.name })
    }

    @Test fun newSynonymsFindTheirExercises() {
        assertEquals(setOf("Жим Арнольда"), names("арнольд"))
        assertEquals(setOf("Становая тяга сумо"), names("сумо"))
        assertEquals(setOf("Подтягивания обратным хватом"), names("обратный хват"))
        assertEquals(setOf("Наклоны со штангой на плечах"), names("good morning"))
        assertEquals(setOf("Болгарские сплит-приседания"), names("болгарские выпады"))
    }
}
