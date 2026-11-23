package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun MealGroupHeader(source: LocalDate, meal: Meal, entries: List<FoodEntry>, enabled: Boolean,
    modifier: Modifier = Modifier, onCopy: (LocalDate) -> Unit) {
    var targetDay by rememberSaveable(source.toEpochDay(), meal.name) { mutableStateOf<Long?>(null) }
    val total = entries.sumOf { it.calories }.display()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(meal.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (entries.isNotEmpty()) TextButton(enabled = enabled, onClick = {
            val today = LocalDate.now()
            targetDay = (if (source == today) today.plusDays(1) else today).toEpochDay()
        }) { Text("Повторить", style = MaterialTheme.typography.labelMedium) }
        Text("$total ккал", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    targetDay?.let { epoch ->
        val destination = LocalDate.ofEpochDay(epoch)
        AlertDialog(
            onDismissRequest = { targetDay = null },
            title = { Text("Повторить приём пищи") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${meal.label} · $total ккал", style = MaterialTheme.typography.titleMedium)
                    Text("Записей для добавления: ${entries.size}")
                    Text("Добавить на дату", style = MaterialTheme.typography.labelLarge)
                    DiaryDaySelector(destination, enabled, { targetDay = it.toEpochDay() })
                    if (destination == source) Text("Записи будут добавлены в этот же день повторно.")
                }
            },
            confirmButton = { Button(enabled = enabled, onClick = {
                targetDay = null
                onCopy(destination)
            }) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { targetDay = null }) { Text("Отмена") } }
        )
    }
}
