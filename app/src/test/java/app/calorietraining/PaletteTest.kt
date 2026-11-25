package app.calorietraining

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import java.io.File
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class PaletteTest {
    @get:Rule val ui = createComposeRule()

    @Test fun darkCobaltAccents() {
        val accents = diaryAccents(dark = true)
        assertEquals(listOf(0xFFB4F03C, 0xFF3DDC97, 0xFFFF8A4C, 0xFFFFC83D, 0xFF5B95FF).map(::Color),
            listOf(accents.kcal, accents.protein, accents.fat, accents.carbs, accents.workout))
    }

    @Test fun lightCobaltAccents() {
        val accents = diaryAccents(dark = false)
        assertEquals(listOf(0xFF487800, 0xFF08785A, 0xFFB8480F, 0xFF8A5F00, 0xFF1B55C4).map(::Color),
            listOf(accents.kcal, accents.protein, accents.fat, accents.carbs, accents.workout))
    }

    @Test fun graphiteWorkoutColors() {
        assertEquals(Color(0xFF9DB9D6), diaryAccents(dark = true, training = true).workout)
        assertEquals(Color(0xFF3B556F), diaryAccents(dark = false, training = true).workout)
    }

    @Test fun trainingKeepsNutritionColors() {
        listOf(true, false).forEach { dark ->
            val base = diaryAccents(dark)
            val training = diaryAccents(dark, training = true)
            assertEquals(base.copy(workout = training.workout, onWorkout = training.onWorkout), training)
        }
    }

    @Test fun workoutDiffersFromFat() {
        listOf(true, false).forEach { dark ->
            listOf(false, true).forEach { training ->
                val accents = diaryAccents(dark, training)
                assertNotEquals(accents.fat, accents.workout)
            }
        }
    }

    @Test fun trainingBackgroundColors() {
        assertEquals(Color(0xFF08090B), trainingBackground(dark = true))
        assertEquals(Color(0xFFEFF1F3), trainingBackground(dark = false))
    }

    @Test fun textOnFilledWorkoutIsReadable() {
        listOf(true, false).forEach { dark ->
            listOf(false, true).forEach { training ->
                val accents = diaryAccents(dark, training)
                assertEquals(if (dark) Color(0xFF06080C) else Color.White, accents.onWorkout)
                assertContrast(accents.onWorkout, accents.workout, 4.5, "текст на плитке dark=$dark training=$training")
            }
        }
    }

    @Test fun textOnFilledButtonsIsReadableDark() = checkFilledButtons(ThemeMode.DARK)

    @Test fun textOnFilledButtonsIsReadableLight() = checkFilledButtons(ThemeMode.LIGHT)

    private fun checkFilledButtons(mode: ThemeMode) {
        lateinit var scheme: ColorScheme
        ui.setContent { DiaryTheme(mode) { scheme = MaterialTheme.colorScheme } }
        ui.runOnIdle {
            assertContrast(scheme.onPrimary, scheme.primary, 4.5, "primary $mode")
            assertContrast(scheme.onSecondary, scheme.secondary, 4.5, "secondary $mode")
            assertContrast(scheme.onTertiary, scheme.tertiary, 4.5, "tertiary $mode")
            assertEquals(diaryAccents(mode == ThemeMode.DARK).protein, scheme.secondary)
            assertEquals(diaryAccents(mode == ThemeMode.DARK).fat, scheme.tertiary)
        }
    }

    @Test fun textOnCardsIsReadableDark() = checkTextOnCards(ThemeMode.DARK, training = false)

    @Test fun textOnCardsIsReadableLight() = checkTextOnCards(ThemeMode.LIGHT, training = false)

    @Test fun textOnTrainingCardsIsReadableDark() = checkTextOnCards(ThemeMode.DARK, training = true)

    @Test fun textOnTrainingCardsIsReadableLight() = checkTextOnCards(ThemeMode.LIGHT, training = true)

    @Test fun textOnTrainingBackgroundIsReadableDark() = checkTextOnBackground(ThemeMode.DARK)

    @Test fun textOnTrainingBackgroundIsReadableLight() = checkTextOnBackground(ThemeMode.LIGHT)

    private class TextCase(val name: String, val text: Color, val card: Color, val large: Boolean)

    /** Пары «цвет текста - карточка - размер» взяты из экранов: кольца питания, профиль, история, анкета, главная. */
    private fun textCases(accents: DiaryAccents, scheme: ColorScheme, training: Boolean): List<TextCase> {
        val cards = listOf(accents.kcal, accents.protein, accents.fat, accents.carbs, accents.workout)
        val common = cards.flatMap {
            listOf(TextCase("onSurfaceVariant", scheme.onSurfaceVariant, it, false),
                TextCase("error", scheme.error, it, false))
        }
        if (training) return common + listOf(
            TextCase("workout/workout мелкий", accents.workout, accents.workout, false),
            TextCase("workout/workout крупный", accents.workout, accents.workout, true),
        )
        return common + listOf(
            TextCase("kcal/kcal крупный", accents.kcal, accents.kcal, true),
            TextCase("carbs/kcal крупный", accents.carbs, accents.kcal, true),
            TextCase("protein/kcal крупный", accents.protein, accents.kcal, true),
            TextCase("fat/kcal крупный", accents.fat, accents.kcal, true),
            TextCase("kcal/kcal мелкий", accents.kcal, accents.kcal, false),
            TextCase("protein/kcal мелкий", accents.protein, accents.kcal, false),
            TextCase("fat/kcal мелкий", accents.fat, accents.kcal, false),
            TextCase("carbs/kcal мелкий", accents.carbs, accents.kcal, false),
            TextCase("workout/protein крупный", accents.workout, accents.protein, true),
            TextCase("fat/protein крупный", accents.fat, accents.protein, true),
            TextCase("workout/workout крупный", accents.workout, accents.workout, true),
            TextCase("workout/workout мелкий", accents.workout, accents.workout, false),
        )
    }

    private fun checkTextOnCards(mode: ThemeMode, training: Boolean) {
        val dark = mode == ThemeMode.DARK
        val accents = diaryAccents(dark, training)
        lateinit var scheme: ColorScheme
        val backdrops = backdrop(mode, training) { scheme = MaterialTheme.colorScheme }
        val container = if (dark) Color(0xFF141416) else Color.White
        val failures = mutableListOf<String>()
        val table = StringBuilder()
        textCases(accents, scheme, training).forEach { case ->
            val fill = glassFill(case.card, dark, container)
            val minimum = if (case.large) 3.0 else 4.5
            val worst = backdrops.minOf { backdrop ->
                val surface = fill.compositeOver(backdrop)
                val highlight = Color.White.copy(alpha = if (dark) .06f else .60f).compositeOver(surface)
                min(ratio(case.text, surface), ratio(case.text, highlight))
            }
            table.append("карточка $mode training=$training ${case.name} на ${case.card.toArgb().toUInt().toString(16)}: ${"%.2f".format(worst)}\n")
            if (worst < minimum) failures += "${case.name} на карточке ${"%.2f".format(worst)} < $minimum"
        }
        File("build/screenshots").apply { mkdirs() }.resolve("contrast-cards.txt").appendText(table.toString())
        assertTrue("Контраст на карточках $mode training=$training: $failures", failures.isEmpty())
    }

    private fun checkTextOnBackground(mode: ThemeMode) {
        val dark = mode == ThemeMode.DARK
        val accents = diaryAccents(dark, training = true)
        lateinit var scheme: ColorScheme
        val backdrops = backdrop(mode, training = true) { scheme = MaterialTheme.colorScheme }
        val texts = listOf("onSurfaceVariant" to scheme.onSurfaceVariant, "error" to scheme.error, "workout" to accents.workout)
        val failures = mutableListOf<String>()
        val table = StringBuilder()
        texts.forEach { (name, color) ->
            val worst = backdrops.minOf { ratio(color, it) }
            table.append("фон тренировок $mode $name: ${"%.2f".format(worst)}\n")
            if (worst < 4.5) failures += "$name ${"%.2f".format(worst)}"
        }
        File("build/screenshots").apply { mkdirs() }.resolve("contrast-background.txt").appendText(table.toString())
        assertTrue("Контраст на фоне тренировок $mode: $failures", failures.isEmpty())
    }

    /** Отрисованный фон без карточек в зоне, где лежит текст: с отступом 16 dp от краёв. */
    private fun backdrop(mode: ThemeMode, training: Boolean, schemeOut: @Composable () -> Unit): List<Color> {
        val screen = ui.setScreenshotContent(mode, training) { schemeOut() }
        val bitmap = screen.captureScreen("palette-backdrop-$mode-$training.png")
        val margin = (16 * bitmap.width / 360f).roundToInt()
        val colors = mutableListOf<Color>()
        for (y in 0 until bitmap.height step 6) for (x in margin until bitmap.width - margin step 6) {
            colors += Color(bitmap.getPixel(x, y))
        }
        return colors
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (max(a, b) + .05) / (min(a, b) + .05)
    }

    @Test fun trainingBackgroundHasSteelSpots() {
        listOf(true, false).forEach { dark ->
            val accents = diaryAccents(dark, training = true)
            val spots = trainingGlows(dark, accents)
            assertEquals(2, spots.size)
            spots.forEach { assertEquals(accents.workout, it.copy(alpha = 1f)) }
            trainingBackdrops(dark, accents).forEach {
                assertContrast(accents.workout, it, 4.5, "цвет тренировок на фоне вкладки dark=$dark")
            }
        }
    }

    @Test fun trainingSchemeIsSteelDark() = checkTrainingScheme(ThemeMode.DARK)

    @Test fun trainingSchemeIsSteelLight() = checkTrainingScheme(ThemeMode.LIGHT)

    private fun checkTrainingScheme(mode: ThemeMode) {
        lateinit var base: ColorScheme
        lateinit var scheme: ColorScheme
        lateinit var accents: DiaryAccents
        ui.setContent { DiaryTheme(mode) {
            base = MaterialTheme.colorScheme
            TrainingPalette { scheme = MaterialTheme.colorScheme; accents = LocalDiaryAccents.current }
        } }
        ui.runOnIdle {
            assertEquals(accents.workout, scheme.primary)
            assertEquals(accents.onWorkout, scheme.onPrimary)
            assertContrast(scheme.onPrimary, scheme.primary, 4.5, "кнопка тренировок $mode")
            assertContrast(scheme.onPrimaryContainer, scheme.primaryContainer, 4.5, "выбранный чип $mode")
            assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5, "тональная кнопка $mode")
            assertContrast(scheme.primary, base.surface, 4.5, "текстовая кнопка $mode")
            assertNotEquals(base.primary, scheme.primary)
            assertEquals(base.error, scheme.error)
            assertEquals(base.surface, scheme.surface)
            assertEquals(diaryAccents(mode == ThemeMode.DARK).kcal, accents.kcal)
        }
    }

    private fun diaryBackdrops(dark: Boolean, accents: DiaryAccents): List<Color> {
        val base = if (dark) Color(0xFF07090B) else Color(0xFFF2F2F5)
        return listOf(base) + diaryGlows(dark, accents).map { it.compositeOver(base) }
    }

    private fun trainingBackdrops(dark: Boolean, accents: DiaryAccents): List<Color> {
        val base = trainingBackground(dark)
        val spots = trainingGlows(dark, accents)
        val both = spots.fold(base) { under, spot -> spot.compositeOver(under) }
        return listOf(base, both) + spots.map { it.compositeOver(base) }
    }

    @Test fun cardHasNoAccentOutside() {
        val bitmap = captureCardOnGrey()
        val bounds = ui.onNodeWithTag("glass-card").fetchSemanticsNode().boundsInRoot
        val unit = bounds.width / 280f
        val background = chroma(bitmap.getPixel((bounds.left - 30 * unit).roundToInt(), bounds.center.y.roundToInt()))
        listOf(2f, 4f, 6f).forEach { gap ->
            val points = listOf(
                (bounds.right + gap * unit) to bounds.center.y,
                (bounds.left - gap * unit) to bounds.center.y,
                bounds.center.x to (bounds.top - gap * unit),
                bounds.center.x to (bounds.bottom + gap * unit),
            )
            points.forEach { (x, y) ->
                val spread = chroma(bitmap.getPixel(x.roundToInt(), y.roundToInt()))
                assertTrue("Вокруг карточки цветной ореол ($spread при фоне $background) в точке $x,$y",
                    spread <= background + 3)
            }
        }
    }

    @Test fun trainingTabBackgroundDiffersDark() = checkTabBackgrounds(ThemeMode.DARK)

    @Test fun trainingTabBackgroundDiffersLight() = checkTabBackgrounds(ThemeMode.LIGHT)

    private fun checkTabBackgrounds(mode: ThemeMode) {
        OnboardingSettings(org.robolectric.RuntimeEnvironment.getApplication()).skipped = true
        run {
            val screen = ui.setScreenshotContent(mode) { DiaryApp(mode, {}) }
            ui.waitForIdle()
            ui.onNode(hasText("Питание") and hasClickAction()).performClick()
            val food = pixel(screen.captureScreen("palette-food-$mode.png"))
            ui.onNode(hasText("Тренировки") and hasClickAction()).performClick()
            val training = pixel(screen.captureScreen("palette-training-$mode.png"))
            val expected = trainingBackground(mode == ThemeMode.DARK)
            assertTrue("Фон тренировок $mode: ${Integer.toHexString(training)}",
                abs(android.graphics.Color.red(training) - (expected.red * 255).roundToInt()) <= 12 &&
                    abs(android.graphics.Color.green(training) - (expected.green * 255).roundToInt()) <= 12 &&
                    abs(android.graphics.Color.blue(training) - (expected.blue * 255).roundToInt()) <= 12)
            val corner = screen.captureScreen("palette-training-$mode.png").getPixel(357, 3)
            val shift = listOf(android.graphics.Color.red(corner) - (expected.red * 255).roundToInt(),
                android.graphics.Color.green(corner) - (expected.green * 255).roundToInt(),
                android.graphics.Color.blue(corner) - (expected.blue * 255).roundToInt()).maxOf { abs(it) }
            assertTrue("В углу вкладки нет стального пятна $mode: ${Integer.toHexString(corner)}", shift >= 15)
            assertNotEquals("Фон питания и тренировок совпал $mode", food, training)
        }
    }

    @Test fun navigationIndicatorIsSteelOnTrainingDark() = checkNavigation(ThemeMode.DARK)

    @Test fun navigationIndicatorIsSteelOnTrainingLight() = checkNavigation(ThemeMode.LIGHT)

    private fun checkNavigation(mode: ThemeMode) {
        OnboardingSettings(org.robolectric.RuntimeEnvironment.getApplication()).skipped = true
        lateinit var base: ColorScheme
        lateinit var steel: ColorScheme
        val screen = ui.setScreenshotContent({ mode }) {
            base = MaterialTheme.colorScheme
            TrainingPalette { steel = MaterialTheme.colorScheme }
            DiaryApp(mode, {})
        }
        ui.waitForIdle()
        ui.onNode(hasText("Тренировки") and hasClickAction()).performClick()
        val onTraining = screen.captureScreen("palette-nav-training-$mode.png")
        val item = ui.onNode(hasText("Тренировки") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val unit = item.width / 72f
        val indicator = onTraining.getPixel((item.center.x - 24 * unit).roundToInt(), (item.top + 28 * unit).roundToInt())
        assertClose(steel.primaryContainer, indicator, "заливка индикатора $mode")
        val label = (item.bottom - 24 * unit).roundToInt()..(item.bottom - 6 * unit).roundToInt()
        val greenest = label.flatMap { y ->
            (item.left.roundToInt() until item.right.roundToInt()).map { x ->
                val c = onTraining.getPixel(x, y)
                android.graphics.Color.green(c) - android.graphics.Color.blue(c)
            }
        }.max()
        assertTrue("Подпись или значок зелёные на вкладке тренировок $mode: $greenest", greenest < 20)

        ui.onNode(hasText("Питание") and hasClickAction()).performClick()
        val onFood = screen.captureScreen("palette-nav-food-$mode.png")
        val food = ui.onNode(hasText("Питание") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val foodIndicator = onFood.getPixel((food.center.x - 24 * unit).roundToInt(), (food.top + 28 * unit).roundToInt())
        assertClose(base.primaryContainer, foodIndicator, "заливка индикатора питания $mode")
    }

    private fun assertClose(expected: Color, actual: Int, label: String) {
        val channels = listOf(expected.red to android.graphics.Color.red(actual), expected.green to android.graphics.Color.green(actual),
            expected.blue to android.graphics.Color.blue(actual))
        assertTrue("$label: ожидали ${Integer.toHexString(expected.toArgb())}, получили ${Integer.toHexString(actual)}",
            channels.all { (e, a) -> abs(e * 255 - a) <= 4 })
    }

    private fun pixel(bitmap: Bitmap): Int = bitmap.getPixel(3, bitmap.height / 2)

    private fun captureCardOnGrey(): Bitmap {
        val screen = ui.setScreenshotContent(ThemeMode.DARK) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(340.dp, 200.dp).background(Color(0xFF202124)), contentAlignment = Alignment.Center) {
                    GlassCard(Color(0xFFB4F03C), Modifier.size(280.dp, 120.dp)) {}
                }
            }
        }
        return screen.captureScreen("palette-card-grey.png")
    }

    private fun chroma(argb: Int): Int {
        val channels = listOf(android.graphics.Color.red(argb), android.graphics.Color.green(argb), android.graphics.Color.blue(argb))
        return channels.max() - channels.min()
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Double, label: String) {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        val ratio = (max(a, b) + .05) / (min(a, b) + .05)
        assertTrue("Контраст $label: ${"%.2f".format(ratio)} < $minimum", ratio >= minimum)
    }
}
