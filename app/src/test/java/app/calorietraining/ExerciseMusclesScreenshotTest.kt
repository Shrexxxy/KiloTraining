package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
class ExerciseMusclesScreenshotTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var view: View

    @Test fun cableRowDark() = captureDetails(ThemeMode.DARK, "Тяга горизонтального блока", "muscles-card-dark")
    @Test fun cableRowLight() = captureDetails(ThemeMode.LIGHT, "Тяга горизонтального блока", "muscles-card-light")
    @Test fun squatDark() = captureDetails(ThemeMode.DARK, "Приседания со штангой", "muscles-squat-dark")

    @Test fun catalogDark() {
        catalog(ThemeMode.DARK)
        ui.onAllNodesWithContentDescription("Мышцы и техника").onFirst().assertIsDisplayed()
        capture("catalog-muscles-icon-dark")
    }

    private fun captureDetails(theme: ThemeMode, title: String, filename: String) {
        catalog(theme)
        ui.onNodeWithText("Название или группа мышц").performTextInput(title)
        ui.onNodeWithContentDescription("Мышцы и техника").performClick()
        ui.onNodeWithText(title).assertIsDisplayed()
        capture(filename)
    }

    private fun catalog(theme: ThemeMode) {
        ui.setContent {
            DiaryTheme(theme) {
                view = LocalView.current
                DiaryBackground { ExercisePicker({}, {}, Modifier.padding(horizontal = 20.dp)) }
            }
        }
    }

    private fun capture(filename: String) {
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/screenshots/exercise-muscles").apply { mkdirs() }
        File(directory, "$filename.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
