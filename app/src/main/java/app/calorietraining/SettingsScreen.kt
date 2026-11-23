package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun SettingsScreen(
    model: ProfileViewModel,
    mode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBackup: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Настройки", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onBack) { Text("Назад") }
            }
            ProfileBlock("Оформление") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { onThemeChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) {
                            Text(when (option) {
                                ThemeMode.SYSTEM -> "Авто"
                                ThemeMode.LIGHT -> "Светлая"
                                ThemeMode.DARK -> "Тёмная"
                            }, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            ProductServerBlock(model)
            GlassCard(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth(), onClick = onBackup) {
                Text("Резервная копия", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProductServerBlock(model: ProfileViewModel) {
    val state by model.productServerState.collectAsStateWithLifecycle()
    val configured = state.address.isNotBlank()
    ProfileBlock("Сервер продуктов") {
        DiaryTextField(state.address, model::changeServerAddress, Modifier.fillMaxWidth(),
            enabled = !state.busy, label = { Text("Адрес") },
            placeholder = { Text("http://192.168.1.10:5080") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        DiaryTextField(state.key, model::changeServerKey, Modifier.fillMaxWidth(),
            enabled = !state.busy, label = { Text("Ключ") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TextButton(onClick = model::checkProductServer, enabled = configured && !state.busy) {
            Text("Проверить связь")
        }
        Text(if (configured) state.status.orEmpty().ifBlank { "Связь не проверена" } else "Не настроен")
        state.pending?.let { Text("Ждут отправки: $it") }
        val lastSent = state.lastSent?.let {
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(it)
        } ?: "—"
        Text("Последняя отправка: $lastSent")
        if (state.unsuitable > 0) Text("Не подходят для отправки: ${state.unsuitable}")
        TextButton(onClick = model::sendProducts, enabled = configured && !state.busy) {
            Text("Отправить сейчас")
        }
    }
}
