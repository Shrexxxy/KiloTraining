package app.calorietraining

import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ExercisePhotoZoomTest {
    @get:Rule val ui = createComposeRule()
    private var photo = exerciseCatalog.first()
    private val vertical = checkNotNull(photoForExercise("Т-тяга"))
    private val frame1 get() = "Открыть фото: ${photo.name}, кадр 1"
    private val frame2 get() = "Открыть фото: ${photo.name}, кадр 2"
    private val big1 get() = "Фото: ${photo.name}, кадр 1"
    private val big2 get() = "Фото: ${photo.name}, кадр 2"
    private lateinit var screen: ScreenCapture

    @Test fun tappingFirstFrameOpensFirst() {
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).assertIsDisplayed()
        ui.onNodeWithContentDescription(big2).assertDoesNotExist()
    }

    @Test fun tappingSecondFrameOpensSecond() {
        showForm()
        ui.onNodeWithContentDescription(frame2).performClick()
        ui.onNodeWithContentDescription(big2).assertIsDisplayed()
        ui.onNodeWithContentDescription(big1).assertDoesNotExist()
    }

    @Test fun swipeSwitchesFrames() {
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).performTouchInput { swipeLeft() }
        ui.waitForIdle()
        ui.onNodeWithContentDescription(big2).assertIsDisplayed()
        ui.onNodeWithContentDescription(big2).performTouchInput { swipeRight() }
        ui.waitForIdle()
        ui.onNodeWithContentDescription(big1).assertIsDisplayed()
    }

    @Test fun dragMovesZoomedImage() {
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).performTouchInput { doubleClick() }
        ui.waitForIdle()
        assertEquals(Offset.Zero, pan(big1))
        ui.onNodeWithContentDescription(big1).performTouchInput { swipeLeft(startX = centerX + 40f, endX = centerX - 40f) }
        ui.waitForIdle()
        assertTrue(pan(big1).x < 0f)
        ui.onNodeWithContentDescription(big1).assertIsDisplayed()
    }

    @Test fun doubleTapZoomsInAndOut() {
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        assertEquals("Масштаб 100%", scale(big1))
        ui.onNodeWithContentDescription(big1).performTouchInput { doubleClick() }
        ui.waitForIdle()
        assertEquals("Масштаб 250%", scale(big1))
        ui.onNodeWithContentDescription(big1).performTouchInput { doubleClick() }
        ui.waitForIdle()
        assertEquals("Масштаб 100%", scale(big1))
    }

    @Test fun pinchStaysWithinOneAndFour() {
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).performTouchInput {
            pinch(Offset(centerX - 10f, centerY), Offset(centerX - 150f, centerY),
                Offset(centerX + 10f, centerY), Offset(centerX + 150f, centerY))
        }
        ui.waitForIdle()
        assertEquals("Масштаб 400%", scale(big1))
        ui.onNodeWithContentDescription(big1).performTouchInput {
            pinch(Offset(centerX - 150f, centerY), Offset(centerX - 5f, centerY),
                Offset(centerX + 150f, centerY), Offset(centerX + 5f, centerY))
        }
        ui.waitForIdle()
        assertEquals("Масштаб 100%", scale(big1))
    }

    @Test fun panIsClampedToImageEdges() {
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).performTouchInput { doubleClick() }
        ui.waitForIdle()
        repeat(3) {
            ui.onNodeWithContentDescription(big1).performTouchInput { swipeLeft(startX = right - 5f, endX = left + 5f) }
        }
        ui.waitForIdle()
        val limit = ui.onNodeWithContentDescription(big1).fetchSemanticsNode().size.width * (2.5f - 1f) / 2f
        assertEquals(-limit, pan(big1).x, 1f)
    }

    @Test fun closeButtonReturnsKeepingEnteredText() {
        showForm()
        ui.onNodeWithText("Вес").performTextInput("77")
        ui.onNodeWithContentDescription(frame2).performClick()
        ui.onNodeWithText("Закрыть").performClick()
        ui.onNodeWithContentDescription(big2).assertDoesNotExist()
        ui.onNodeWithText("77").assertIsDisplayed()
    }

    @Test fun systemBackReturnsKeepingEnteredText() {
        showForm()
        ui.onNodeWithText("Вес").performTextInput("77")
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).assertIsDisplayed()
        ui.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithContentDescription(big1).assertDoesNotExist()
        ui.onNodeWithText("77").assertIsDisplayed()
    }

    @Test fun recreationKeepsFrameSwipedTo() {
        val restorer = StateRestorationTester(ui)
        restorer.setContent { Form() }
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).performTouchInput { swipeLeft() }
        ui.waitForIdle()
        ui.onNodeWithContentDescription(big2).assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        ui.waitForIdle()
        ui.onNodeWithContentDescription(big2).assertIsDisplayed()
        ui.onNodeWithContentDescription(big1).assertDoesNotExist()
    }

    @Test fun verticalPhotoIsShownWholeAndPanStaysOnImage() {
        photo = vertical
        showForm()
        ui.onNodeWithContentDescription(frame1).performClick()
        val box = ui.onNodeWithContentDescription(big1).fetchSemanticsNode().size
        val fitted = minOf(box.width / 500f, box.height / 750f).let { Offset(500f * it, 750f * it) }
        val bitmap = screen.captureScreen("photo-zoom-vertical-photo-whole.png", includeDialog = true)
        val top = ((box.height - fitted.y) / 2).toInt() + 6
        val bottom = ((box.height + fitted.y) / 2).toInt() - 6
        assertTrue(rowHasPhoto(bitmap, top))
        assertTrue(rowHasPhoto(bitmap, bottom))
        ui.onNodeWithContentDescription(big1).performTouchInput { doubleClick() }
        ui.waitForIdle()
        repeat(3) {
            ui.onNodeWithContentDescription(big1).performTouchInput { swipeUp(startY = bottom.toFloat(), endY = top.toFloat()) }
        }
        ui.waitForIdle()
        assertEquals(-(fitted.y * 2.5f - box.height) / 2f, pan(big1).y, 1f)
        repeat(6) {
            ui.onNodeWithContentDescription(big1).performTouchInput { swipeDown(startY = top.toFloat(), endY = bottom.toFloat()) }
        }
        ui.waitForIdle()
        assertEquals((fitted.y * 2.5f - box.height) / 2f, pan(big1).y, 1f)
        ui.onNodeWithContentDescription(big1).assertIsDisplayed()
        screen.captureScreen("photo-zoom-vertical-photo-scaled.png", includeDialog = true)
    }

    @Test fun verticalPhotoLightScreenshot() {
        photo = vertical
        capture(ThemeMode.LIGHT, "photo-zoom-vertical-photo-light.png")
    }

    @Test fun recreationKeepsOpenedFrame() {
        val restorer = StateRestorationTester(ui)
        restorer.setContent { Form() }
        ui.onNodeWithContentDescription(frame2).performClick()
        restorer.emulateSavedInstanceStateRestore()
        ui.onNodeWithContentDescription(big2).assertIsDisplayed()
        ui.onNodeWithContentDescription(big1).assertDoesNotExist()
    }

    @Test fun horizontalPhotoDarkScreenshot() = capture(ThemeMode.DARK, "photo-zoom-horizontal-photo-dark.png")
    @Test fun horizontalPhotoLightScreenshot() = capture(ThemeMode.LIGHT, "photo-zoom-horizontal-photo-light.png")

    @Test
    @Config(qualifiers = "ru-rRU-w800dp-h360dp")
    fun horizontalPhotoLandscapeScreenshot() = capture(ThemeMode.DARK, "photo-zoom-horizontal-photo-landscape.png")

    @Test fun zoomedScreenshot() {
        showForm(ThemeMode.DARK)
        ui.onNodeWithContentDescription(frame2).performClick()
        ui.onNodeWithContentDescription(big2).performTouchInput { doubleClick() }
        ui.waitForIdle()
        screen.captureScreen("photo-zoom-horizontal-photo-scaled.png", includeDialog = true)
    }

    private fun capture(theme: ThemeMode, file: String) {
        showForm(theme)
        ui.onNodeWithContentDescription(frame1).performClick()
        ui.onNodeWithContentDescription(big1).assertIsDisplayed()
        screen.captureScreen(file, includeDialog = true)
    }

    private fun rowHasPhoto(bitmap: android.graphics.Bitmap, y: Int) =
        (0 until bitmap.width step 4).any { x -> (bitmap.getPixel(x, y) and 0xFFFFFF) > 0x101010 }

    private fun scale(description: String) = ui.onNodeWithContentDescription(description)
        .fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private fun pan(description: String) = ui.onNodeWithContentDescription(description)
        .fetchSemanticsNode().config[ZoomPanKey]

    private fun showForm(theme: ThemeMode = ThemeMode.DARK) {
        screen = ui.setScreenshotContent(theme) { Form() }
    }

    @Composable
    private fun Form() {
        var weight by rememberSaveable { mutableStateOf("") }
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес") })
            ExerciseFrames(photo)
        }
    }
}
