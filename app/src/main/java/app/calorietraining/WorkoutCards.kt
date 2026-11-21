package app.calorietraining

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

internal fun Workout.volume(): BigDecimal = sets.filterNot { it.planned }.sumOf { set ->
    (set.weight.amount() ?: BigDecimal.ZERO) * set.reps.toBigDecimal()
}

internal fun Workout.nextPlanned(): SetDraft? =
    sets.groupBy { it.group }.values.firstNotNullOfOrNull { group -> group.firstOrNull { it.planned } }

private fun SetDraft.tileLabel() = "${if (weight.isBlank()) "Свой вес" else "$weight кг"} × $reps"

private fun BigDecimal.groupedVolume(): String {
    val number = display()
    val integer = number.substringBefore(',')
    val grouped = integer.reversed().chunked(3).joinToString("\u00a0").reversed()
    return grouped + number.substring(integer.length)
}

@Composable
internal fun WorkoutMetrics(workout: Workout) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, contentColor = colors.onSurface, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val metrics = listOf("ПОДХОДЫ" to "${workout.sets.count { !it.planned }}/${workout.sets.size}",
                "ВРЕМЯ" to (workout.minutes?.let { "$it мин" } ?: "—"), "ОБЪЁМ" to "${workout.volume().groupedVolume()} кг")
            metrics.forEachIndexed { index, (name, value) ->
                if (index > 0) VerticalDivider(Modifier.height(30.dp), color = colors.outlineVariant)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun NextSetCard(set: SetDraft, enabled: Boolean, complete: () -> Unit) {
    val accent = LocalDiaryAccents.current.workout
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, contentColor = colors.onSurface,
        border = BorderStroke(1.5.dp, accent)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Следующий · ${set.exercise}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(set.tileLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Button(onClick = complete, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onTertiary)) {
                Text("Засчитать")
            }
        }
    }
}

@Composable
internal fun ExerciseCard(sets: List<SetDraft>, onlyPending: Boolean, enabled: Boolean, edit: (SetDraft) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalDiaryAccents.current.workout
    val exercise = sets.first().exercise
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, contentColor = colors.onSurface) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photoForExercise(exercise)?.let { Image(painterResource(it.second), null, Modifier.size(48.dp)) }
                Text(exercise, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sets.forEachIndexed { index, set ->
                    if (onlyPending && !set.planned) return@forEachIndexed
                    val (background, foreground, status) = when {
                        set.planned -> Triple(colors.surfaceContainerHighest, colors.onSurfaceVariant, "в плане")
                        set.warmup -> Triple(accent.copy(alpha = 0.35f).compositeOver(colors.surfaceContainer), colors.onSurface, "разминка")
                        else -> Triple(accent, colors.onTertiary, "выполнен")
                    }
                    val label = set.tileLabel()
                    Surface(onClick = { edit(set) }, enabled = enabled, shape = RoundedCornerShape(12.dp),
                        color = background, contentColor = foreground,
                        modifier = Modifier.height(40.dp).semantics { contentDescription = "Подход ${index + 1}: $label, $status" }) {
                        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            TextButton(onClick = { edit(sets.last().copy(id = null, planned = false)) }, enabled = enabled) {
                Text("Добавить подход")
            }
        }
    }
}
