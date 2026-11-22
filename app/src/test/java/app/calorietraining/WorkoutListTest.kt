package app.calorietraining

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutListTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test fun database14MigratesWithoutInventingTimestamps() {
        app.openOrCreateDatabase("workout-v14", 0, null).use { db ->
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT, minutes INTEGER)")
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER, warmup INTEGER)")
            db.execSQL("INSERT INTO workout VALUES(7,'2026-11-20','Старая',30)")
            db.execSQL("INSERT INTO lifting VALUES(8,7,'bench','Жим','40',10,0,0)")
            db.execSQL("""CREATE TABLE my_product (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL, brand TEXT NOT NULL, barcode TEXT,
                weighted INTEGER NOT NULL, energy TEXT NOT NULL, protein TEXT NOT NULL, fat TEXT NOT NULL,
                carbs TEXT NOT NULL, source TEXT NOT NULL, search_key TEXT NOT NULL, updated_at TEXT NOT NULL)""")
            db.version = 14
        }
        FoodStore(app, "workout-v14").use { store ->
            assertEquals(16, store.readableDatabase.version)
            assertEquals(listOf("2026-11-20", "Старая", "30", null, null, "0"),
                store.row("SELECT day,title,minutes,started_at,finished_at,live FROM workout WHERE id=7"))
            assertEquals(listOf("7", "Жим", "40", "10", null),
                store.row("SELECT session,exercise,weight,reps,done_at FROM lifting WHERE id=8"))
        }
    }

    @Test fun backup14RetainsWorkoutTimestamps() {
        FoodStore(app).use { source ->
            assertEquals("calorietraining/14", JSONObject(source.backup()).getString("format"))
            val id = source.fixture("Живая", "2026-11-23T19:05", live = true)
            source.writableDatabase.execSQL("UPDATE workout SET finished_at='2026-11-23T20:01',minutes=56 WHERE id=?", arrayOf(id))
            source.fixtureSet(id, "bench", "Жим", "60", "8", "2026-11-23T19:12:30")
            FoodStore(app, "round-trip").use { restored ->
                restored.readBackup(source.backup().reader())
                assertEquals(listOf("2026-11-23T19:05", "2026-11-23T20:01", "1", "56"),
                    restored.row("SELECT started_at,finished_at,live,minutes FROM workout WHERE id=$id"))
                assertEquals(listOf("2026-11-23T19:12:30"), restored.row("SELECT done_at FROM lifting WHERE session=$id"))
            }
        }
    }

    @Test fun backup13KeepsDatesAndSets() {
        val backup = """{"format":"calorietraining/13","workout":[{"id":"7","day":"2026-11-20","title":"Старая","minutes":"30"}],
            "lifting":[{"id":"8","session":"7","exercise_group":"bench","exercise":"Жим","weight":"40","reps":"10","planned":"0","warmup":"0"}],
            "food":[],"my_product":[],"measurement":[],"goal":[],"recipe":[],"ingredient":[],"workout_template":[],"template_set":[]}"""
        FoodStore(app).use { store ->
            store.readBackup(backup.reader())
            assertEquals(listOf("2026-11-20", "30", null, null, "0"),
                store.row("SELECT day,minutes,started_at,finished_at,live FROM workout WHERE id=7"))
            assertEquals(listOf("40", "10", null), store.row("SELECT weight,reps,done_at FROM lifting WHERE id=8"))
        }
    }

    private fun FoodStore.backup() = StringWriter().also(this::exportDiary).toString()
}

internal class WorkoutTestClock(
    @Volatile private var instant: Instant = Instant.parse("2026-11-23T12:05:00Z"),
    private val zone: ZoneId = ZoneId.of("Asia/Krasnoyarsk"),
) : Clock() {
    override fun instant() = instant
    override fun getZone() = zone
    override fun withZone(zone: ZoneId) = WorkoutTestClock(instant, zone)

    fun set(local: String) {
        instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    }
}

internal fun FoodStore.row(sql: String): List<String?> = readableDatabase.rawQuery(sql, null).use { cursor ->
    assertTrue("Запрос должен вернуть строку", cursor.moveToFirst())
    List(cursor.columnCount) { cursor.getString(it) }
}

internal fun FoodStore.fixture(title: String, time: String, live: Boolean = false): Long {
    val id = newWorkout(LocalDate.parse(time.substringBefore('T')))
    writableDatabase.execSQL("UPDATE workout SET title=? WHERE id=?", arrayOf<Any>(title, id))
    if ('T' in time) writableDatabase.execSQL(
        "UPDATE workout SET started_at=?,live=? WHERE id=?", arrayOf<Any>(time, if (live) 1 else 0, id)
    )
    return id
}

internal fun FoodStore.fixtureSet(session: Long, group: String, exercise: String, weight: String, reps: String, time: String?) {
    saveSet(session, SetDraft(group = group, exercise = exercise, weight = weight, reps = reps))
    writableDatabase.execSQL(
        "UPDATE lifting SET done_at=? WHERE id=(SELECT max(id) FROM lifting WHERE session=?)", arrayOf<Any?>(time, session)
    )
}
