package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    photoProgress: PhotoProgress? = null,
) {
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Настройки", onBack = onBack)
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Резервная копия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    photoProgress?.let { Text(it.label()) }
                }
            }
            Text(versionLabel(LocalContext.current), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun versionLabel(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return "Версия ${info.versionName} (${info.longVersionCode})" + if (debug) " · dev" else ""
}
