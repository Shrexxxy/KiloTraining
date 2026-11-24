package app.calorietraining

import android.content.Context
import java.io.File
import java.io.StringWriter
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ServerBackup(val id: UUID, val createdAt: Instant, val sizeBytes: Int, val format: String)

internal class HttpBackupServer(address: String, key: String) {
    private val http = ServerHttpClient(address, key, readTimeout = 30_000)

    suspend fun uploadBackup(json: String): ServerBackup =
        requireNotNull(http.request("/backups", method = "POST", body = json) { JSONObject(it).backup() })

    suspend fun listBackups(): List<ServerBackup> = requireNotNull(http.request("/backups") {
        val rows = JSONArray(it)
        List(rows.length()) { index -> rows.getJSONObject(index).backup() }
    })

    suspend fun downloadBackup(id: UUID): String = requireNotNull(http.request("/backups/$id") { it })
}

private fun JSONObject.backup(): ServerBackup = try {
    ServerBackup(UUID.fromString(getString("id")), Instant.parse(getString("createdAt")),
        getInt("sizeBytes"), getString("format"))
} catch (_: IllegalArgumentException) {
    throw ProductServerException(ServerFailure.OTHER)
} catch (_: java.time.format.DateTimeParseException) {
    throw ProductServerException(ServerFailure.OTHER)
}

internal fun backupServerMessage(error: ProductServerException) = when (error.failure) {
    ServerFailure.OFFLINE -> "Сервер не отвечает"
    ServerFailure.UNAUTHORIZED -> "Неверный ключ"
    ServerFailure.INVALID_DATA -> "Копия повреждена"
    ServerFailure.OTHER -> "Не удалось выполнить запрос к серверу"
}

internal data class UploadedDiary(val copy: ServerBackup, val unsentPhotos: Int)

internal fun savedOnServerMessage(unsentPhotos: Int) = "Копия сохранена на сервере" +
    if (unsentPhotos > 0) ". Фото отправлены не все: $unsentPhotos" else ""

internal val Context.backupPreferences get() = getSharedPreferences("server", Context.MODE_PRIVATE)
internal const val AutoBackupUnsentPhotos = "autoBackupUnsentPhotos"

internal suspend fun HttpBackupServer.uploadDiary(
    context: Context,
    photoServer: PhotoServer?,
    progress: (PhotoProgress) -> Unit = {}
) = withContext(Dispatchers.IO) {
    val json = StringWriter()
    val photos = linkedMapOf<String, File?>()
    FoodStore(context).use { store ->
        val db = store.writableDatabase
        db.atomic {
            store.exportDiary(json)
            db.diaryPhotos().forEach { photo ->
                val hash = photo.hash ?: return@forEach
                if (photos[hash] == null) photos[hash] = context.localPhoto(photo.path)
            }
        }
    }
    val copy = uploadBackup(json.toString())
    UploadedDiary(copy, photoServer?.upload(photos, progress) ?: photos.size)
}

private val autoBackupScope = CoroutineScope(Dispatchers.IO)
private val autoBackupLock = Mutex()
private const val BackupIntervalMillis = 24 * 60 * 60 * 1_000L

internal fun launchAutoBackup(context: Context) {
    autoBackupScope.launch { autoBackupDiary(context) }
}

internal suspend fun autoBackupDiary(context: Context, now: Long? = null) {
    autoBackupLock.withLock {
        val settings = ProductServerSettings(context)
        val server = settings.backupClient() ?: return
        val last = settings.lastAutoBackupMillis
        if (last > 0 && (now ?: System.currentTimeMillis()) - last <= BackupIntervalMillis) return
        try {
            val result = server.uploadDiary(context, settings.photoClient())
            context.backupPreferences.edit().putInt(AutoBackupUnsentPhotos, result.unsentPhotos).apply()
            settings.lastAutoBackupMillis = now ?: System.currentTimeMillis()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Следующая попытка будет при запуске приложения.
        }
    }
}
