package app.calorietraining

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.StringWriter
import java.time.LocalDate
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoBackupTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 23)

    @Test fun upgradesVersion19PreservingFoodVenueAndMuscles() {
        val name = "photos-v19.db"
        val file = app.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.createLegacyFoodTable()
            db.createVenues()
            db.execSQL("CREATE TABLE exercise_muscle (exercise_key TEXT, muscle TEXT, is_primary INTEGER)")
            db.execSQL("INSERT INTO exercise_muscle VALUES ('press', 'CHEST', 1)")
            db.execSQL("INSERT INTO venue VALUES (1, 'Кафе', NULL, 1)")
            db.execSQL("INSERT INTO food (id, day, name, energy, grams, calories, venue_id) VALUES (1, ?, 'Суп', '100', '', '100', 1)",
                arrayOf(day.toString()))
            db.version = 19
        }
        FoodStore(app, name).use { store ->
            val db = store.readableDatabase
            assertEquals(21, db.version)
            db.rawQuery("SELECT food.name, venue.name, photo_hash, logo_hash, exercise_key FROM food JOIN venue ON venue.id = food.venue_id CROSS JOIN exercise_muscle", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Суп", it.getString(0))
                assertEquals("Кафе", it.getString(1))
                assertNull(it.getString(2))
                assertNull(it.getString(3))
                assertEquals("press", it.getString(4))
            }
        }
    }

    @Test fun roundTripsHashesWithoutPathsAndAcceptsOldFormats() {
        val jpeg = byteArrayOf(-1, -40, -1, 5)
        val hash = MessageDigest.getInstance("SHA-256").digest(jpeg).joinToString("") { "%02x".format(it) }
        val file = File(app.filesDir, "photos/local-only.jpg").apply { parentFile!!.mkdirs(); writeBytes(jpeg) }
        FoodStore(app, "photos-source.db").use { source ->
            source.save(day, FoodDraft(name = "Суп", energy = "100", venueName = "Кафе", photoPath = file.path))
            source.setVenueLogo(source.venues().single().id, file.path)
            val backup = StringWriter().also(source::exportDiary).toString()
            val json = JSONObject(backup)
            assertEquals("calorietraining/19", json.getString("format"))
            assertEquals(hash, json.getJSONArray("food").getJSONObject(0).getString("photo_hash"))
            assertEquals(hash, json.getJSONArray("venue").getJSONObject(0).getString("logo_hash"))
            assertFalse(backup.contains("local-only.jpg"))
            assertFalse(backup.contains("photo_path"))
            assertFalse(backup.contains("logo_path"))
            FoodStore(app, "photos-restore.db").use { restored ->
                restored.readBackup(backup.reader())
                assertEquals(hash, JSONObject(StringWriter().also(restored::exportDiary).toString())
                    .getJSONArray("food").getJSONObject(0).getString("photo_hash"))
            }
            for (version in listOf(17, 16)) {
                val old = JSONObject(backup).put("format", "calorietraining/$version")
                old.getJSONArray("food").getJSONObject(0).remove("photo_hash")
                old.getJSONArray("venue").getJSONObject(0).remove("logo_hash")
                if (version == 16) old.remove("exercise_muscle")
                FoodStore(app, "photos-restore-$version.db").use { restored ->
                    restored.readBackup(old.toString().reader())
                    assertEquals("Суп", restored.entries(day).single().name)
                }
            }
            json.getJSONArray("food").getJSONObject(0).put("photo_hash", "zz")
            FoodStore(app, "photos-invalid.db").use { restored ->
                restored.save(day, FoodDraft(name = "Гречка", energy = "100"))
                val invalid = JSONObject(json.toString()).apply {
                    getJSONArray("food").getJSONObject(0).put("id", "2")
                    getJSONArray("venue").getJSONObject(0).put("id", "2")
                    getJSONArray("food").getJSONObject(0).put("venue_id", "2")
                }
                assertThrows(IllegalArgumentException::class.java) { restored.readBackup(invalid.toString().reader()) }
                assertEquals("Гречка", restored.entries(day).single().name)
            }
        }
    }
}
