package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h3600dp")
class ProfileChartsScreenshotTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun monthDark() = capture("profile-charts-dark", ThemeMode.DARK, "30 дней")
    @Test fun monthLight() = capture("profile-charts-light", ThemeMode.LIGHT, "30 дней")
    @Test fun weekDark() = capture("profile-charts-week-dark", ThemeMode.DARK, "7 дней")
    @Test fun allTimeDark() = capture("profile-charts-alltime-dark", ThemeMode.DARK, "Всё время")
    @Test fun emptyDark() = capture("profile-empty-dark", ThemeMode.DARK, "30 дней", empty = true)

    private fun capture(name: String, mode: ThemeMode, period: String, empty: Boolean = false) {
        if (!empty) seed()
        lateinit var view: View
        ui.setContent {
            DiaryTheme(mode) {
                view = LocalView.current
                DiaryApp(mode, {})
            }
        }
        val profileTab = hasText("Профиль") and isSelectable()
        ui.onNode(profileTab).performClick()
        val last = hasText(if (empty) "Добавь время еды." else "23:00")
        ui.awaitUntil(10_000) { ui.onAllNodes(last).fetchSemanticsNodes().isNotEmpty() }
        if (period != "30 дней") {
            ui.onNodeWithText(period).performClick()
            val chart = hasContentDescription(
                if (period == "Всё время") "Калории по месяцам" else "Калории по дням, среднее",
                substring = true,
            )
            ui.awaitUntil(10_000) {
                ui.onAllNodes(hasText(period) and isSelected()).fetchSemanticsNodes().isNotEmpty() &&
                    ui.onAllNodes(chart).fetchSemanticsNodes().isNotEmpty()
            }
        }
        ui.onNode(last).assertIsDisplayed()
        // Высокое окно вмещает все карточки, снимок обрезается по нижней карточке.
        val end = ui.onNode(last).fetchSemanticsNode().boundsInRoot.bottom
        val height = (end + 36 * view.resources.displayMetrics.density).roundToInt()
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, height.coerceAtMost(view.height), Bitmap.Config.ARGB_8888)
                .also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed() {
        val today = LocalDate.now()
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveOnboarding(
                UserProfile(Sex.MALE, today.year - 34, 182, ActivityLevel.MODERATE, GoalKind.LOSE),
                Measurement(today.minusWeeks(40), "89", "25"), DailyGoals(2400, MacroGoals(140, 75, 280)),
            )
            store.setGoals(today.minusDays(14), DailyGoals(2100, MacroGoals(140, 65, 245)))
            for (offset in (0L..49L).filter { it % 5 != 4L } + listOf(80L, 130L, 190L, 260L)) {
                val day = today.minusDays(offset)
                val factor = (offset % 7).toInt()
                store.save(day, FoodDraft(name = "Завтрак", energy = "${420 + factor * 30}",
                    meal = Meal.BREAKFAST, mealTime = if (offset % 3 == 0L) "08:00" else "09:10",
                    macros = Macros("28", "15", "${45 + factor * 5}")))
                store.save(day, FoodDraft(name = "Обед", energy = "${680 + factor * 40}",
                    meal = Meal.LUNCH, mealTime = if (offset % 4 == 0L) "12:30" else "13:10",
                    macros = Macros("48", "${23 + factor * 2}", "90")))
                store.save(day, FoodDraft(name = "Ужин", energy = "${620 + factor * 56}",
                    meal = Meal.DINNER, mealTime = if (offset % 2 == 0L) "19:30" else "20:00",
                    macros = Macros("${40 + factor * 5}", "28", "92")))
            }
            for ((index, offset) in listOf(36L, 29L, 22L, 18L, 15L, 9L, 6L, 3L, 1L).withIndex()) {
                val day = today.minusDays(offset)
                val session = store.newWorkout(day)
                store.setDuration(session, 45 + index * 2)
                store.saveSet(session, SetDraft(exercise = "Жим штанги лёжа",
                    weight = (50 + index / 2 * 2.5).toString(), reps = "5"))
                store.saveSet(session, SetDraft(exercise = "Жим штанги лёжа",
                    weight = (45 + index / 3 * 2.5).toString(), reps = "8"))
            }
            listOf(
                Triple(27L, "85.8", "24"), Triple(21L, "84.9", "23.8"), Triple(14L, "85.1", "23"),
                Triple(9L, "83.7", "22.5"), Triple(6L, "83.1", "22.7"), Triple(3L, "83.4", "21.9"),
                Triple(0L, "82", "21.6"),
            ).forEach { (days, weight, fat) ->
                store.saveMeasurement(Measurement(today.minusDays(days), weight, fat))
            }
        }
    }
}
