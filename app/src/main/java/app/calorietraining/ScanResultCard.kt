package app.calorietraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ScanResultCard(code: String, outcome: ScanOutcome, busy: Boolean, onEvent: (FoodEvent) -> Unit) {
    val message = when (outcome) {
        ScanOutcome.PHONE -> "Сохранено на телефоне"
        ScanOutcome.SERVER -> "Найдено в вашей базе"
        ScanOutcome.CATALOG -> "Найдено в Open Food Facts"
        ScanOutcome.MISSING -> "Этого товара нет в Open Food Facts. Заполните название и калорийность с упаковки — после сохранения товар запомнится, и в следующий раз всё подставится само"
        ScanOutcome.NO_ENERGY -> "В каталоге есть товар, но без калорийности. Допишите её с упаковки"
        ScanOutcome.UNAVAILABLE -> "Не удалось связаться с каталогом. Заполните с упаковки или попробуйте позже"
    }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Штрихкод $code", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            when (outcome) {
                ScanOutcome.MISSING, ScanOutcome.NO_ENERGY ->
                    TextButton(onClick = { onEvent(FoodEvent.BrowseFoods) }, enabled = !busy) {
                        Text("Найти по названию")
                    }
                ScanOutcome.UNAVAILABLE ->
                    TextButton(onClick = { onEvent(FoodEvent.Barcode(code)) }, enabled = !busy) {
                        Text("Повторить поиск")
                    }
                ScanOutcome.PHONE, ScanOutcome.SERVER, ScanOutcome.CATALOG -> Unit
            }
        }
    }
}
