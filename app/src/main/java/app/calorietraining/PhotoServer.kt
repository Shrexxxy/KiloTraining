package app.calorietraining

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

private const val MaxPhotoBytes = 2 * 1024 * 1024
private const val MissingBatchSize = 500

internal data class PhotoProgress(val done: Int, val total: Int)

internal class PhotoServer(address: String, key: String) {
    private val http = ServerHttpClient(address, key, readTimeout = 30_000)

    private suspend fun missing(hashes: List<String>): List<String> {
        val request = JSONObject().put("hashes", JSONArray(hashes)).toString()
        return requireNotNull(http.request("/photos/missing", method = "POST", body = request) {
            val items = JSONArray(it)
            val absent = List(items.length(), items::getString)
            if (!hashes.containsAll(absent) || absent.toSet().size != absent.size)
                throw ProductServerException(ServerFailure.INVALID_DATA)
            absent
        })
    }

    suspend fun upload(photos: Map<String, File?>, progress: (PhotoProgress) -> Unit): Int {
        if (photos.isEmpty()) return 0
        var done = 0
        progress(PhotoProgress(done, photos.size))
        try {
            val absent = mutableListOf<String>()
            for (batch in photos.keys.chunked(MissingBatchSize)) {
                val missing = missing(batch)
                absent.addAll(missing)
                done += batch.size - missing.size
                progress(PhotoProgress(done, photos.size))
            }
            for (hash in absent) {
                currentCoroutineContext().ensureActive()
                val file = photos[hash] ?: continue
                val length = file.length()
                if (length !in 3..MaxPhotoBytes.toLong() || file.photoHash() != hash) continue
                http.putBinary("/photos/$hash", length, "image/jpeg") { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
                progress(PhotoProgress(++done, photos.size))
            }
        } catch (_: IOException) {
            return photos.size - done
        }
        return photos.size - done
    }

    private suspend fun download(hash: String, file: File): Boolean {
        val received = http.getBinary("/photos/$hash") { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer, 0, minOf(buffer.size, MaxPhotoBytes - total + 1))
                    if (count < 0) break
                    total += count
                    if (total > MaxPhotoBytes) return@use false
                    output.write(buffer, 0, count)
                }
                true
            }
        }
        return received == true && file.photoHash() == hash
    }

    suspend fun restore(store: FoodStore, progress: (PhotoProgress) -> Unit): Boolean {
        val db = store.writableDatabase
        var changed = false
        db.atomic { changed = store.reuseLocalPhotos() }
        val missing = store.missingPhotos()
        var done = 0
        if (missing.isNotEmpty()) progress(PhotoProgress(done, missing.size))
        val directory = store.context.photosDirectory
        for ((hash, rows) in missing) {
            currentCoroutineContext().ensureActive()
            var file: File? = null
            var attached = false
            try {
                directory.mkdirs()
                val target = File.createTempFile("restore-", ".part", store.context.cacheDir)
                file = target
                if (!download(hash, target)) continue
                currentCoroutineContext().ensureActive()
                val photo = File.createTempFile("restore-", ".jpg", directory)
                try {
                    Files.move(target.toPath(), photo.toPath(), REPLACE_EXISTING)
                } catch (error: IOException) {
                    photo.delete()
                    throw error
                }
                file = photo
                var updated = 0
                db.atomic { rows.forEach { updated += it.setPath(db, photo.absolutePath) } }
                attached = updated > 0
                if (attached) changed = true
                progress(PhotoProgress(++done, missing.size))
            } catch (error: ProductServerException) {
                if (error.failure == ServerFailure.OFFLINE || error.failure == ServerFailure.UNAUTHORIZED) break
            } catch (_: IOException) {
                continue
            } finally {
                if (!attached) file?.delete()
            }
        }
        return changed
    }
}

internal fun FoodStore.missingPhotos(): Map<String, List<DiaryPhoto>> =
    readableDatabase.diaryPhotos().filter { it.hash != null && context.localPhoto(it.path) == null }
        .groupBy { requireNotNull(it.hash) }

internal fun ProductServerSettings.photoClient() = address.trim().takeIf { it.isNotBlank() && key.isNotBlank() }
    ?.let { PhotoServer(it, key.trim()) }
