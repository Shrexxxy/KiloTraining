package app.calorietraining
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class MvpScreenTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    fun node(t: String) = ui.onNodeWithText(t)
    fun waitText(t: String) {
        try { ui.waitUntil(10000) { ui.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: ComposeTimeoutException) { throw AssertionError("Missing $t: " + ui.onRoot().printToString(),e) }
    }

    @Test fun opensScanner() {
        waitText("Завтрак")
        ui.addInMeal(Meal.BREAKFAST)
        ui.onNodeWithContentDescription("Сканировать штрихкод").performClick()
        node("Открыть камеру").assertIsDisplayed()
        node("Цифры штрихкода").assertIsDisplayed()
    }
    @Test fun scanCallbackAddsCachedPortion() {
        waitText("Завтрак")
        FoodStore(ui.activity.application).use { it.cacheProduct("12345678",Product("Йогурт","120")) }
        ui.addInMeal(Meal.BREAKFAST)
        ui.onNodeWithContentDescription("Сканировать штрихкод").performClick()
        node("Открыть камеру").performClick()
        val started = org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivityForResult
        org.junit.Assert.assertTrue(started.intent.component!!.className.contains("CaptureActivity"))
        val result = android.content.Intent().putExtra("SCAN_RESULT","12345678")
        ui.runOnUiThread {
            ui.activity.activityResultRegistry.dispatchResult(started.requestCode,android.app.Activity.RESULT_OK,result)
        }
        waitText("Йогурт")
        node("Вес порции, г").performTextInput("75")
        node("Сохранить").performClick()
        waitText("90")
    }

    @Test fun savesAndEditsSet() {
        node("Тренировки").performClick()
        waitText("Начать тренировку")
        node("Начать тренировку").performClick()
        waitText("Добавить упражнение")
        node("Добавить упражнение").performClick()
        node("Название упражнения").performTextInput("Жим лёжа")
        node("Вес, кг").performTextInput("40")
        node("Повторы").performTextInput("12")
        node("Записать подход").performScrollTo().performClick()
        waitText("40 кг × 12")
        node("40 кг × 12").performClick()
        node("Повторы").performTextReplacement("9")
        node("Сохранить изменения").performScrollTo().performClick()
        waitText("40 кг × 9")
    }
}
