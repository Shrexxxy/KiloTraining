package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.io.File
import org.robolectric.shadows.ShadowDialog

internal fun ComposeContentTestRule.setScreenshotContent(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
): ScreenCapture = setScreenshotContent({ mode }, content)

internal fun ComposeContentTestRule.setScreenshotContent(
    mode: @Composable () -> ThemeMode,
    content: @Composable () -> Unit,
): ScreenCapture {
    lateinit var view: View
    setContent {
        DiaryTheme(mode()) {
            view = LocalView.current
            DiaryBackground { content() }
        }
    }
    return ScreenCapture(this) { view }
}

internal class ScreenCapture(
    private val ui: ComposeContentTestRule,
    private val view: () -> View,
) {
    fun captureScreen(filename: String, includeDialog: Boolean = false, heightPx: Int? = null): Bitmap {
        // В Robolectric captureToImage ждёт перерисовки окна, которая здесь не вызывается.
        val bitmap = ui.runOnIdle {
            val root = view()
            val height = heightPx?.coerceIn(1, root.height) ?: root.height
            Bitmap.createBitmap(root.width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                root.draw(canvas)
                if (includeDialog) {
                    val window = requireNotNull(ShadowDialog.getLatestDialog().window)
                    val decor = window.decorView
                    if (window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                        canvas.drawColor((window.attributes.dimAmount * 255).toInt() shl 24)
                    }
                    canvas.translate((root.width - decor.width) / 2f, (root.height - decor.height) / 2f)
                    decor.draw(canvas)
                }
            }
        }
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, filename).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }
}
