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
    training: Boolean = false,
    content: @Composable () -> Unit,
): ScreenCapture = setScreenshotContent({ mode }, training, content)

internal fun ComposeContentTestRule.setScreenshotContent(
    mode: @Composable () -> ThemeMode,
    training: Boolean = false,
    content: @Composable () -> Unit,
): ScreenCapture {
    lateinit var view: View
    setContent {
        DiaryTheme(mode()) {
            view = LocalView.current
            DiaryBackground(training) { if (training) TrainingPalette(content) else content() }
        }
    }
    return ScreenCapture(this) { view }
}

internal class ScreenCapture(
    private val ui: ComposeContentTestRule,
    private val view: () -> View,
) {
    fun captureScreen(
        filename: String,
        includeDialog: Boolean = false,
        heightPx: Int? = null,
        includePopups: Boolean = false,
    ): Bitmap {
        // В Robolectric captureToImage ждёт перерисовки окна, которая здесь не вызывается.
        val bitmap = ui.runOnIdle {
            val root = view()
            val height = heightPx?.coerceIn(1, root.height) ?: root.height
            Bitmap.createBitmap(root.width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                root.draw(canvas)
                if (includePopups) drawPopups(root, canvas)
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

    /** Выпадающие меню живут в отдельных окнах, поэтому рисуем их поверх корня по координатам окна. */
    private fun drawPopups(root: View, canvas: Canvas) {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val views = field.get(instance) as List<View>
        views.filter { it !== root.rootView && it.width > 0 && it.height > 0 }.forEach { popup ->
            val params = popup.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            canvas.save()
            canvas.translate(params.x.toFloat(), params.y.toFloat())
            popup.draw(canvas)
            canvas.restore()
        }
    }
}
