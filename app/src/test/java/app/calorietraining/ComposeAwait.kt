package app.calorietraining

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.Shadows.shadowOf

internal fun ComposeTestRule.awaitUntil(timeoutMillis: Long, condition: () -> Boolean) {
    waitUntil(timeoutMillis) {
        // Продолжения после IO ждут обработки главной очереди Robolectric.
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}
