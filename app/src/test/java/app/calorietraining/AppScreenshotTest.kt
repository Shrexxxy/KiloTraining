package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class AppScreenshotTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun food() = capture("Питание", "app-food-dark.png")
    @Test fun profile() = capture("Профиль", "app-profile-dark.png")

    private fun capture(tab: String, filename: String) {
        lateinit var view: View
        ui.setContent { DiaryTheme(ThemeMode.DARK) {
            view = LocalView.current
            DiaryApp(ThemeMode.DARK, {})
        } }
        val ready = if (tab == "Профиль") {
            ui.onNodeWithText(tab).performClick()
            "Сравнение периодов"
        } else "За этот день пока нет записей"
        ui.waitUntil(10_000) { ui.onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty() }
        // В Robolectric captureToImage ждёт обратный вызов перерисовки окна, который здесь не приходит.
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
