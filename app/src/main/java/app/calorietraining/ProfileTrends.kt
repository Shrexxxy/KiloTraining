package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

private fun BigDecimal.signed(): String = when (signum()) {
    1 -> "+${groupedNumber()}"
    -1 -> "−${abs().groupedNumber()}"
    else -> groupedNumber()
}

@Composable
internal fun PeriodComparison(now: PeriodStats, before: PeriodStats, days: Int) {
    if (now.workouts + before.workouts + now.foodDays + before.foodDays == 0) {
        Text("Добавь записи для сравнения периодов.")
        return
    }
    ChartLabel("Было · прошлые $days дней / стало · последние $days дней")
    val accents = LocalDiaryAccents.current
    ComparisonMetric("Тренировки", "", before.workouts.toBigDecimal(), now.workouts.toBigDecimal(), accents.workout)
    ComparisonMetric("Средний рабочий жим", " кг", before.benchAverage, now.benchAverage, accents.workout,
        "подходов: ${before.benchSets} / ${now.benchSets}", scale = 1)
    ComparisonMetric("Средние калории", " ккал", before.averageCalories, now.averageCalories, accents.kcal,
        "дней с записями: ${before.foodDays} / ${now.foodDays}")
}

@Composable
private fun ComparisonMetric(
    title: String, unit: String, before: BigDecimal?, now: BigDecimal?, color: Color, sample: String? = null,
    scale: Int = 0,
) {
    if (before == null && now == null) return
    val parts = ComparisonParts(before, now, scale)
    val described = "$title, было ${parts.before}${unit.takeIf { before != null }.orEmpty()}, " +
        "стало ${parts.now}${unit.takeIf { now != null }.orEmpty()}, разница ${parts.delta?.let { it + unit } ?: "—"}"
    Text(title, style = MaterialTheme.typography.titleSmall)
    ChartBars(listOf(before?.toFloat(), now?.toFloat()), color,
        listOfNotNull(described, sample).joinToString(", "), height = 56)
    ChartLabel(comparisonLabel(before, now, unit, scale))
}

/** Округлённые «было», «стало» и разница со знаком. */
private class ComparisonParts(before: BigDecimal?, now: BigDecimal?, private val scale: Int) {
    private fun BigDecimal.rounded() = setScale(scale, RoundingMode.HALF_UP)
    val before = before?.rounded()?.groupedNumber() ?: "—"
    val now = now?.rounded()?.groupedNumber() ?: "—"
    val delta = if (before == null || now == null) null else (now.rounded() - before.rounded()).let { difference ->
        val sign = when (difference.signum()) { 1 -> "+"; -1 -> "−"; else -> "" }
        sign + difference.abs().groupedNumber()
    }
}

/** Подпись «было …, стало … (разница)» с округлением до [scale] знаков; стрелок в интерфейсе нет по решению пользователя. */
internal fun comparisonLabel(before: BigDecimal?, now: BigDecimal?, unit: String, scale: Int): String {
    val parts = ComparisonParts(before, now, scale)
    fun withUnit(value: String) = if (value == "—") value else value + unit
    return "было ${withUnit(parts.before)}, стало ${withUnit(parts.now)}" + (parts.delta?.let { " ($it$unit)" } ?: "")
}

@Composable
internal fun MeasurementTrends(records: List<Measurement>, end: LocalDate, days: Int) {
    val window = remember(records, end, days) {
        records.asReversed().filter { it.day <= end && (days == 0 || it.day >= end.minusDays(days - 1L)) }
    }
    if (records.isEmpty()) return
    if (window.isEmpty()) {
        Text("Добавь замер за выбранный период.")
        return
    }
    TrendChart("Вес", "кг", LocalDiaryAccents.current.workout,
        remember(window) { window.mapNotNull { m -> m.weight.amount()?.let { m.day to it } } })
    TrendChart("Жир", "%", LocalDiaryAccents.current.fat,
        remember(window) { window.mapNotNull { m -> m.fat.amount()?.let { m.day to it } } })
}

@Composable
internal fun TrendChart(title: String, unit: String, color: Color, points: List<Pair<LocalDate, BigDecimal>>) {
    if (points.isEmpty()) return
    val first = points.first()
    val last = points.last()
    val low = points.minOf { it.second }
    val high = points.maxOf { it.second }
    val spread = high - low
    val duration = last.first.toEpochDay() - first.first.toEpochDay()
    val guide = MaterialTheme.colorScheme.outlineVariant
    val latest = "${last.second.display()} $unit"
    val deltaUnit = if (unit == "%") "п. п." else unit
    val change = if (points.size > 1) " · ${(last.second - first.second).signed()} $deltaUnit" else ""
    val summary = "$title, последнее $latest$change, точек ${points.size}, минимум ${low.display()}, максимум ${high.display()}, " +
        "${first.first.format(profileChartDate)}–${last.first.format(profileChartDate)}"
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text("$latest$change", style = MaterialTheme.typography.titleLarge, color = color)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ChartLabel(high.display())
            if (low != high) ChartLabel(low.display())
        }
        Canvas(Modifier.weight(1f).height(140.dp).chartDescription(summary)) {
            val pad = 5.dp.toPx()
            val top = pad
            val bottom = size.height - pad
            for (y in listOf(top, bottom)) drawLine(guide, Offset(pad, y), Offset(size.width - pad, y))
            var previous: Offset? = null
            points.forEachIndexed { index, (date, value) ->
                val fraction = if (spread.signum() == 0) .5f else (value - low).divide(spread, MathContext.DECIMAL32).toFloat()
                val x = when {
                    points.size == 1 -> .5f
                    duration == 0L -> index.toFloat() / (points.size - 1)
                    else -> (date.toEpochDay() - first.first.toEpochDay()).toFloat() / duration
                }
                val point = Offset(pad + (size.width - pad * 2) * x, bottom - (bottom - top) * fraction)
                previous?.let { drawLine(color, it, point, 2.dp.toPx()) }
                drawCircle(color, 3.dp.toPx(), point)
                previous = point
            }
        }
    }
    ChartDates(first.first, last.first)
}
