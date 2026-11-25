package app.calorietraining

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
import java.time.Duration

internal fun Workout.volume(): BigDecimal = sets.filterNot { it.planned }.sumOf { set ->
    (set.weight.amount() ?: BigDecimal.ZERO) * set.reps.toBigDecimal()
}

internal fun Workout.nextPlanned(): SetDraft? =
    sets.groupBy { it.group }.values.firstNotNullOfOrNull { group -> group.firstOrNull { it.planned } }

internal fun List<SetDraft>.inExecutionOrder(): List<SetDraft> =
    sortedWith(compareBy({ it.planned }, { it.doneAt }, { it.id }))

private fun SetDraft.tileLabel() = "${if (weight.isBlank()) "Свой вес" else "$weight кг"} × $reps"

internal fun BigDecimal.groupedNumber(): String {
    val number = display()
    val digits = number.removePrefix("-")
    val integer = digits.substringBefore(',')
    val grouped = integer.reversed().chunked(3).joinToString("\u00a0").reversed()
    return (if (number.startsWith("-")) "-" else "") + grouped + digits.substring(integer.length)
}

@Composable
internal fun WorkoutMetrics(workout: Workout, minutes: Long?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val metrics = listOf("ПОДХОДЫ" to "${workout.sets.count { !it.planned }}/${workout.sets.size}",
            "ВРЕМЯ" to (minutes?.let { "$it мин" } ?: "—"), "ОБЪЁМ" to "${workout.volume().groupedNumber()} кг")
        metrics.forEachIndexed { index, (name, value) ->
            if (index > 0) VerticalDivider(Modifier.height(30.dp), color = colors.outlineVariant)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun NextSetCard(set: SetDraft, enabled: Boolean, complete: () -> Unit) {
    val accent = LocalDiaryAccents.current.workout
    val colors = MaterialTheme.colorScheme
    GlassCard(accent) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Следующий · ${set.exercise}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(set.tileLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Button(onClick = complete, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = LocalDiaryAccents.current.onWorkout)) {
                Text("Засчитать")
            }
        }
    }
}

@Composable
internal fun ExerciseCard(
    sets: List<SetDraft>,
    rests: Map<Long?, Duration>,
    onMuscles: (String) -> Unit,
    readOnly: Boolean = false,
    edit: (SetDraft) -> Unit,
) {
    val exercise = sets.first().exercise
    val photo = photoForExercise(exercise)
    GlassCard(LocalDiaryAccents.current.workout) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photo?.let { Image(painterResource(it.second), null, Modifier.size(48.dp)) }
                Text(
                    exercise, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                if (exercise.isNotBlank()) {
                    ExerciseMusclesButton(if (photo == null) "Мышцы упражнения" else "Мышцы и техника") {
                        onMuscles(exercise)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sets.forEachIndexed { index, set ->
                    WorkoutSetTile(set.weight, set.reps, set.warmup, set.planned, index + 1,
                        caption = rests[set.id]?.let { "отдых ${formatRestElapsed(it.seconds)}" },
                        onClick = if (readOnly) null else ({ edit(set) }))
                }
            }
            if (!readOnly) TextButton(onClick = { edit(sets.maxBy { it.id ?: 0L }.copy(id = null, planned = false, doneAt = null)) }) {
                Text("+ Подход")
            }
        }
    }
}

@Composable
internal fun WorkoutSetTile(
    weight: String,
    reps: String,
    warmup: Boolean = false,
    planned: Boolean = false,
    number: Int? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalDiaryAccents.current.workout
    val (background, foreground, status) = when {
        planned -> Triple(colors.surfaceContainerHighest, colors.onSurfaceVariant, "в плане")
        warmup -> Triple(accent.copy(alpha = 0.35f).compositeOver(colors.surfaceContainer), colors.onSurface, "разминка")
        else -> Triple(accent, LocalDiaryAccents.current.onWorkout, "выполнен")
    }
    val label = "${if (weight.isBlank()) "Свой вес" else "$weight кг"} × $reps"
    val description = when {
        number != null -> "Подход $number: $label, $status"
        warmup -> "Разминка"
        else -> null
    }
    val modifier = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable () -> Unit = {
        Column(Modifier.heightIn(min = 40.dp).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (onClick == null) Surface(modifier, shape, background, foreground, content = content)
    else Surface(onClick = onClick, modifier = modifier, shape = shape, color = background,
        contentColor = foreground, content = content)
}
