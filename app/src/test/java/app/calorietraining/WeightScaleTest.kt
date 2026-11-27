package app.calorietraining

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class WeightScaleTest {
    private val today get() = LocalDate.now()
    private val withoutMeasurements = setOf("startsAtZeroWithoutMeasurements", "boundsHold")

    @get:Rule(order = 0)
    val seed = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            OnboardingSettings(app).skipped = true
            if (description.methodName in withoutMeasurements) return
            FoodStore(app).use { store ->
                store.saveMeasurement(Measurement(today.minusDays(2), "82"))
                store.saveMeasurement(Measurement(today.minusDays(1), "82.4"))
                if (description.methodName == "weightKeepsTodaysFat") {
                    store.saveMeasurement(Measurement(today, fat = "18"))
                }
                if (description.methodName == "databaseErrorKeepsDialogOpen") {
                    store.writableDatabase.execSQL(
                        "CREATE TRIGGER block_insert BEFORE INSERT ON measurement BEGIN SELECT RAISE(ABORT, 'x'); END")
                }
            }
        }
    }

    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    private val plus get() = ui.onNodeWithContentDescription("Увеличить на 0,1")
    private val minus get() = ui.onNodeWithContentDescription("Уменьшить на 0,1")
    private val readout get() = ui.onNodeWithTag("weight-readout")
    private val save get() = ui.onNode(hasText("Сохранить") and hasClickAction())

    private fun openDialog() {
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Записать вес").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText("Записать вес").onFirst().performScrollTo().performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithTag("weight-readout").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shown() = readout.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun stored(day: LocalDate = today): Pair<String, String>? = FoodStore(ui.activity.application).use { store ->
        store.readableDatabase.rawQuery("SELECT weight,fat FROM measurement WHERE day=?", arrayOf(day.toString())).use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
        }
    }

    private fun shownNumber() = shown().replace(',', '.').toBigDecimal()

    @Test fun stepsFromLastWeightAndSavesToday() {
        openDialog()
        assertEquals("82,4", shown())
        plus.performClick()
        assertEquals("82,5", shown())
        minus.performClick()
        minus.performClick()
        assertEquals("82,3", shown())
        save.performClick()
        ui.awaitUntil(10_000) { stored() != null }
        assertEquals("82.3".toBigDecimal(), stored()!!.first.toBigDecimal())
    }

    @Test fun shortTapIsExactlyOneStep() {
        openDialog()
        ui.mainClock.autoAdvance = false
        plus.performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(100)
        plus.performTouchInput { up() }
        ui.mainClock.advanceTimeBy(2_000)
        ui.mainClock.autoAdvance = true
        assertEquals("82,5", shown())
    }

    @Test fun holdRepeatsStepAndStopsOnRelease() {
        openDialog()
        ui.mainClock.autoAdvance = false
        plus.performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(1_000)
        val held = shownNumber()
        assertTrue(held > "82.5".toBigDecimal())
        plus.performTouchInput { up() }
        ui.mainClock.advanceTimeBy(1_000)
        ui.mainClock.autoAdvance = true
        assertEquals(held, shownNumber())
    }

    @Test fun typedValueIsShownAndSaved() {
        openDialog()
        readout.performTextReplacement("81,7")
        assertEquals("81,7", shown())
        save.assertIsEnabled().performClick()
        ui.awaitUntil(10_000) { stored() != null }
        assertEquals("81.7".toBigDecimal(), stored()!!.first.toBigDecimal())
    }

    @Test fun invalidInputCannotBeSaved() {
        openDialog()
        listOf("абв", "", "0", "-1", "501").forEach { value ->
            readout.performTextReplacement(value)
            save.assertIsNotEnabled()
        }
        assertNull(stored())
    }

    @Test fun startsAtZeroWithoutMeasurements() {
        openDialog()
        assertEquals("0", shown())
        save.assertIsNotEnabled()
        readout.performTextReplacement("80")
        save.assertIsEnabled().performClick()
        ui.awaitUntil(10_000) { stored() != null }
        assertEquals("80".toBigDecimal(), stored()!!.first.toBigDecimal())
    }

    @Test fun boundsHold() {
        openDialog()
        minus.performClick()
        assertEquals("0", shown())
        readout.performTextReplacement("500")
        plus.performClick()
        assertEquals("500", shown())
    }

    @Test fun weightKeepsTodaysFat() {
        openDialog()
        readout.performTextReplacement("82")
        save.performClick()
        ui.awaitUntil(10_000) { stored()?.first == "82" }
        assertEquals("82" to "18", stored())
    }

    @Test fun dialogHasNoFatField() {
        openDialog()
        ui.onAllNodesWithText("Жир", substring = true).assertCountEquals(0)
        ui.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test fun recreationKeepsInputAndCancelWritesNothing() {
        openDialog()
        plus.performClick()
        plus.performClick()
        ui.activityRule.scenario.recreate()
        ui.awaitUntil(10_000) { ui.onAllNodesWithTag("weight-readout").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("82,6", shown())
        ui.onNodeWithText("Отмена").performClick()
        ui.onAllNodesWithTag("weight-readout").assertCountEquals(0)
        assertNull(stored())
    }

    @Test fun databaseErrorKeepsDialogOpen() {
        openDialog()
        plus.performClick()
        save.performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Не удалось сохранить замер").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("82,5", shown())
        assertNull(stored())
    }

    @Test fun buttonsAreAtLeast48dp() {
        openDialog()
        with(ui.density) {
            listOf(plus, minus).forEach { button ->
                val bounds = button.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width >= 48.dp.toPx() - 0.5f && bounds.height >= 48.dp.toPx() - 0.5f)
            }
        }
    }

    @Test fun numberFieldHasLabelWithUnit() {
        openDialog()
        readout.assertContentDescriptionEquals("Вес, кг")
        assertEquals("82,4", shown())
    }
}
