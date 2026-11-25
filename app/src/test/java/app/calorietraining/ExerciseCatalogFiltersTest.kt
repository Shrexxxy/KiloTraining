package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ExerciseCatalogFiltersTest {
    @get:Rule val ui = createComposeRule()
    private val search = "Название или группа мышц"
    private val back = "Назад"
    private val arnold = "Жим Арнольда"

    @Test fun combinesFiltersAndSearchThenResets() {
        ui.setContent { DiaryTheme(ThemeMode.DARK) { ExercisePicker({}, {}) } }
        filterLegsAndDumbbells()
        ui.onNodeWithText(search).performTextInput("выпады")
        ui.onNodeWithText("Найдено: 1").assertExists()
        ui.onNodeWithText("Выпады с гантелями").assertIsDisplayed()
        ui.onNodeWithText("Болгарские сплит-приседания").assertDoesNotExist()
        ui.onNodeWithText("Сбросить").performClick()
        ui.onNodeWithText("Найдено: 80").assertExists()
        ui.onNodeWithText("Сбросить").assertDoesNotExist()
    }

    @Test fun similarExerciseReturnsThroughBothCardsToRestoredList() {
        val restoration = StateRestorationTester(ui)
        restoration.setContent { DiaryTheme(ThemeMode.DARK) { ExercisePicker({}, {}) } }
        openShoulders()
        list().performScrollToNode(hasText(arnold))
        val top = ui.onNodeWithText(arnold).fetchSemanticsNode().boundsInRoot.top
        openDetails(arnold)
        ui.onNodeWithText("Похожие").assertExists()
        ui.onNodeWithText("Жим гантелей сидя").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("Жим гантелей сидя").assertIsDisplayed()
        ui.onNodeWithContentDescription(back).performClick()
        ui.onNodeWithText(arnold).assertIsDisplayed()
        ui.onNodeWithContentDescription(back).performClick()
        chip("Плечи").assertIsSelected()
        assertEquals(top, ui.onNodeWithText(arnold).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun chipsScrollAndExposeSelectionAtLargeFont() {
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    ExercisePicker({}, {})
                }
            }
        }
        ui.onAllNodes(hasScrollAction() and !hasScrollToIndexAction())[0].performTouchInput { swipeLeft() }
        chip("Пресс").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        ui.onAllNodes(hasScrollAction() and !hasScrollToIndexAction())[1].performTouchInput { swipeLeft() }
        chip("Другое").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
    }

    @Test fun darkFiltersScreenshot() = captureFilters(ThemeMode.DARK, "dark")
    @Test fun lightFiltersScreenshot() = captureFilters(ThemeMode.LIGHT, "light")

    @Test fun emptyScreenshot() {
        val screen = ui.setScreenshotContent(training = true) { ExercisePicker({}, {}) }
        ui.onNodeWithText(search).performTextInput("неттакогоупражнения")
        ui.onNodeWithText("Совпадений нет").assertIsDisplayed()
        ui.onNodeWithText("Сбросить").assertExists()
        screen.captureScreen("catalog-empty-dark.png")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h1400dp")
    fun similarScreenshot() {
        val screen = ui.setScreenshotContent(training = true) { ExercisePicker({}, {}) }
        ui.onNodeWithText(search).performTextInput("арнольд")
        openDetails(arnold)
        ui.onNodeWithText("Похожие").assertIsDisplayed()
        screen.captureScreen("exercise-similar-dark.png")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w720dp-h1750dp")
    fun newFramesScreenshot() {
        val screen = ui.setScreenshotContent(training = true) {
            Column {
                exerciseCatalog.drop(57).chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { photo ->
                            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                androidx.compose.material3.Text(photo.name)
                                ExerciseFrames(photo)
                            }
                        }
                    }
                }
            }
        }
        screen.captureScreen("catalog-new-frames.png")
    }

    private fun captureFilters(mode: ThemeMode, suffix: String) {
        val screen = ui.setScreenshotContent(mode, training = true) { ExercisePicker({}, {}) }
        filterLegsAndDumbbells()
        chip("Гантели").assertIsSelected()
        screen.captureScreen("catalog-filters-$suffix.png")
    }

    private fun filterLegsAndDumbbells() {
        chip("Ноги").performClick().assertIsSelected()
        chip("Гантели").performClick().assertIsSelected()
    }

    private fun openShoulders() {
        ui.onAllNodes(hasScrollAction() and !hasScrollToIndexAction())[0].performTouchInput { swipeLeft() }
        chip("Плечи").performClick().assertIsSelected()
    }

    private fun openDetails(name: String) {
        ui.onNode(hasContentDescription("Мышцы и техника") and hasAnySibling(hasText(name)))
            .performClick()
    }

    private fun chip(label: String) = ui.onNode(hasText(label) and isSelectable())

    private fun list() = ui.onNode(hasScrollToIndexAction())
}
