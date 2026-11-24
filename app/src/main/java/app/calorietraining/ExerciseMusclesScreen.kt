package app.calorietraining

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun ExerciseMusclesButton(description: String = "Мышцы и техника", onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.ic_exercise_muscles),
            contentDescription = description,
            tint = LocalDiaryAccents.current.workout,
        )
    }
}

@Composable
internal fun ExerciseMusclesScreen(exercise: ExercisePhoto, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var trail by rememberSaveable(exercise.name) { mutableStateOf(arrayListOf(exercise.name)) }
    val scrollStates = rememberSaveableStateHolder()
    val card = trail.lastOrNull()?.let(::photoForExercise) ?: exercise
    val depth = trail.lastIndex
    scrollStates.SaveableStateProvider(depth) {
        ExerciseMusclesCard(card, onBack = {
            if (depth == 0) onBack() else {
                scrollStates.removeState(depth)
                trail = ArrayList(trail.dropLast(1))
            }
        }, onSimilar = { trail = ArrayList(trail + it.name) }, modifier = modifier)
    }
}

@Composable
private fun ExerciseMusclesCard(
    exercise: ExercisePhoto,
    onBack: () -> Unit,
    onSimilar: (ExercisePhoto) -> Unit,
    modifier: Modifier,
) {
    val technique = exercise.technique
    val accent = LocalDiaryAccents.current.workout
    val secondary = accent.copy(alpha = 0.35f)
    Column(modifier.fillMaxSize()) {
        ScreenHeader(exercise.name, onBack = onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(accent, Modifier.fillMaxWidth()) {
                ExerciseFrames(exercise, Modifier.padding(8.dp))
            }
            GlassCard(accent, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleDiagram(technique, accent, secondary)
                    MuscleLegend("Основные", technique.primary, accent)
                    MuscleLegend("Дополнительные", technique.secondary, secondary)
                }
            }
            GlassCard(accent, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Как делать", style = MaterialTheme.typography.titleMedium)
                    technique.steps.forEachIndexed { index, step ->
                        Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val similar = remember(exercise) { similarExercises(exercise) }
            if (similar.isNotEmpty()) {
                GlassCard(accent, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Похожие", style = MaterialTheme.typography.titleMedium)
                        similar.forEach { photo ->
                            Text(
                                photo.name,
                                Modifier.fillMaxWidth().clickable { onSimilar(photo) }.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleLegend(label: String, muscles: List<Muscle>, color: Color) {
    val names = muscles.joinToString { it.label }.ifEmpty { "—" }
    Text(
        "$label: $names",
        modifier = Modifier.background(color.copy(alpha = color.alpha * 0.24f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}
