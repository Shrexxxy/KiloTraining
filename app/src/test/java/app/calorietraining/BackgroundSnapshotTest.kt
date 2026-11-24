package app.calorietraining

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class BackgroundSnapshotTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun backupUsesDiaryBackground() {
        ui.setContent { DiaryTheme(ThemeMode.DARK) { DiaryApp(ThemeMode.DARK, {}) } }
        ui.onNode(hasText("Профиль") and isSelectable()).performClick()
        ui.onNodeWithContentDescription("Настройки").performClick()
        ui.onNodeWithTag("diary-background").assertExists()
        ui.onNodeWithText("Резервная копия").performScrollTo().performClick()
        ui.onNodeWithText("Резервная копия").assertIsDisplayed()
        ui.onNodeWithTag("diary-background").assertExists()
    }

    @Test fun emptyDarkScreenshotShowsDiaryBackground() {
        ui.setScreenshotContent { Box(Modifier.fillMaxSize()) }.captureScreen("empty-dark.png")
        val bitmap = requireNotNull(BitmapFactory.decodeFile("build/screenshots/empty-dark.png"))
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertEquals(255, Color.alpha(pixel))
        assertNotEquals(Color.BLACK, pixel)
    }
}
