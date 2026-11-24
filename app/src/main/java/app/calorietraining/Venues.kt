package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Заведение из дневника и число разных блюд, которые в нём записали. */
data class Venue(val id: Long, val name: String, val logoPath: String?, val dishCount: Int)

internal fun venueKey(name: String): String = name.trim().lowercase(Locale.ROOT).replace('ё', 'е')

internal fun SQLiteDatabase.createVenues() {
    execSQL("""CREATE TABLE venue (
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, logo_path TEXT, created_at INTEGER NOT NULL
    )""")
    execSQL("ALTER TABLE food ADD COLUMN venue_id INTEGER REFERENCES venue(id)")
    execSQL("ALTER TABLE food ADD COLUMN photo_path TEXT")
    execSQL("CREATE INDEX food_venue ON food(venue_id, day, id)")
}

internal fun SQLiteDatabase.findOrCreateVenue(name: String, now: Long): Long? {
    val key = venueKey(name)
    if (key.isEmpty()) return null
    rawQuery("SELECT id, name FROM venue ORDER BY id", null).use { rows ->
        while (rows.moveToNext()) if (venueKey(rows.getString(1)) == key) return rows.getLong(0)
    }
    return insertOrThrow("venue", null, ContentValues().apply {
        put("name", name.trim())
        put("created_at", now)
    })
}

internal fun FoodStore.venues(): List<Venue> = readableDatabase.rawQuery(
    """SELECT v.id, v.name, v.logo_path, f.name FROM venue v
       LEFT JOIN food f ON f.venue_id = v.id
       ORDER BY f.day DESC, f.id DESC, v.created_at DESC, v.id DESC""", null
).use { rows ->
    val venues = linkedMapOf<Long, Venue>()
    val dishes = mutableMapOf<Long, MutableSet<String>>()
    while (rows.moveToNext()) {
        val id = rows.getLong(0)
        venues.putIfAbsent(id, Venue(id, rows.getString(1), rows.getString(2), 0))
        rows.getString(3)?.let { dishes.getOrPut(id, ::mutableSetOf).add(venueKey(it)) }
    }
    venues.values.map { it.copy(dishCount = dishes[it.id]?.size ?: 0) }
}

internal fun FoodStore.venueDishes(venue: Venue): List<FoodDraft> = readableDatabase.rawQuery(
    """SELECT name, energy, grams, protein, fat, carbs, photo_path FROM food
       WHERE venue_id = ? ORDER BY day DESC, id DESC""", arrayOf(venue.id.toString())
).use { rows ->
    val seen = mutableSetOf<String>()
    buildList {
        while (rows.moveToNext()) {
            val name = rows.getString(0)
            val grams = rows.getString(2)
            if (seen.add(venueKey(name))) add(FoodDraft(name = name, energy = rows.getString(1),
                weighted = grams.isNotEmpty(), grams = grams, venueName = venue.name, photoPath = rows.getString(6),
                macros = Macros(rows.getString(3), rows.getString(4), rows.getString(5))))
        }
    }
}

internal fun SQLiteDatabase.entryPhoto(day: LocalDate, id: Long): String? =
    rawQuery("SELECT photo_path FROM food WHERE id = ? AND day = ?", arrayOf(id.toString(), day.toString()))
        .use { if (it.moveToFirst()) it.getString(0) else null }

internal fun FoodStore.setVenueLogo(id: Long, path: String) {
    val db = writableDatabase
    db.atomic {
        db.rawQuery("SELECT logo_path FROM venue WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getString(0)?.takeIf { old -> old != path }?.let(unusedPhotos::add)
        }
        val updated = db.update("venue", ContentValues().apply { put("logo_path", path) },
            "id = ?", arrayOf(id.toString()))
        if (updated == 0) unusedPhotos.add(path)
    }
    cleanUnusedPhotos()
}

internal fun FoodStore.cleanUnusedPhotos() {
    if (unusedPhotos.isEmpty()) return
    val db = writableDatabase
    if (db.inTransaction()) return
    val directory = File(context.filesDir, "photos").canonicalFile
    db.atomic {
        for (path in unusedPhotos) {
            val referenced = db.rawQuery(
                """SELECT 1 FROM food WHERE photo_path = ?
                   UNION ALL SELECT 1 FROM venue WHERE logo_path = ? LIMIT 1""", arrayOf(path, path)
            ).use { it.moveToFirst() }
            if (!referenced) {
                val file = File(path).canonicalFile
                if (file.parentFile == directory) {
                    runCatching { java.nio.file.Files.deleteIfExists(file.toPath()) }.onFailure {
                        android.util.Log.w("VenuePhotos", "Не удалось удалить фото", it)
                    }
                }
            }
        }
    }
    unusedPhotos.clear()
}
