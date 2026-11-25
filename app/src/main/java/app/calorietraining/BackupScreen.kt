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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val photoRevision: Int = 0,
    val autoPhotoWarning: String? = null,
    val serverConfigured: Boolean = false,
    val copies: List<ServerBackup> = emptyList(),
    val lastCopy: Instant? = null,
    val photoProgress: PhotoProgress? = null,
    val missingPhotos: Int = 0
)

class BackupViewModel(private val app: Application) : AndroidViewModel(app) {
    private val settings = ProductServerSettings(app)
    private val state = MutableStateFlow(BackupState())
    internal val uiState = state.asStateFlow()
    private var staged: File? = null
    private var photoJob: Job? = null

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
        runOperation("Не удалось получить список копий") {
            val missing = FoodStore(app).use { it.missingPhotos().size }
            val warning = app.backupPreferences.getInt(AutoBackupUnsentPhotos, 0).takeIf { it > 0 && server != null }
            state.update { it.copy(missingPhotos = missing, autoPhotoWarning = warning?.let(::savedOnServerMessage)) }
            if (warning != null) app.backupPreferences.edit().remove(AutoBackupUnsentPhotos).apply()
            val copies = server?.listBackups().orEmpty()
            state.value.copy(busy = false, copies = copies,
                lastCopy = listOfNotNull(state.value.lastCopy, copies.firstOrNull()?.createdAt).maxOrNull())
        }
    }

    internal fun uploadServerCopy() {
        if (photoJob?.isActive == true) return
        val server = settings.backupClient() ?: return
        runOperation("Не удалось сохранить копию") {
            val result = server.uploadDiary(app, settings.photoClient(), ::updatePhotoProgress)
            val copy = result.copy
            state.value.copy(busy = false, message = savedOnServerMessage(result.unsentPhotos), autoPhotoWarning = null,
                photoProgress = null, lastCopy = copy.createdAt,
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
        runOperation("Не удалось восстановить дневник. Попробуйте ещё раз.", onSuccess = ::downloadPhotos) {
            photoJob?.cancel()
            photoJob?.join()
            val missing = FoodStore(app, file.absolutePath).use { source ->
                FoodStore(app).use {
                    it.replaceFrom(source)
                    it.missingPhotos().size
                }
            }
            SQLiteDatabase.deleteDatabase(file)
            staged = null
            state.value.copy(busy = false, preview = null, message = "Дневник восстановлен",
                revision = state.value.revision + 1, missingPhotos = missing, photoProgress = null)
        }
    }

    internal fun downloadPhotos() {
        val server = settings.photoClient() ?: return
        if (photoJob?.isActive == true || state.value.missingPhotos == 0) return
        updatePhotoProgress(PhotoProgress(0, state.value.missingPhotos))
        photoJob = viewModelScope.launch {
            try {
                val (changed, missing) = withContext(Dispatchers.IO) {
                    FoodStore(app).use { store ->
                        server.restore(store, ::updatePhotoProgress) to store.missingPhotos().size
                    }
                }
                state.update { it.copy(missingPhotos = missing, photoRevision = it.photoRevision + if (changed) 1 else 0) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Дневник уже сохранён, оставшиеся фото можно докачать вручную.
            } finally {
                state.update { it.copy(photoProgress = null) }
            }
        }
    }

    private fun updatePhotoProgress(progress: PhotoProgress) {
        state.update { it.copy(photoProgress = progress) }
    }

    private fun runOperation(failure: String, onSuccess: () -> Unit = {}, action: suspend () -> BackupState) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                state.value = withContext(Dispatchers.IO) { action() }
                onSuccess()
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
                Text("Фото хранятся на сервере отдельно от файла копии", style = MaterialTheme.typography.bodyMedium)
                state.photoProgress?.let { Text(it.label()) }
                if (state.missingPhotos > 0 && state.photoProgress == null) {
                    OutlinedButton(enabled = state.serverConfigured && !state.busy, onClick = model::downloadPhotos) {
                        Text("Докачать фото")
                    }
                }
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
            state.autoPhotoWarning?.let { Text(it) }
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
            Button(enabled = !state.busy && state.photoProgress == null, onClick = onUpload) { Text("Сохранить на сервер") }
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

internal fun PhotoProgress.label() = "Фото: $done из $total"

private val backupDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.forLanguageTag("ru"))
private fun Instant.backupDate() = backupDateFormat.withZone(ZoneId.systemDefault()).format(this)

private fun Int.backupSize(): String {
    val locale = Locale.forLanguageTag("ru")
    return if (this >= 1024 * 1024) String.format(locale, "%.1f МБ", this / 1048576.0)
    else String.format(locale, "%.1f КБ", this / 1024.0)
}
