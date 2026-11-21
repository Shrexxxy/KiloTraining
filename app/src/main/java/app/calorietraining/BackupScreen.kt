package app.calorietraining

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import java.time.LocalDate

data class BackupState(val busy: Boolean = false, val preview: String? = null,
    val message: String? = null, val revision: Int = 0)

class BackupViewModel(private val app: Application) : AndroidViewModel(app) {
    private val state = MutableStateFlow(BackupState())
    val uiState = state.asStateFlow()
    private var staged: File? = null

    fun export(uri: Uri) = runOperation("Не удалось записать копию. Выберите другой файл.") {
        FoodStore(app).use { store ->
            val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException()
            stream.bufferedWriter(Charsets.UTF_8).use(store::exportDiary)
        }
        state.value.copy(busy = false, message = "Копия сохранена")
    }

    fun prepare(uri: Uri) = runOperation("Файл не удалось прочитать как копию дневника.") {
        val file = File.createTempFile("diary-restore-", ".db", app.cacheDir)
        try {
            val preview = FoodStore(app, file.absolutePath).use { store ->
                val stream = app.contentResolver.openInputStream(uri) ?: throw IOException()
                stream.bufferedReader(Charsets.UTF_8).use(store::readBackup)
            }
            staged = file
            state.value.copy(busy = false, preview = preview)
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
            BackupState(message = "Дневник восстановлен", revision = state.value.revision + 1)
        }
    }

    private fun runOperation(failure: String, action: () -> BackupState) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                state.value = withContext(Dispatchers.IO) { action() }
            } catch (e: CancellationException) { throw e }
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
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it != null) model.export(it)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) model.prepare(it)
    }
    fun close() {
        if (!state.busy) { model.discard(); onClose() }
    }
    BackHandler { close() }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Резервная копия", style = MaterialTheme.typography.headlineMedium)
            val preview = state.preview
            if (preview == null) {
                Text("Сохраните питание, продукты и тренировки в файл. Его можно перенести на другой телефон.")
                Button(enabled = !state.busy, onClick = { create.launch("diary-${LocalDate.now()}.json") }) { Text("Сохранить копию") }
                OutlinedButton(enabled = !state.busy, onClick = { open.launch(arrayOf("*/*")) }) { Text("Восстановить из файла") }
            } else {
                Text(preview, style = MaterialTheme.typography.titleMedium)
                Text("Все текущие записи будут заменены содержимым этой копии.")
                Button(enabled = !state.busy, onClick = model::restore) { Text("Заменить дневник") }
                OutlinedButton(enabled = !state.busy, onClick = model::discard) { Text("Отменить восстановление") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it) }
            TextButton(enabled = !state.busy, onClick = { close() }) { Text("Назад") }
        }
    }
}
