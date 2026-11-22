package app.calorietraining

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import java.math.BigDecimal
import java.math.MathContext
import java.time.format.DateTimeFormatter

private data class ProgressPoint(val session: SessionHistory, val value: BigDecimal)

private fun recentMaxima(sessions: List<SessionHistory>, measure: (PreviousSet) -> BigDecimal?): List<ProgressPoint> =
    sessions.asSequence().mapNotNull { session ->
        session.sets.mapNotNull(measure).maxOrNull()?.let { ProgressPoint(session, it) }
    }.take(8).toList().asReversed()

internal data class RepRecord(val reps: Int, val weight: BigDecimal, val session: SessionHistory)

internal fun repetitionRecords(sessions: List<SessionHistory>): List<RepRecord> {
    val best = sortedMapOf<Int, RepRecord>()
    for (session in sessions) for (set in session.sets) {
        if (set.warmup) continue
        val reps = set.reps.toIntOrNull() ?: continue
        val weight = set.weight.amount() ?: continue
        val previous = best[reps]
        if (previous == null || weight > previous.weight) best[reps] = RepRecord(reps, weight, session)
    }
    return best.values.toList()
}

private val chartDate = DateTimeFormatter.ofPattern("dd.MM.yy")

@Composable
internal fun ExerciseProgress(exercise: String, sessions: List<SessionHistory>,
    onWorkout: (SessionHistory) -> Unit, modifier: Modifier = Modifier) {
    val records = remember(sessions) { repetitionRecords(sessions) }
    val weights = remember(sessions) { recentMaxima(sessions) { it.weight.amount() } }
    val repetitions = remember(sessions) {
        recentMaxima(sessions) { if (it.weight.isBlank()) it.reps.toIntOrNull()?.toBigDecimal() else null }
    }
    if (weights.isEmpty() && repetitions.isEmpty()) return
    var preferReps by rememberSaveable(exercise) { mutableStateOf(false) }
    val showReps = weights.isEmpty() || (preferReps && repetitions.isNotEmpty())
    val points = if (showReps) repetitions else weights
    val unit = if (showReps) "повт." else "кг"
    val maximum = points.maxOf { it.value }
    val colors = MaterialTheme.colorScheme
    val workout = LocalDiaryAccents.current.workout
    GlassCard(LocalDiaryAccents.current.workout, modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (showReps) "Максимум повторений со своим весом" else "Максимальный вес за тренировку",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (weights.isNotEmpty() && repetitions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showReps, { preferReps = false }, label = { Text("С весом") })
                    FilterChip(showReps, { preferReps = true }, label = { Text("Свой вес") })
                }
            }
            if (records.isNotEmpty()) {
                var expanded by rememberSaveable(exercise) { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Скрыть рекорды" else "Рекорды по повторам")
                }
                if (expanded) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    records.forEach { record ->
                        WorkoutSetTile(record.weight.groupedNumber(), record.reps.toString(),
                            caption = record.session.date.format(chartDate), onClick = { onWorkout(record.session) })
                    }
                }
            }
            points.forEach { point ->
                val fraction = if (maximum.signum() == 0) 0f else point.value.divide(maximum, MathContext.DECIMAL32).toFloat()
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Открыть тренировку") { onWorkout(point.session) }
                    .heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(point.session.date.format(chartDate), style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f).height(10.dp).background(colors.surfaceContainerHighest, MaterialTheme.shapes.small)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(workout, MaterialTheme.shapes.small))
                    }
                    Text("${point.value.groupedNumber()} $unit", modifier = Modifier.widthIn(min = 60.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
