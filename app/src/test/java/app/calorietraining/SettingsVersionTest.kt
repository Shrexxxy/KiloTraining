package app.calorietraining

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h900dp")
class SettingsVersionTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun installed() = app.packageManager.getPackageInfo(app.packageName, 0)
        .let { it.versionName to it.longVersionCode }

    private fun show(theme: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f) =
        ui.setScreenshotContent(theme) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SettingsScreen(ProfileViewModel(app), theme, {}, {}, {})
            }
        }

    @Test fun debugBuildShowsVersionWithDev() {
        val (name, code) = installed()
        assertTrue(app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        show()
        ui.onNodeWithText("Версия $name ($code) · dev").performScrollTo().assertIsDisplayed()
    }

    @Test fun releaseBuildShowsVersionWithoutDev() {
        val (name, code) = installed()
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        show()
        ui.onNodeWithText("Версия $name ($code)").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("dev", substring = true).assertDoesNotExist()
    }

    @Test fun bottomOfSettingsAtLargeFontDark() = bottomShot(ThemeMode.DARK, "settings-version-dark.png")
    @Test fun topOfSettingsLight() {
        show(ThemeMode.LIGHT).captureScreen("settings-top-light.png")
    }

    @Test fun bottomOfSettingsAtLargeFontLight() = bottomShot(ThemeMode.LIGHT, "settings-version-light.png")

    private fun bottomShot(theme: ThemeMode, file: String) {
        val (name, code) = installed()
        val screen = show(theme, fontScale = 2f)
        ui.onNodeWithText("Версия $name ($code)", substring = true).performScrollTo().assertIsDisplayed()
        screen.captureScreen(file)
    }
}
