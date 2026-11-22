package app.calorietraining

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal fun restFraction(remainingMs: Long, totalMs: Long): Float =
    if (totalMs <= 0) 0f else (remainingMs.toDouble() / totalMs).coerceIn(0.0, 1.0).toFloat()

private fun formatRestMinutes(seconds: Long): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

internal fun formatRestElapsed(seconds: Long): String {
    if (seconds < 3600) return formatRestMinutes(seconds)
    return "${seconds / 3600}:${formatRestMinutes(seconds % 3600).padStart(5, '0')}"
}

@Composable
internal fun RestTimer(deadline: Long, total: Long, onChange: (deadline: Long, total: Long) -> Unit) {
    RestTimer(deadline, total, 0) { end, duration, _ -> onChange(end, duration) }
}

@Composable
internal fun RestTimer(
    deadline: Long,
    total: Long,
    started: Long,
    onChange: (deadline: Long, total: Long, started: Long) -> Unit
) {
    val accent = LocalDiaryAccents.current.workout
    val stopwatch = started != 0L
    if (deadline == 0L && !stopwatch) {
        Column(Modifier.fillMaxWidth()) {
            Text("Отдых", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val padding = PaddingValues(horizontal = 8.dp)
                FilledTonalButton(onClick = { onChange(0, 0, SystemClock.elapsedRealtime()) },
                    contentPadding = padding) { Text("Засечь") }
                for (minutes in 1..3) {
                    FilledTonalButton(onClick = {
                        val duration = minutes * 60_000L
                        onChange(SystemClock.elapsedRealtime() + duration, duration, 0)
                    }, contentPadding = padding) { Text("$minutes мин") }
                }
            }
        }
        return
    }

    fun timerMillis(): Long {
        val now = SystemClock.elapsedRealtime()
        return (if (stopwatch) now - started else deadline - now).coerceAtLeast(0L)
    }

    var millis by remember(deadline, started) { mutableLongStateOf(timerMillis()) }
    LaunchedEffect(deadline, started) {
        while (true) {
            millis = timerMillis()
            if (!stopwatch && millis == 0L) break
            delay(if (stopwatch) 1000 - millis % 1000 else (millis - 1) % 1000 + 1)
        }
    }
    val seconds = if (stopwatch) millis / 1000 else (millis + 999) / 1000
    val running = stopwatch || millis > 0L
    val fraction = if (stopwatch) (seconds % 60) / 60f else restFraction(millis, total)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RestRing(seconds, fraction, stopwatch, 200.dp)
        if (!running) Text("Время отдыха закончилось", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                if (!stopwatch) {
                    FilledTonalButton(onClick = { onChange(deadline + 30_000L, total + 30_000L, 0) }) { Text("+30 с") }
                }
                Button(onClick = { onChange(0, 0, 0) }, colors = ButtonDefaults.buttonColors(containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onTertiary)) { Text("Стоп") }
            } else {
                FilledTonalButton(onClick = { onChange(0, 0, 0) }) { Text("Начать снова") }
                TextButton(onClick = { onChange(0, 0, 0) }) { Text("Закрыть") }
            }
        }
    }
}

@Composable
internal fun WorkoutRestTimer(seconds: Long) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RestRing(seconds, (seconds % 60) / 60f, stopwatch = true, diameter = 144.dp)
    }
}

@Composable
private fun RestRing(seconds: Long, fraction: Float, stopwatch: Boolean, diameter: androidx.compose.ui.unit.Dp) {
    val accent = LocalDiaryAccents.current.workout
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter).testTag("rest-ring")) {
            val stroke = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arc = size.minDimension - stroke.width
            drawCircle(accent.copy(alpha = 0.22f), arc / 2, style = stroke)
            if (fraction > 0) drawArc(accent, -90f, 360f * fraction, false,
                Offset(inset, inset), Size(arc, arc), style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (stopwatch) "ОТДЫХ ИДЁТ" else "ОТДЫХ",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (stopwatch) formatRestElapsed(seconds) else formatRestMinutes(seconds),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (seconds >= 3600) 32.sp else 42.sp,
                    fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
        }
    }
}
