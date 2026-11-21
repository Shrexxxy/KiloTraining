package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yy")
private fun BigDecimal.signed() = if (signum() > 0) "+${display()}" else display()

@Composable
internal fun PeriodComparison(now: PeriodStats, before: PeriodStats, end: LocalDate, days: Int) {
    val start = end.minusDays(days - 1L)
    fun interval(a: LocalDate, b: LocalDate) = "${a.format(shortDate)}–${b.format(shortDate)}"
    Text("Раньше: ${interval(start.minusDays(days.toLong()), start.minusDays(1))}")
    Text("Сейчас: ${interval(start, end)}")
    val workouts = (now.workouts - before.workouts).toBigDecimal()
    Text("Записано тренировок: ${before.workouts} → ${now.workouts} (${workouts.signed()})")
    ComparisonMetric("Средний рабочий жим", "кг", "Подходов", before.benchAverage, now.benchAverage, before.benchSets, now.benchSets)
    ComparisonMetric("Средние калории", "ккал", "Дней с записями", before.averageCalories, now.averageCalories, before.foodDays, now.foodDays)
}

@Composable
private fun ComparisonMetric(title: String, unit: String, countLabel: String, before: BigDecimal?, now: BigDecimal?,
    oldCount: Int, newCount: Int) {
    val change = if (before != null && now != null) " · ${(now - before).signed()} $unit" else ""
    Text("$title: ${before?.display() ?: "—"} → ${now?.display() ?: "—"} $unit$change")
    Text("$countLabel: $oldCount → $newCount", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun MeasurementTrends(records: List<Measurement>, end: LocalDate, days: Int) {
    val window = remember(records, end, days) {
        records.asReversed().filter { it.day <= end && (days == 0 || it.day >= end.minusDays(days - 1L)) }
    }
    if (records.isEmpty()) return
    if (window.isEmpty()) {
        Text("В выбранном периоде замеров нет.")
        return
    }
    MeasurementChart("Вес", "кг", remember(window) { window.mapNotNull { m -> m.weight.amount()?.let { m.day to it } } })
    MeasurementChart("Жир", "%", remember(window) { window.mapNotNull { m -> m.fat.amount()?.let { m.day to it } } })
}

@Composable
private fun MeasurementChart(title: String, unit: String, points: List<Pair<LocalDate, BigDecimal>>) {
    if (points.isEmpty()) return
    val first = points.first()
    val last = points.last()
    if (points.size == 1) {
        Text("$title · один замер за период: ${first.second.display()} $unit", style = MaterialTheme.typography.bodySmall)
        return
    }
    val low = points.minOf { it.second }
    val high = points.maxOf { it.second }
    val spread = high - low
    val duration = (last.first.toEpochDay() - first.first.toEpochDay()).toFloat()
    val color = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    val deltaUnit = if (unit == "%") "п. п." else unit
    Text("$title: ${first.second.display()} → ${last.second.display()} $unit · ${(last.second - first.second).signed()} $deltaUnit")
    Text("Замеров: ${points.size} · Шкала: ${low.display()}–${high.display()} $unit", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = "График: $title по датам замеров" }) {
        val pad = 4.dp.toPx()
        val chartHeight = size.height - 2 * pad
        drawLine(guide, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad))
        var previous: Offset? = null
        for ((date, value) in points) {
            val fraction = if (spread.signum() == 0) 0.5f else (value - low).divide(spread, MathContext.DECIMAL32).toFloat()
            val x = (date.toEpochDay() - first.first.toEpochDay()) / duration
            val point = Offset(pad + (size.width - 2 * pad) * x, pad + chartHeight * (1 - fraction))
            previous?.let { drawLine(color, it, point, strokeWidth = 2.dp.toPx()) }
            drawCircle(color, 3.dp.toPx(), point)
            previous = point
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(first.first.format(shortDate), style = MaterialTheme.typography.labelSmall)
        Text(last.first.format(shortDate), style = MaterialTheme.typography.labelSmall)
    }
}
