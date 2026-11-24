package app.calorietraining

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private val photoHashPattern = Regex("[0-9a-f]{64}")
internal fun validPhotoHash(hash: String) = photoHashPattern.matches(hash)

internal fun SQLiteDatabase.addPhotoHashes() {
    execSQL("ALTER TABLE food ADD COLUMN photo_hash TEXT")
    execSQL("ALTER TABLE venue ADD COLUMN logo_hash TEXT")
}

internal val Context.photosDirectory get() = File(filesDir, "photos")

internal fun Context.localPhoto(path: String?): File? {
    if (path == null) return null
    return try {
        File(path).canonicalFile.takeIf { it.parentFile == photosDirectory.canonicalFile && it.isFile }
    } catch (_: IOException) {
        null
    }
}

internal fun File.photoHash(): String? = try {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
} catch (_: IOException) {
    null
}

internal fun Context.photoHash(path: String?) = localPhoto(path)?.photoHash()

internal enum class PhotoColumn(val table: String, val path: String, val hash: String) {
    FOOD("food", "photo_path", "photo_hash"),
    VENUE("venue", "logo_path", "logo_hash")
}

internal data class DiaryPhoto(val column: PhotoColumn, val id: Long, val path: String?, val hash: String?) {
    fun setPath(db: SQLiteDatabase, value: String): Int = db.update(column.table,
        ContentValues().apply { put(column.path, value) },
        "id = ? AND ${column.hash} = ? AND ${column.path} IS ?",
        arrayOf(id.toString(), requireNotNull(hash), path))
}

internal fun SQLiteDatabase.diaryPhotos(): List<DiaryPhoto> = PhotoColumn.entries.flatMap { column ->
    rawQuery("SELECT id, ${column.path}, ${column.hash} FROM ${column.table} " +
        "WHERE ${column.path} IS NOT NULL OR ${column.hash} IS NOT NULL ORDER BY id", null).use { rows ->
        buildList {
            while (rows.moveToNext()) add(DiaryPhoto(column, rows.getLong(0), rows.getString(1), rows.getString(2)))
        }
    }
}

internal fun FoodStore.refreshPhotoHashes() {
    val db = writableDatabase
    val hashes = mutableMapOf<String, String?>()
    db.diaryPhotos().forEach { photo ->
        val path = photo.path ?: return@forEach
        val hash = hashes.getOrPut(path) { context.photoHash(path) } ?: return@forEach
        if (hash != photo.hash) db.update(photo.column.table, ContentValues().apply { put(photo.column.hash, hash) },
            "id = ? AND ${photo.column.path} = ?", arrayOf(photo.id.toString(), path))
    }
}

internal fun FoodStore.reuseLocalPhotos(): Boolean {
    val db = writableDatabase
    val missing = db.diaryPhotos().filter { it.hash != null && context.localPhoto(it.path) == null }.groupBy { it.hash }
    if (missing.isEmpty()) return false
    var changed = false
    val remaining = missing.toMutableMap()
    context.photosDirectory.listFiles().orEmpty().forEach { candidate ->
        val file = context.localPhoto(candidate.path) ?: return@forEach
        val hash = file.photoHash() ?: return@forEach
        remaining.remove(hash)?.forEach {
            if (it.setPath(db, file.path) > 0) changed = true
        }
    }
    return changed
}
