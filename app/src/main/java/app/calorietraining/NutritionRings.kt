package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Доля заполнения кольца, превышение цели и наличие данных. */
data class RingState(val fraction: Float, val over: Int?, val known: Boolean)

/** Рассчитывает заполнение для показателя с заданной целью. */
fun ringState(total: BigDecimal?, goal: Int?): RingState? {
    if (goal == null) return null
    if (total == null) return RingState(0f, null, false)
    val target = goal.toBigDecimal()
    val excess = (total - target).takeIf { it.signum() > 0 }
    val fraction = if (goal == 0) {
        if (excess == null) 1f else 2f
    } else {
        total.divide(target, MathContext.DECIMAL64).toFloat().coerceIn(0f, 2f)
    }
    return RingState(fraction,
        excess?.setScale(0, RoundingMode.HALF_UP)?.min(Int.MAX_VALUE.toBigDecimal())?.toInt(), true)
}

private fun BigDecimal.ringNumber(grouped: Boolean = true): String {
    val digits = setScale(0, RoundingMode.HALF_UP).toPlainString()
    return if (grouped) digits.reversed().chunked(3).joinToString("\u00a0").reversed() else digits
}

private data class Metric(val name: String, val total: BigDecimal?, val goal: Int?, val unit: String, val color: Color) {
    val ring = ringState(total, goal)
}

/** Показывает итоги питания и прогресс по целям выбранного дня. */
@Composable
internal fun NutritionRings(
    calories: BigDecimal, macros: Macros, goals: DailyGoals, hasEntries: Boolean,
    today: Boolean, edit: () -> Unit
) {
    val accents = LocalDiaryAccents.current
    val metrics = listOf(
        Metric("Калории", calories.takeIf { hasEntries || goals.kcal != null }, goals.kcal, "ккал", accents.kcal),
        Metric("Углеводы", macros.carbs.amount(), goals.macros.carbs, "г", accents.carbs),
        Metric("Белки", macros.protein.amount(), goals.macros.protein, "г", accents.protein),
        Metric("Жиры", macros.fat.amount(), goals.macros.fat, "г", accents.fat)
    ).filter { it.total != null || it.goal != null }
    val rings = metrics.filter { it.ring != null }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (rings.isNotEmpty()) RingChart(rings)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                metrics.forEach { metric ->
                    Column {
                        Text(metric.name.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow {
                            Text(metric.total?.ringNumber() ?: "—", Modifier.alignByBaseline(), color = metric.color,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            metric.goal?.let {
                                val unit = if (metric.unit == "г") " г" else ""
                                Text(" / ${it.toBigDecimal().ringNumber()}$unit", Modifier.alignByBaseline(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                            }
                            if (metric.unit == "г") metric.ring?.over?.let {
                                Text("+${it.toBigDecimal().ringNumber()} г", Modifier.alignByBaseline().padding(start = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (metric.unit == "ккал" && metric.goal != null) {
                            val remaining = metric.goal.toBigDecimal() - calories
                            val amount = remaining.abs().ringNumber()
                            val status = when {
                                remaining.signum() == 0 -> "цель выполнена"
                                remaining.signum() < 0 -> "сверх $amount ккал"
                                today -> "осталось $amount ккал"
                                else -> "недобор $amount ккал"
                            }
                            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (metrics.any { it.total == null }) Text("Прочерк означает, что для итога не хватает данных",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = edit) {
            Text(if (goals == DailyGoals()) "Задать дневную цель" else "Изменить цель")
        }
    }
}

@Composable
private fun RingChart(rings: List<Metric>) {
    val description = rings.joinToString(", ") { metric ->
        val excess = metric.ring?.over?.takeIf { metric.unit == "г" }
        val progress = "${metric.name} ${metric.total?.ringNumber(false) ?: "нет данных"} из ${metric.goal} ${metric.unit}"
        if (excess == null) progress else "$progress, сверх ${excess.toBigDecimal().ringNumber()} г"
    }
    val contrast = if (LocalDiaryDark.current) Color.White else MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(150.dp).testTag("nutrition-rings").semantics { contentDescription = description }) {
        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        val overflowStroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
        rings.forEachIndexed { index, metric ->
            val inset = stroke.width / 2 + index * 16.dp.toPx()
            val radius = size.minDimension / 2 - inset
            drawCircle(metric.color.copy(alpha = 0.22f), radius, style = stroke)
            val fraction = requireNotNull(metric.ring).fraction
            val topLeft = Offset(inset, inset)
            val diameter = Size(radius * 2, radius * 2)
            if (fraction >= 1f) drawCircle(metric.color, radius, style = stroke)
            else if (fraction > 0f) drawArc(metric.color, -90f, fraction * 360f, false,
                topLeft, diameter, style = stroke)
            if (fraction > 1f) {
                val sweep = (fraction - 1f) * 360f
                val end = sweep - 90f
                val radians = Math.toRadians(end.toDouble())
                val tip = center + Offset(cos(radians).toFloat(), sin(radians).toFloat()) * radius
                val shadowCenter = tip + Offset(-sin(radians).toFloat(), cos(radians).toFloat()) * 2.dp.toPx()
                val shadowRadius = 8.dp.toPx()
                val color = lerp(metric.color, contrast, .45f)
                if (fraction == 2f) drawCircle(color, radius, style = overflowStroke)
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .72f), Color.Transparent),
                    shadowCenter, shadowRadius), shadowRadius, shadowCenter)
                // На полном обороте последние градусы закрывают тень с одной стороны стыка.
                val visibleSweep = if (fraction == 2f) 18f else sweep
                drawArc(color, end - visibleSweep, visibleSweep, false,
                    topLeft, diameter, style = overflowStroke)
            }
        }
    }
}
