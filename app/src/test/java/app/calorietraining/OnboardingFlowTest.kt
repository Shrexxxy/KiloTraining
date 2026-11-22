package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.StringWriter
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class OnboardingFlowTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val today get() = LocalDate.now()
    private val birthYear get() = (today.year - 30).toString()
    private val goals = DailyGoals(2760, MacroGoals(128, 72, 400))
    private val instance = mutableIntStateOf(0)
    private lateinit var view: View

    @Test fun savesCalculatedGoalAndTodaysMeasurement() {
        launch()
        capture("onboarding-goal-dark.png")
        completeQuestionnaire()
        ui.onNode(hasText("2 760 ккал") or hasText("2\u00a0760 ккал") or hasText("2\u202f760 ккал")).assertIsDisplayed()
        capture("onboarding-result-dark.png")
        click("Сохранить цель")
        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Питание") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasText("Питание") and isSelectable()).assertIsSelected()
        FoodStore(context).use { store ->
            assertEquals(goals, store.goalsFor(today))
            store.readableDatabase.rawQuery("SELECT weight, fat FROM measurement WHERE day=?",
                arrayOf(today.toString())).use {
                assertTrue(it.moveToFirst())
                assertEquals(80.0, it.getString(0).toDouble(), 0.0)
                assertEquals("", it.getString(1))
            }
        }
    }

    @Test fun remembersSkipAfterDiaryIsRecreated() {
        launch()
        ui.onNodeWithText("Ваша цель").assertIsDisplayed()
        click("Пропустить")
        ui.onNodeWithText("Ваша цель").assertDoesNotExist()
        ui.runOnIdle { instance.intValue++ }
        ui.onNodeWithText("Ваша цель").assertDoesNotExist()
        ui.onNode(hasText("Питание") and isSelectable()).assertIsSelected()
    }

    @Test fun existingGoalSuppressesOnboardingWithoutProfile() {
        FoodStore(context).use { it.setGoals(today, goals) }
        launch()
        ui.onNodeWithText("Ваша цель").assertDoesNotExist()
        ui.onNode(hasText("Питание") and isSelectable()).assertIsSelected()
    }

    @Test fun restoredGoalSuppressesOnboarding() {
        val backup = StringWriter()
        FoodStore(context, "backup-source").use {
            it.setGoals(today.plusDays(1), goals)
            it.exportDiary(backup)
        }
        FoodStore(context, "backup-staged").use { staged ->
            staged.readBackup(backup.toString().reader())
            FoodStore(context).use { it.replaceFrom(staged) }
        }
        launch()
        ui.onNodeWithText("Ваша цель").assertDoesNotExist()
        ui.onNode(hasText("Питание") and isSelectable()).assertIsSelected()
    }

    @Test fun manualGoalCanBeCancelledAndSavedWithProfile() {
        launch()
        completeQuestionnaire()
        click("Изменить вручную")
        field("Ккал в день").assertTextContains("2760")
        click("Отмена")
        ui.onNodeWithText("Сохранить цель").assertIsDisplayed()
        click("Изменить вручную")
        field("Ккал в день").performTextReplacement("2500")
        click("Сохранить")
        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Питание") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
        FoodStore(context).use {
            assertEquals(DailyGoals(2500, MacroGoals(128, 72, 400)), it.goalsFor(today))
            val data = it.onboardingData(today)
            assertEquals(GoalKind.MAINTAIN, data.profile?.kind)
            assertEquals(Measurement(today, "80"), data.measurement)
        }
    }

    @Test fun opensPrefilledQuestionnaireFromProfile() {
        FoodStore(context).use { store ->
            store.setGoals(today, goals)
            store.saveMeasurement(Measurement(today, "80", "15"))
            store.writableDatabase.execSQL(
                "INSERT INTO user_profile VALUES (1,'MALE',?,180,'MODERATE','MAINTAIN','2026-11-23T10:15:00Z')",
                arrayOf(birthYear)
            )
        }
        launch()
        ui.onNodeWithText("Ваша цель").assertDoesNotExist()
        click("Профиль")
        ui.onNodeWithText("Мои данные").performScrollTo().assertIsDisplayed()
        capture("profile-mydata-dark.png")
        click("Пересчитать цель")
        ui.onNodeWithText("Ваша цель").assertIsDisplayed()
        click("Далее")
        field("Год рождения").assertTextContains(birthYear)
        field("Рост, см").assertTextContains("180")
        click("Далее")
        field("Вес, кг").assertTextContains("80")
        field("% жира").assertTextContains("15")
        click("Далее")
        ui.onNodeWithText("Далее").assertIsEnabled()
    }

    @Test fun invalidHeightBlocksProgressAndExplainsRange() {
        launch()
        enterHeight("90")
        ui.onNodeWithText("Далее").assertIsNotEnabled()
        ui.onNodeWithText("120–230", substring = true).assertIsDisplayed()
    }

    @Test fun rendersResultInLightTheme() {
        launch(ThemeMode.LIGHT)
        completeQuestionnaire()
        ui.onNodeWithText("Сохранить цель").assertIsDisplayed()
        capture("onboarding-result-light.png")
    }

    private fun launch(mode: ThemeMode = ThemeMode.DARK) = ui.setContent {
        key(instance.intValue) {
            DiaryTheme(mode) {
                view = LocalView.current
                DiaryApp(mode, {})
            }
        }
    }

    private fun capture(filename: String) {
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun completeQuestionnaire() {
        enterHeight("180")
        click("Далее")
        field("Вес, кг").performTextInput("80")
        click("Далее")
        click("3–5 тренировок")
        click("Далее")
    }

    private fun enterHeight(height: String) {
        ui.onNodeWithText("Ваша цель").assertIsDisplayed()
        click("Поддерживать вес")
        click("Далее")
        click("Мужской")
        field("Год рождения").performTextInput(birthYear)
        field("Рост, см").performTextInput(height)
    }

    private fun click(text: String) = ui.onNodeWithText(text).performClick()

    private fun field(label: String) = ui.onNode(hasSetTextAction() and hasText(label))
}
