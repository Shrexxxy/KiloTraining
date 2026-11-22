package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
private fun BigDecimal.signed(): String = when (signum()) {
    1 -> "+${groupedNumber()}"
    -1 -> "−${abs().groupedNumber()}"
    else -> groupedNumber()
}

@Composable
internal fun PeriodComparison(now: PeriodStats, before: PeriodStats, end: LocalDate, days: Int) {
    val start = end.minusDays(days - 1L)
    fun interval(a: LocalDate, b: LocalDate) = "${a.format(shortDate)}–${b.format(shortDate)}"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ComparisonHeading("Прошлые $days дней", interval(start.minusDays(days.toLong()), start.minusDays(1)),
            Modifier.weight(1f))
        ComparisonHeading("Последние $days дней", interval(start, end), Modifier.weight(1f))
        Spacer(Modifier.width(72.dp))
    }
    ComparisonMetric("Тренировки", "", before.workouts.toBigDecimal(), now.workouts.toBigDecimal())
    ComparisonMetric("Средний рабочий жим", " кг", before.benchAverage, now.benchAverage,
        "подходов: ${before.benchSets}", "подходов: ${now.benchSets}")
    ComparisonMetric("Средние калории", " ккал", before.averageCalories, now.averageCalories,
        "дней с записями: ${before.foodDays}", "дней с записями: ${now.foodDays}")
}

@Composable
private fun ComparisonHeading(title: String, dates: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        ComparisonCaption(dates)
    }
}

@Composable
private fun ComparisonCaption(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ComparisonMetric(title: String, unit: String, before: BigDecimal?, now: BigDecimal?,
    oldSample: String? = null, newSample: String? = null) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(before?.let { it.groupedNumber() + unit } ?: "—", fontWeight = FontWeight.Bold)
                oldSample?.let { ComparisonCaption(it) }
            }
            Column(Modifier.weight(1f)) {
                Text(now?.let { it.groupedNumber() + unit } ?: "—", fontWeight = FontWeight.Bold)
                newSample?.let { ComparisonCaption(it) }
            }
            Box(Modifier.width(72.dp), contentAlignment = Alignment.TopEnd) {
                val change = if (before != null && now != null) (now - before).signed() + unit else "—"
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(change, Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
    MeasurementChart("Вес", "кг", LocalDiaryAccents.current.workout, remember(window) { window.mapNotNull { m -> m.weight.amount()?.let { m.day to it } } })
    MeasurementChart("Жир", "%", LocalDiaryAccents.current.fat, remember(window) { window.mapNotNull { m -> m.fat.amount()?.let { m.day to it } } })
}

@Composable
private fun MeasurementChart(title: String, unit: String, color: Color, points: List<Pair<LocalDate, BigDecimal>>) {
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
    val guide = MaterialTheme.colorScheme.outlineVariant
    val deltaUnit = if (unit == "%") "п. п." else unit
    Text("$title: с ${first.second.display()} до ${last.second.display()} $unit · ${(last.second - first.second).signed()} $deltaUnit")
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
