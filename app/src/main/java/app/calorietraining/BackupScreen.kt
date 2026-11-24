package app.calorietraining

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal data class BackupState(
    val busy: Boolean = false,
    val preview: String? = null,
    val message: String? = null,
    val revision: Int = 0,
    val serverConfigured: Boolean = false,
    val copies: List<ServerBackup> = emptyList(),
    val lastCopy: Instant? = null
)

class BackupViewModel(private val app: Application) : AndroidViewModel(app) {
    private val settings = ProductServerSettings(app)
    private val state = MutableStateFlow(BackupState())
    internal val uiState = state.asStateFlow()
    private var staged: File? = null

    fun export(uri: Uri) = runOperation("Не удалось записать копию. Выберите другой файл.") {
        FoodStore(app).use { store ->
            val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException()
            stream.bufferedWriter(Charsets.UTF_8).use(store::exportDiary)
        }
        state.value.copy(busy = false, message = "Копия сохранена")
    }

    fun prepare(uri: Uri) = runOperation("Файл не удалось прочитать как копию дневника.") {
        stage {
            val stream = app.contentResolver.openInputStream(uri) ?: throw IOException()
            stream.bufferedReader(Charsets.UTF_8)
        }
    }

    internal fun loadServerCopies() {
        if (state.value.busy) return
        val server = settings.backupClient()
        state.value = state.value.copy(serverConfigured = server != null, copies = emptyList(),
            lastCopy = settings.lastAutoBackupMillis.takeIf { server != null && it > 0 }?.let(Instant::ofEpochMilli))
        if (server == null) return
        runOperation("Не удалось получить список копий") {
            val copies = server.listBackups()
            state.value.copy(busy = false, copies = copies,
                lastCopy = listOfNotNull(state.value.lastCopy, copies.firstOrNull()?.createdAt).maxOrNull())
        }
    }

    internal fun uploadServerCopy() {
        val server = settings.backupClient() ?: return
        runOperation("Не удалось сохранить копию") {
            val copy = server.uploadDiary(app)
            state.value.copy(busy = false, message = "Копия сохранена на сервере", lastCopy = copy.createdAt,
                copies = (listOf(copy) + state.value.copies.filterNot { it.id == copy.id }).take(30))
        }
    }

    internal fun prepareServerCopy(id: UUID) {
        val server = settings.backupClient() ?: return
        runOperation("Копия повреждена") {
            val json = server.downloadBackup(id)
            stage { json.removePrefix("\uFEFF").reader() }
        }
    }

    private fun stage(open: () -> Reader): BackupState {
        val file = File.createTempFile("diary-restore-", ".db", app.cacheDir)
        try {
            val preview = FoodStore(app, file.absolutePath).use { store ->
                open().use(store::readBackup)
            }
            staged = file
            return state.value.copy(busy = false, preview = preview)
        } finally {
            if (staged != file) SQLiteDatabase.deleteDatabase(file)
        }
    }

    fun discard() {
        if (state.value.busy) return
        staged?.let(SQLiteDatabase::deleteDatabase)
        staged = null
        state.value = state.value.copy(preview = null, message = null)
    }

    fun restore() {
        val file = staged ?: return
        runOperation("Не удалось восстановить дневник. Попробуйте ещё раз.") {
            FoodStore(app, file.absolutePath).use { source ->
                FoodStore(app).use { it.replaceFrom(source) }
            }
            SQLiteDatabase.deleteDatabase(file)
            staged = null
            state.value.copy(busy = false, preview = null, message = "Дневник восстановлен",
                revision = state.value.revision + 1)
        }
    }

    private fun runOperation(failure: String, action: suspend () -> BackupState) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                state.value = withContext(Dispatchers.IO) { action() }
            } catch (e: CancellationException) { throw e }
            catch (e: ProductServerException) {
                state.value = state.value.copy(busy = false, message = backupServerMessage(e))
            }
            catch (e: Exception) { state.value = state.value.copy(busy = false, message = failure) }
        }
    }

    override fun onCleared() {
        staged?.let(SQLiteDatabase::deleteDatabase)
        super.onCleared()
    }
}

@Composable
fun BackupScreen(model: BackupViewModel, onClose: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadServerCopies() }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it != null) model.export(it)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) model.prepare(it)
    }
    fun close() {
        if (!state.busy) { model.discard(); onClose() }
    }
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenHeader("Резервная копия", onBack = ::close, backEnabled = !state.busy)
            val preview = state.preview
            if (preview == null) {
                Text("Сохраните питание, продукты и тренировки в файл. Его можно перенести на другой телефон.")
                Text("Фото блюд и логотипы в копию не входят", style = MaterialTheme.typography.bodyMedium)
                Button(enabled = !state.busy, onClick = { create.launch("diary-${LocalDate.now()}.json") }) { Text("Сохранить копию") }
                OutlinedButton(enabled = !state.busy, onClick = { open.launch(arrayOf("*/*")) }) { Text("Восстановить из файла") }
                if (state.serverConfigured) {
                    ServerBackupCard(state, model::uploadServerCopy, model::prepareServerCopy)
                }
            } else {
                Text(preview, style = MaterialTheme.typography.titleMedium)
                Text("Все текущие записи будут заменены содержимым этой копии.")
                Button(enabled = !state.busy, onClick = model::restore) { Text("Заменить") }
                OutlinedButton(enabled = !state.busy, onClick = model::discard) { Text("Отмена") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it) }
        }
    }
}

@Composable
private fun ServerBackupCard(state: BackupState, onUpload: () -> Unit, onDownload: (UUID) -> Unit) {
    GlassCard(MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("На сервере", style = MaterialTheme.typography.titleLarge)
            Text("Последняя копия: ${state.lastCopy?.backupDate() ?: "—"}",
                style = MaterialTheme.typography.bodyMedium)
            Button(enabled = !state.busy, onClick = onUpload) { Text("Сохранить на сервер") }
            state.copies.forEach { copy ->
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(copy.createdAt.backupDate()) },
                    supportingContent = { Text(copy.sizeBytes.backupSize()) },
                    modifier = Modifier.clickable(enabled = !state.busy) { onDownload(copy.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

private val backupDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.forLanguageTag("ru"))
private fun Instant.backupDate() = backupDateFormat.withZone(ZoneId.systemDefault()).format(this)

private fun Int.backupSize(): String {
    val locale = Locale.forLanguageTag("ru")
    return if (this >= 1024 * 1024) String.format(locale, "%.1f МБ", this / 1048576.0)
    else String.format(locale, "%.1f КБ", this / 1024.0)
}
