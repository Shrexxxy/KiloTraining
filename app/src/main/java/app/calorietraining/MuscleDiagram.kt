package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun MuscleDiagram(technique: ExerciseTechnique, primary: Color, secondary: Color) {
    val names = technique.primary.joinToString { it.label }
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Основные мышцы: $names. Вид спереди и сзади." },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BodyFigure("Спереди", frontMuscles, technique, primary, secondary, outline, Modifier.weight(1f))
        BodyFigure("Сзади", backMuscles, technique, primary, secondary, outline, Modifier.weight(1f))
    }
}

@Composable
private fun BodyFigure(
    caption: String,
    regions: List<MuscleRegion>,
    technique: ExerciseTechnique,
    primary: Color,
    secondary: Color,
    outline: Color,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(204.dp)) {
            val factor = minOf(size.width / 100f, size.height / 216f)
            val left = (size.width - 100f * factor) / 2f
            val top = (size.height - 216f * factor) / 2f
            val border = Stroke(0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            translate(left, top) {
                scale(factor, factor, Offset.Zero) {
                    for (direction in listOf(1f, -1f)) {
                        scale(direction, 1f, Offset(50f, 0f)) {
                            drawPath(bodyOutline, outline, style = border)
                            for ((muscle, path) in regions) {
                                val fill = when (muscle) {
                                    in technique.primary -> primary
                                    in technique.secondary -> secondary
                                    else -> Color.Transparent
                                }
                                drawPath(path, fill)
                                drawPath(path, outline.copy(alpha = 0.66f), style = border)
                            }
                        }
                    }
                }
            }
        }
        Text(caption, style = MaterialTheme.typography.labelMedium, color = outline)
    }
}

private data class MuscleRegion(val muscle: Muscle, val path: Path)

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

private fun region(muscle: Muscle, data: String) = MuscleRegion(muscle, path(data))

private val bodyOutline = path(
    "M50,3 C61,3 65,11 64,21 Q63,31 57,35 L59,43 Q65,46 76,48 " +
        "Q85,52 85,65 L88,88 Q92,94 94,108 L97,120 Q101,126 98,135 " +
        "Q95,139 92,132 L89,127 Q86,131 85,124 L85,119 Q80,109 78,98 " +
        "L74,83 Q69,93 70,103 Q77,118 74,137 L69,160 Q71,169 68,180 " +
        "L65,198 Q65,203 70,207 Q73,212 62,212 L55,212 Q52,211 54,204 " +
        "L55,187 Q52,173 56,160 L54,143 L50,123"
)

private val frontMuscles = listOf(
    region(Muscle.Neck, "M51,36 Q54,36 56,34 L58,43 L51,48 Z"),
    region(Muscle.Traps, "M59,44 L73,49 L62,49 Z"),
    region(Muscle.Shoulders, "M72,51 Q83,49 84,65 L75,70 Q72,65 72,51 Z"),
    region(Muscle.Chest, "M51,51 Q62,48 70,51 L73,66 Q63,73 51,67 Z"),
    region(Muscle.Abdominals, "M51,71 Q57,73 61,72 Q61,92 59,102 L52,114 L51,104 Z"),
    region(Muscle.Abdominals, "M63,74 L71,70 L72,79 Q67,88 68,99 L61,103 Z"),
    region(Muscle.Biceps, "M76,73 L84,68 L87,87 Q84,93 79,89 Z"),
    region(Muscle.Forearms, "M81,94 L88,90 Q92,101 94,117 L87,119 Z"),
    region(Muscle.Abductors, "M65,103 L69,101 Q75,113 73,122 L69,126 Z"),
    region(Muscle.Adductors, "M58,110 L62,113 L61,141 L56,152 L52,124 Z"),
    region(Muscle.Quadriceps, "M64,115 Q71,124 72,129 Q71,148 66,157 L58,156 Q64,139 64,115 Z"),
    region(Muscle.Calves, "M58,163 Q65,160 68,167 L65,183 L59,199 Q59,185 56,176 Z"),
)

private val backMuscles = listOf(
    region(Muscle.Neck, "M51,34 L56,34 L58,44 L51,48 Z"),
    region(Muscle.Traps, "M52,49 L59,44 L73,49 Q63,51 59,61 L51,74 Z"),
    region(Muscle.Shoulders, "M72,52 Q82,49 84,64 L76,72 Q70,65 68,56 Z"),
    region(Muscle.MiddleBack, "M62,59 L67,56 L72,70 Q63,77 52,83 L52,76 Z"),
    region(Muscle.Lats, "M73,73 Q71,84 67,96 L61,102 L55,85 Q65,82 73,73 Z"),
    region(Muscle.LowerBack, "M52,85 L59,100 L56,107 L51,105 Z"),
    region(Muscle.Triceps, "M77,74 L84,68 L87,88 L80,91 Z"),
    region(Muscle.Forearms, "M81,94 L88,91 Q92,102 95,117 L87,119 Z"),
    region(Muscle.Glutes, "M51,109 L66,101 Q72,107 71,118 Q66,127 53,122 L51,118 Z"),
    region(Muscle.Abductors, "M72,114 Q76,127 72,139 L69,130 Z"),
    region(Muscle.Hamstrings, "M61,126 L68,124 Q73,143 65,157 L57,154 L54,125 Z"),
    region(Muscle.Calves, "M58,163 L66,161 Q71,171 66,183 L61,198 L57,195 Q58,186 56,177 Z"),
)
