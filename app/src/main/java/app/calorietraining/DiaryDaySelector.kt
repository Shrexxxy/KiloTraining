package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryDaySelector(day: LocalDate, enabled: Boolean, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            DateArrow(R.drawable.ic_chevron_left, "Предыдущий день", enabled) { onDay(day.minusDays(1)) }
            TextButton(onClick = { calendarOpen = true }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Выбрать дату, ${day.caption()}" }) {
                Text(day.caption(), style = MaterialTheme.typography.titleMedium)
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
            }
            DateArrow(R.drawable.ic_chevron_right, "Следующий день", enabled) { onDay(day.plusDays(1)) }
        }
        if (day != LocalDate.now()) {
            TextButton(onClick = { onDay(LocalDate.now()) }, enabled = enabled) { Text("Сегодня") }
        }
    }
    if (calendarOpen) {
        // Material DatePicker представляет календарную дату как полночь UTC.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = minOf(1900, day.year)..maxOf(2100, day.year)
        )
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(enabled = enabled && picker.selectedDateMillis != null, onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        calendarOpen = false
                        onDay(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { calendarOpen = false }) { Text("Отмена") } }
        ) { DatePicker(state = picker) }
    }
}
