package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiaryThemeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun switchesBackgroundAndPrimary() {
        val mode = mutableStateOf(ThemeMode.DARK)
        var background = Color.Unspecified
        var primary = Color.Unspecified
        ui.setContent {
            DiaryTheme(mode.value) {
                background = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
            }
        }
        ui.runOnIdle {
            assertEquals(Color(0xFF000000), background)
            assertEquals(Color(0xFFB4F03C), primary)
            mode.value = ThemeMode.LIGHT
        }
        ui.runOnIdle {
            assertEquals(Color(0xFFF2F2F5), background)
            assertEquals(Color(0xFF487800), primary)
        }
    }
}
