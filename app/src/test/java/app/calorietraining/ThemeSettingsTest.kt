package app.calorietraining

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSettingsTest {
    @Test fun defaultsToSystemAndRemembersDark() {
        val context = RuntimeEnvironment.getApplication()
        val settings = ThemeSettings(context)
        assertEquals(ThemeMode.SYSTEM, settings.mode)
        settings.mode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, ThemeSettings(context).mode)
    }
}
