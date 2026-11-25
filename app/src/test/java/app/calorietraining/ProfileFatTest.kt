package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ProfileFatTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.now()
    private lateinit var model: ProfileViewModel

    private fun open(seed: FoodStore.() -> Unit) {
        FoodStore(app).use { it.seed() }
        model = ProfileViewModel(app)
        ui.setContent { DiaryTheme(ThemeMode.DARK) { ProfileScreen(model, {}) } }
        ui.awaitUntil(10_000) { model.uiState.value.stats != null }
    }

    private fun stored(day: LocalDate) = FoodStore(app).use { store ->
        store.readableDatabase.rawQuery("SELECT weight,fat FROM measurement WHERE day=?", arrayOf(day.toString())).use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
        }
    }

    private fun scrollTo(text: String) = ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Test fun fatFormHasOnlyFatAndKeepsWeight() {
        open { saveMeasurement(Measurement(today, "82")) }
        scrollTo("Записать % жира")
        ui.onNodeWithText("Записать % жира").performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Жир, %").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText("Вес, кг").assertCountEquals(0)
        ui.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        ui.onNode(hasSetTextAction()).performTextInput("19")
        ui.onNodeWithText("Сохранить замер").performClick()
        ui.awaitUntil(10_000) { stored(today)?.second == "19" }
        assertEquals("82" to "19", stored(today))
    }

    @Test fun fatFormKeepsExistingFatOfTodayWeightUntouched() {
        open { saveMeasurement(Measurement(today, "82", "20")) }
        scrollTo("Записать % жира")
        ui.onNodeWithText("Записать % жира").performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Жир, %").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasSetTextAction()).performTextReplacement("18,5")
        ui.onNodeWithText("Сохранить замер").performClick()
        ui.awaitUntil(10_000) { stored(today)?.second == "18,5" || stored(today)?.second == "18.5" }
        assertEquals("82", stored(today)!!.first)
    }

    @Test fun historyOpensOldMeasurementWithBothFieldsAndKeepsOther() {
        val past = today.minusDays(5)
        open { saveMeasurement(Measurement(past, "83", "22")); saveMeasurement(Measurement(today, "82", "21")) }
        scrollTo("История всех замеров")
        ui.onNodeWithText("История всех замеров").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(past.caption()))
        ui.onNode(hasClickAction().and(hasText(past.caption(), substring = true))).performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Вес, кг").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("84")
        ui.onNodeWithText("Сохранить замер").performClick()
        ui.awaitUntil(10_000) { stored(past)?.first == "84" }
        assertEquals("84" to "22", stored(past))
        assertEquals("82" to "21", stored(today))
    }

    @Test fun historyEditSeesWeightWrittenAfterScreenWasLoaded() {
        open { saveMeasurement(Measurement(today, fat = "21")) }
        FoodStore(app).use { it.saveWeight(today, "82") }
        scrollTo("История всех замеров")
        ui.onNodeWithText("История всех замеров").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasClickAction().and(hasText(today.caption(), substring = true)))
        ui.onNode(hasClickAction().and(hasText(today.caption(), substring = true))).performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Вес, кг").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("20")
        ui.onNodeWithText("Сохранить замер").performClick()
        ui.awaitUntil(10_000) { stored(today)?.second == "20" }
        assertEquals("82" to "20", stored(today))
    }
}
