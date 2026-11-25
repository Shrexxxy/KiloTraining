package app.calorietraining

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DeltoidsCatalogTest {
    private val front = Muscle.FrontDelts
    private val side = Muscle.SideDelts
    private val rear = Muscle.RearDelts
    private val delts = setOf(front, side, rear)

    private fun technique(name: String) = exerciseCatalog.single { it.name == name }.technique

    @Test fun shoulderGroupFollowsTheAgreedTable() {
        val expected = mapOf(
            "Жим гантелей сидя" to (listOf(front) to listOf(side, Muscle.Triceps)),
            "Армейский жим стоя" to (listOf(front) to listOf(side, Muscle.Triceps)),
            "Жим Арнольда" to (listOf(front) to listOf(side, Muscle.Triceps)),
            "Жим в тренажёре на плечи" to (listOf(front) to listOf(side, Muscle.Triceps)),
            "Подъём гантелей в стороны" to (listOf(side) to emptyList()),
            "Подъём руки в сторону на блоке" to (listOf(side) to listOf(Muscle.Forearms)),
            "Тяга штанги к подбородку" to (listOf(side) to listOf(front, Muscle.Traps)),
            "Подъём гантелей перед собой" to (listOf(front) to emptyList()),
            "Разведение гантелей в наклоне" to (listOf(rear) to emptyList()),
            "Обратные разведения в тренажёре" to (listOf(rear) to emptyList()),
            "Тяга каната к лицу" to (listOf(rear) to listOf(Muscle.MiddleBack)),
        )
        assertEquals(11, exerciseCatalog.count { it.muscle == "Плечи" })
        expected.forEach { (name, roles) ->
            assertEquals(name, roles.first, technique(name).primary)
            assertEquals(name, roles.second, technique(name).secondary)
        }
    }

    @Test fun otherExercisesReplaceShouldersInPlaceWithFrontOrRearDelt() {
        val chest = mapOf(
            "Жим штанги лёжа" to listOf(front, Muscle.Triceps),
            "Жим гантелей лёжа" to listOf(front, Muscle.Triceps),
            "Жим гантелей на наклонной скамье" to listOf(front, Muscle.Triceps),
            "Сведение рук в кроссовере" to listOf(front),
            "Жим штанги на наклонной скамье" to listOf(front, Muscle.Triceps),
            "Отжимания на брусьях на грудь" to listOf(front, Muscle.Triceps),
            "Отжимания от пола" to listOf(front, Muscle.Triceps),
            "Жим в тренажёре сидя" to listOf(front, Muscle.Triceps),
            "Жим гантелей на скамье с наклоном вниз" to listOf(front, Muscle.Triceps),
            "Разводка гантелей на наклонной скамье" to listOf(front),
        )
        chest.forEach { (name, secondary) ->
            assertEquals(name, listOf(Muscle.Chest), technique(name).primary)
            assertEquals(name, secondary, technique(name).secondary)
        }
        assertEquals(listOf(Muscle.Chest, front), technique("Жим штанги узким хватом").secondary)
        assertEquals(listOf(Muscle.Chest, front), technique("Отжимания на брусьях на трицепс").secondary)
        assertEquals(listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, front), technique("Гоблет-присед").secondary)
        assertEquals(listOf(Muscle.Biceps, Muscle.MiddleBack, rear), technique("Тяга вертикального блока").secondary)
        assertEquals(listOf(Muscle.Biceps, Muscle.MiddleBack, rear), technique("Тяга верхнего блока узким хватом").secondary)
        for (name in listOf("Тяга горизонтального блока", "Тяга гантели одной рукой", "Тяга штанги в наклоне")) {
            assertEquals(name, listOf(Muscle.Biceps, Muscle.Lats, rear), technique(name).secondary)
        }
    }

    @Test fun shouldersAreGoneAndLabelsAreSet() {
        assertTrue(Muscle.entries.none { it.name == "Shoulders" })
        assertEquals("передняя дельта", front.label)
        assertEquals("средняя дельта", side.label)
        assertEquals("задняя дельта", rear.label)
        val used = exerciseCatalog.flatMap { it.technique.primary + it.technique.secondary }.toSet()
        assertTrue(used.containsAll(delts))
        assertEquals(29, exerciseCatalog.count { (it.technique.primary + it.technique.secondary).any(delts::contains) })
    }

    @Test fun benchPressStillMatchesExternalBaseThroughADelt() {
        assertEquals(listOf(front, Muscle.Triceps), technique("Жим штанги лёжа").secondary)
    }

    @Test fun threeDifferentDeltsGiveThreeSeparateWeekRows() {
        val bench = SetDraft(exercise = "Жим гантелей сидя", weight = "20", reps = "8")
        val today = LocalDate.of(2026, 11, 25)
        val load = muscleLoad(listOf(Workout(1, List(2) { bench }, day = today)), today) { photoForExercise(it)?.technique }
        assertEquals(
            mapOf(front to 2, side to 1, Muscle.Triceps to 1),
            load.scores.mapValues { it.value.toInt() },
        )
        val custom = ExerciseTechnique(listOf(front), listOf(side, rear), emptyList())
        val own = muscleLoad(listOf(Workout(1, listOf(bench.copy(exercise = "Мой жим")), day = today)), today) { custom }
        assertEquals(mapOf(front to "1", side to "0.5", rear to "0.5"), own.scores.mapValues { it.value.stripTrailingZeros().toPlainString() })
    }
}
