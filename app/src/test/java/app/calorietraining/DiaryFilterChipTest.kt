package app.calorietraining

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class DiaryFilterChipTest {
    @get:Rule val ui = createComposeRule()

    @Test fun borderContrastLight() = verifyContrast(ThemeMode.LIGHT)
    @Test fun borderContrastDark() = verifyContrast(ThemeMode.DARK)

    @Test fun unselectedChipDrawsBorderLight() = verifyDrawnBorder(ThemeMode.LIGHT)
    @Test fun unselectedChipDrawsBorderDark() = verifyDrawnBorder(ThemeMode.DARK)

    @Test fun selectedAndDisabledChipsLookLikeStandard() {
        val states = listOf(true to true, false to false, true to false)
        val screen = ui.setScreenshotContent(ThemeMode.LIGHT) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                for ((selected, enabled) in states) {
                    DiaryFilterChip(selected, {}, { Text("Чип") }, Modifier.testTag("ours-$selected-$enabled"), enabled)
                    FilterChip(selected, {}, { Text("Чип") }, Modifier.testTag("standard-$selected-$enabled"), enabled)
                }
            }
        }
        val bitmap = screen.captureScreen("chips-states-light.png")
        fun pixels(tag: String): List<Int> {
            val bounds = ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            return (bounds.top.roundToInt() until bounds.bottom.roundToInt()).flatMap { y ->
                (bounds.left.roundToInt() until bounds.right.roundToInt()).map { x -> bitmap.getPixel(x, y) }
            }
        }
        for ((selected, enabled) in states) {
            assertTrue("$selected, $enabled", pixels("standard-$selected-$enabled") == pixels("ours-$selected-$enabled"))
        }
    }

    @Test fun screensUseOnlyDiaryFilterChip() {
        val files = File("src/main").walk().filter { it.extension == "kt" }.toList()
        assertTrue("Исходники не найдены", files.any { it.name == "DiaryFilterChip.kt" })
        val direct = files.filter {
            it.name != "DiaryFilterChip.kt" && Regex("""\bFilterChip\s*\(""").containsMatchIn(it.readText())
        }
        assertEquals(emptyList<String>(), direct.map { it.name })
    }

    @Test fun catalogLight() = captureCatalog(ThemeMode.LIGHT, "light")
    @Test fun catalogDark() = captureCatalog(ThemeMode.DARK, "dark")
    @Test fun musclesLight() = captureMuscles(ThemeMode.LIGHT, "light")
    @Test fun musclesDark() = captureMuscles(ThemeMode.DARK, "dark")

    private fun captureCatalog(mode: ThemeMode, suffix: String) {
        val screen = ui.setScreenshotContent(mode, training = true) { ExercisePicker({}, {}, Modifier.padding(horizontal = 20.dp)) }
        ui.onNode(hasText("Ноги") and isSelectable()).performClick().assertIsSelected()
        screen.captureScreen("chips-catalog-$suffix.png")
    }

    private fun captureMuscles(mode: ThemeMode, suffix: String) {
        val screen = ui.setScreenshotContent(mode, training = true) { CustomMusclesScreen("Мой жим") {} }
        ui.awaitUntil(5_000) { ui.onAllNodesWithText("Сохранить").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("грудь").performScrollTo().performClick()
        ui.onNodeWithText("Основная").assertIsDisplayed()
        screen.captureScreen("chips-muscles-$suffix.png")
    }

    private fun verifyContrast(mode: ThemeMode) {
        lateinit var colors: ColorScheme
        ui.setContent { DiaryTheme(mode) { colors = MaterialTheme.colorScheme } }
        ui.runOnIdle {
            val border = filterChipBorderColor(colors)
            for ((name, background) in listOf("surface" to colors.surface, "surfaceContainer" to colors.surfaceContainer)) {
                val contrast = contrast(border, background)
                assertTrue("$mode, $name: $contrast", contrast >= 3f)
            }
        }
    }

    private fun verifyDrawnBorder(mode: ThemeMode) {
        lateinit var colors: ColorScheme
        val screen = ui.setScreenshotContent(mode) {
            colors = MaterialTheme.colorScheme
            DiaryFilterChip(false, {}, { Text("Чип") }, Modifier.padding(16.dp).testTag("chip"))
        }
        val bitmap = screen.captureScreen("chips-border-${mode.name.lowercase()}.png")
        val bounds = ui.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
        val x = bounds.left.roundToInt() + 12
        val column = (bounds.top.roundToInt() until bounds.bottom.roundToInt()).map { bitmap.getPixel(x, it) }
        val border = filterChipBorderColor(colors).toArgb()
        assertTrue("$mode: ${column.distinct().map(Integer::toHexString)}", border in column)
    }

    private fun contrast(first: Color, second: Color): Float {
        val (dark, light) = listOf(first.luminance(), second.luminance()).sorted()
        return (light + .05f) / (dark + .05f)
    }
}
