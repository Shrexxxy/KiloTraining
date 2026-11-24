package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.RoundingMode.HALF_UP
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun Modifier.chartDescription(summary: String) =
    semantics { contentDescription = "График. $summary" }

internal val profileChartDate = DateTimeFormatter.ofPattern("dd.MM")

@Composable
internal fun ChartDates(first: LocalDate, last: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ChartLabel(first.format(profileChartDate))
        if (last != first) ChartLabel(last.format(profileChartDate))
    }
}

@Composable
internal fun ChartLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ChartBars(
    values: List<Float?>, color: Color, summary: String, goals: List<Float?> = emptyList(),
    height: Int = 104,
) {
    if (values.isEmpty()) return
    val max = maxOf(values.filterNotNull().maxOrNull() ?: 0f, goals.filterNotNull().maxOrNull() ?: 0f, 1f)
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(height.dp).chartDescription(summary)) {
        val floor = size.height - 1.dp.toPx()
        val plot = floor - 6.dp.toPx()
        val slot = size.width / values.size
        val width = (slot * .7f).coerceAtMost(24.dp.toPx())
        fun y(value: Float) = floor - plot * value / max
        drawLine(guide.copy(alpha = .35f), Offset(0f, floor), Offset(size.width, floor), 1.dp.toPx())
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val goal = goals.getOrNull(index)
            val top = minOf(y(value), floor - 2.dp.toPx())
            val bright = if (goals.isEmpty()) values.size != 2 || index == 1 else goal != null && value > goal
            drawRoundRect(color.copy(alpha = if (bright) 1f else .55f),
                Offset(slot * (index + .5f) - width / 2, top), Size(width, floor - top),
                CornerRadius((width / 3).coerceAtMost(3.dp.toPx())))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
        goals.forEachIndexed { index, goal ->
            if (goal == null) return@forEachIndexed
            val x = slot * index
            val level = y(goal)
            val previous = goals.getOrNull(index - 1)
            if (previous != null && previous != goal) drawLine(guide, Offset(x, y(previous)),
                Offset(x, level), 1.dp.toPx(), pathEffect = dash)
            drawLine(guide, Offset(x, level), Offset(x + slot, level), 1.dp.toPx(), pathEffect = dash)
        }
    }
}

@Composable
internal fun CaloriesChart(series: ProfileSeries, stats: PeriodStats, period: String) {
    val average = stats.averageCalories
    if (average == null) {
        Text("Добавь запись питания.")
        return
    }
    val points = series.calories
    val color = LocalDiaryAccents.current.kcal
    val averageText = average.setScale(0, HALF_UP).display()
    val count = series.aboveGoalDays
    val dayWord = when {
        count % 100 in 11..14 -> "дней"
        count % 10 == 1 -> "день"
        count % 10 in 2..4 -> "дня"
        else -> "дней"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${average.setScale(0, HALF_UP).groupedNumber()} ккал", style = MaterialTheme.typography.headlineSmall, color = color)
        ChartLabel("В среднем за день")
    }
    ChartBars(points.map { it.kcal?.toFloat() }, color,
        "Калории $period, среднее $averageText ккал, выше цели $count $dayWord, дней с записями ${stats.foodDays}",
        goals = points.map { it.goal?.toFloat() })
    ChartDates(points.first().day, points.last().day)
}

@Composable
internal fun MacrosChart(series: ProfileSeries) {
    val (protein, fat, carbs) = series.macros
    val values = listOf(protein, fat, carbs)
    if (values.all { it == null }) {
        Text("Добавь БЖУ к записям питания.")
        return
    }
    val accents = LocalDiaryAccents.current
    val colors = listOf(accents.protein, accents.fat, accents.carbs)
    val labels = listOf("Белки", "Жиры", "Углеводы")
    val shares = series.macros.calorieShares
    val sharesText = labels.zip(shares).joinToString(", ") { (label, share) -> "$label $share%" }
    if (shares.isNotEmpty()) {
        Canvas(Modifier.fillMaxWidth().height(12.dp).chartDescription("Доли калорий, $sharesText")) {
            var x = 0f
            shares.forEachIndexed { index, share ->
                val width = size.width * share / 100
                drawRect(colors[index], Offset(x, 0f), Size(width, size.height))
                x += width
            }
        }
    }
    val (proteinGoal, fatGoal, carbsGoal) = series.macroGoals
    val goals = listOf(proteinGoal, fatGoal, carbsGoal)
    labels.forEachIndexed { index, label ->
        val value = values[index]
        val goal = goals[index]
        val grams = value?.display()?.let { "$it г" } ?: "—"
        val amount = if (goal == null) grams else "$grams / $goal г"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label + (shares.getOrNull(index)?.let { " · $it%" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            Text(amount, style = MaterialTheme.typography.bodyMedium)
        }
        if (value != null && goal != null) {
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            val fill = if (goal == 0) if (value.signum() == 0) 0f else 1f else (value.toFloat() / goal).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(5.dp).chartDescription("$label, среднее $grams, цель $goal г")) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                drawRoundRect(colors[index], size = Size(size.width * fill, size.height), cornerRadius = radius)
            }
        }
    }
}

@Composable
internal fun BmiChart(bmi: Bmi) {
    val accents = LocalDiaryAccents.current
    val marker = MaterialTheme.colorScheme.onSurface
    val colors = listOf(accents.protein, accents.workout, accents.kcal, accents.carbs)
    val limits = listOf(14f) + bmiBoundaries.map { it.toFloat() } + listOf(36f)
    Canvas(Modifier.fillMaxWidth().height(20.dp).chartDescription("ИМТ ${bmi.value.display()}")) {
        fun x(value: Float) = (value - limits.first()) / (limits.last() - limits.first()) * size.width
        for (i in colors.indices) {
            val left = x(limits[i])
            drawRect(colors[i].copy(alpha = .7f), Offset(left, size.height * .3f),
                Size(x(limits[i + 1]) - left, size.height * .4f))
        }
        val position = x(bmi.value.toFloat().coerceIn(limits.first(), limits.last()))
            .coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
        drawLine(marker, Offset(position, 1.dp.toPx()), Offset(position, size.height - 1.dp.toPx()), 3.dp.toPx())
    }
    Row(Modifier.fillMaxWidth()) {
        for ((index, label) in listOf("<18,5", "18,5–25", "25–30", ">30").withIndex()) {
            Text(label, Modifier.weight(limits[index + 1] - limits[index]),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ActivityChart(level: ActivityLevel, color: Color) {
    val unused = MaterialTheme.colorScheme.outlineVariant
    val filled = level.ordinal + 1
    Canvas(Modifier.width(106.dp).height(16.dp).chartDescription("Активность $filled из 5, ${level.label}")) {
        repeat(5) { i -> drawCircle(if (i < filled) color else unused, 5.dp.toPx(),
            Offset(size.width * (i + .5f) / 5, center.y)) }
    }
}
