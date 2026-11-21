package app.calorietraining

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun RestTimer(deadline: Long, onDeadlineChanged: (Long) -> Unit, modifier: Modifier = Modifier) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    fun remainingMillis() = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    var seconds by remember(deadline) { mutableLongStateOf((remainingMillis() + 999) / 1000) }
    LaunchedEffect(deadline) {
        while (true) {
            val millis = remainingMillis()
            seconds = (millis + 999) / 1000
            if (millis == 0L) break
            delay((millis - 1) % 1000 + 1)
        }
    }
    Column(modifier.fillMaxWidth()) {
        when {
            deadline == 0L -> {
                OutlinedButton(onClick = { choosing = !choosing }) { Text("Таймер отдыха") }
                if (choosing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (minutes in 1..3) {
                        TextButton(onClick = {
                            choosing = false
                            onDeadlineChanged(SystemClock.elapsedRealtime() + minutes * 60_000L)
                        }) { Text("$minutes мин") }
                    }
                }
            }
            seconds > 0L -> {
                Text("Отдых · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { onDeadlineChanged(maxOf(deadline, SystemClock.elapsedRealtime()) + 30_000L) }) {
                        Text("+30 секунд")
                    }
                    TextButton(onClick = { onDeadlineChanged(0L) }) { Text("Завершить") }
                }
            }
            else -> {
                Text("Время отдыха закончилось", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onDeadlineChanged(0L); choosing = true }) { Text("Начать снова") }
                TextButton(onClick = { onDeadlineChanged(0L) }) { Text("Закрыть") }
            }
        }
    }
}
