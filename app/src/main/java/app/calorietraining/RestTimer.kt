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

@Composable
internal fun RestTimer(deadline: Long, total: Long, onChange: (deadline: Long, total: Long) -> Unit) {
    val accent = LocalDiaryAccents.current.workout
    if (deadline == 0L) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Отдых", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            for (minutes in 1..3) {
                FilledTonalButton(onClick = {
                    val duration = minutes * 60_000L
                    onChange(SystemClock.elapsedRealtime() + duration, duration)
                }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("$minutes мин") }
            }
        }
        return
    }
    fun remainingMillis() = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    var remaining by remember(deadline) { mutableLongStateOf(remainingMillis()) }
    LaunchedEffect(deadline) {
        while (true) {
            remaining = remainingMillis()
            if (remaining == 0L) break
            delay((remaining - 1) % 1000 + 1)
        }
    }
    val seconds = (remaining + 999) / 1000
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp).testTag("rest-ring")) {
                val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val diameter = size.minDimension - stroke.width
                drawCircle(accent.copy(alpha = 0.22f), diameter / 2, style = stroke)
                val fraction = restFraction(remaining, total)
                if (fraction > 0) drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset),
                    Size(diameter, diameter), style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ОТДЫХ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 54.sp,
                        fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }
        if (remaining == 0L) Text("Время отдыха закончилось", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (remaining > 0L) {
                FilledTonalButton(onClick = { onChange(deadline + 30_000L, total + 30_000L) }) { Text("+30 с") }
                Button(onClick = { onChange(0, 0) }, colors = ButtonDefaults.buttonColors(containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onTertiary)) { Text("Стоп") }
            } else {
                FilledTonalButton(onClick = { onChange(0, 0) }) { Text("Начать снова") }
                TextButton(onClick = { onChange(0, 0) }) { Text("Закрыть") }
            }
        }
    }
}
