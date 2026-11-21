package app.calorietraining

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay

@Composable
internal fun DelayedProgress(busy: Boolean) {
    if (!busy) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    if (visible) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
}
