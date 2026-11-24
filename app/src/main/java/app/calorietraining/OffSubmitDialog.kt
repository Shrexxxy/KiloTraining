package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun OffSubmitDialog(
    card: MyProduct,
    test: Boolean,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var verified by remember(card) { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier.padding(horizontal = 12.dp),
        onDismissRequest = onDismiss,
        title = { Text("Поделиться в Open Food Facts") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Данные станут общедоступными по лицензии ODbL")
                if (test) Text("Тестовая площадка, настоящая карточка не изменится",
                    color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleSmall)
                    Text("Штрихкод: ${card.barcode}")
                    Text("Марка: ${card.brand.ifBlank { "не указана" }}")
                    Text("На 100 г", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Калорийность: ${card.energy} ккал")
                    Text("Белки: ${card.macros.protein} г")
                    Text("Жиры: ${card.macros.fat} г")
                    Text("Углеводы: ${card.macros.carbs} г")
                }
                if (card.hasOffCalorieWarning()) {
                    Text("Калорийность расходится с расчётом по БЖУ", color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(verified, enabled = !sending, role = Role.Checkbox) { verified = it },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(checked = verified, onCheckedChange = null, enabled = !sending)
                    Text("Я сверил значения с упаковкой")
                }
                if (sending) Text("Отправка…")
            }
        },
        confirmButton = { TextButton(onClick = onSubmit, enabled = verified && !sending) { Text("Отправить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Назад") } }
    )
}
