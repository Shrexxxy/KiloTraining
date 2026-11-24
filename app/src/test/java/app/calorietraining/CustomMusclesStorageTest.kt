package app.calorietraining

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomMusclesStorageTest {
    private val app = RuntimeEnvironment.getApplication()
    private val chestAndTriceps = ExerciseTechnique(listOf(Muscle.Chest), listOf(Muscle.Triceps), emptyList())

    @Test fun savesReplacesAndClearsSelectionByName() {
        FoodStore(app).use { store ->
            store.saveCustomMuscles("Мой жим", chestAndTriceps.primary, chestAndTriceps.secondary)
            assertEquals(mapOf("мой жим" to chestAndTriceps), store.customMuscles())
            store.saveCustomMuscles("  МОЙ ЖИМ ", listOf(Muscle.Shoulders), emptyList())
            assertEquals(ExerciseTechnique(listOf(Muscle.Shoulders), emptyList(), emptyList()), store.customMuscles()["мой жим"])
            store.saveCustomMuscles("мой жим", emptyList(), emptyList())
            store.saveCustomMuscles("Жим штанги лежа", listOf(Muscle.Chest), emptyList())
            assertTrue(store.customMuscles().isEmpty())
        }
    }

    @Test fun database18KeepsWorkoutAndStartsWithEmptySelection() {
        app.openOrCreateDatabase("muscles-v18", 0, null).use { db ->
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, title TEXT)")
            db.execSQL("INSERT INTO workout VALUES(7,'Моя тренировка')")
            db.createLegacyFoodTable()
            db.createVenues()
            db.version = 18
        }
        FoodStore(app, "muscles-v18").use { store ->
            assertEquals(20, store.readableDatabase.version)
            store.readableDatabase.rawQuery("SELECT title FROM workout WHERE id = 7", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Моя тренировка", it.getString(0))
            }
            assertTrue(store.customMuscles().isEmpty())
        }
    }

    @Test fun backup17RoundTripsAndBackup16ClearsSelection() {
        FoodStore(app).use { store ->
            val backup = store.emptyBackup()
            assertEquals("calorietraining/18", backup.getString("format"))
            store.saveCustomMuscles("Мой жим", chestAndTriceps.primary, chestAndTriceps.secondary)
            val exported = store.emptyBackup()
            store.restore(backup)
            assertTrue(store.customMuscles().isEmpty())
            store.restore(exported)
            assertEquals(mapOf("мой жим" to chestAndTriceps), store.customMuscles())

            backup.put("format", "calorietraining/16").remove("exercise_muscle")
            store.restore(backup)
            assertTrue(store.customMuscles().isEmpty())
        }
    }

    @Test fun invalidMuscleRowsAreRejectedWithoutReplacingDiary() {
        FoodStore(app).use { store ->
            val before = store.emptyBackup()
            store.saveCustomMuscles("Мой жим", chestAndTriceps.primary, chestAndTriceps.secondary)
            for (row in listOf(
                """{"exercise_key":"другое","muscle":"Unknown","is_primary":"1"}""",
                """{"exercise_key":"","muscle":"Chest","is_primary":"1"}""",
                """{"exercise_key":"другое","muscle":"Chest","is_primary":"2"}""",
            )) {
                before.put("exercise_muscle", org.json.JSONArray("[$row]"))
                assertThrows(IllegalArgumentException::class.java) { store.restore(before) }
                assertEquals(mapOf("мой жим" to chestAndTriceps), store.customMuscles())
            }
        }
    }

    private fun FoodStore.restore(backup: JSONObject) {
        app.deleteDatabase("muscles-staged")
        FoodStore(app, "muscles-staged").use { staged ->
            staged.readBackup(backup.toString().reader())
            replaceFrom(staged)
        }
    }

    private fun FoodStore.emptyBackup() = JSONObject(StringWriter().also(::exportDiary).toString())
}
