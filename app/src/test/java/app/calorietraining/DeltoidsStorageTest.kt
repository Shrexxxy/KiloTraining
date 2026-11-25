package app.calorietraining

import java.io.StringWriter
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeltoidsStorageTest {
    private val app = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 20)
    private val allDelts = listOf(Muscle.FrontDelts, Muscle.SideDelts, Muscle.RearDelts)

    private fun FoodStore.snapshot() = JSONObject(StringWriter().also(::exportDiary).toString())

    private fun FoodStore.muscleRows(): Set<Triple<String, String, Int>> =
        readableDatabase.rawQuery("SELECT exercise_key, muscle, is_primary FROM exercise_muscle", null).use {
            buildSet { while (it.moveToNext()) add(Triple(it.getString(0), it.getString(1), it.getInt(2))) }
        }

    private fun withoutMuscles(snapshot: JSONObject) = JSONObject(snapshot.toString()).apply { remove("exercise_muscle") }

    private fun seedVersion20(name: String): JSONObject {
        app.deleteDatabase(name)
        return FoodStore(app, name).use { store ->
            store.save(day, FoodDraft(name = "Гречка", energy = "130", grams = "200"))
            val session = store.newWorkout(day)
            store.saveSet(session, SetDraft(exercise = "Жим гантелей сидя", weight = "20", reps = "8"))
            store.saveSet(session, SetDraft(exercise = "Мой жим", weight = "30", reps = "5", planned = true))
            val db = store.writableDatabase
            for ((key, muscle, primary) in listOf(
                Triple("мой жим", "Shoulders", 1), Triple("мой жим", "Triceps", 0),
                Triple("мои махи", "Shoulders", 0), Triple("мои махи", "Chest", 1),
                Triple("тяга", "Lats", 1),
            )) db.execSQL("INSERT INTO exercise_muscle VALUES (?,?,?)", arrayOf<Any>(key, muscle, primary))
            db.version = 20
            store.snapshot()
        }
    }

    @Test fun database20SplitsShouldersKeepsRolesAndOtherData() {
        val before = seedVersion20("deltoids-v20")
        FoodStore(app, "deltoids-v20").use { store ->
            assertEquals(21, store.readableDatabase.version)
            val after = store.snapshot()
            assertEquals(withoutMuscles(before).toString(), withoutMuscles(after).toString())
            assertEquals(
                buildSet {
                    allDelts.forEach { add(Triple("мой жим", it.name, 1)); add(Triple("мои махи", it.name, 0)) }
                    add(Triple("мой жим", "Triceps", 0)); add(Triple("мои махи", "Chest", 1)); add(Triple("тяга", "Lats", 1))
                },
                store.muscleRows(),
            )
            assertEquals(allDelts, store.customMuscles()["мой жим"]!!.primary)
            assertEquals(listOf(Muscle.Triceps), store.customMuscles()["мой жим"]!!.secondary)
            assertEquals(listOf(Muscle.Chest), store.customMuscles()["мои махи"]!!.primary)
            assertEquals(allDelts, store.customMuscles()["мои махи"]!!.secondary)
        }
    }

    @Test fun reopeningUpgradedDatabaseChangesNothing() {
        seedVersion20("deltoids-twice")
        val first = FoodStore(app, "deltoids-twice").use { it.snapshot().toString() to it.muscleRows() }
        val second = FoodStore(app, "deltoids-twice").use { it.snapshot().toString() to it.muscleRows() }
        assertEquals(first, second)
    }

    @Test fun database20WithoutCustomMusclesStaysIntact() {
        app.deleteDatabase("deltoids-empty")
        val before = FoodStore(app, "deltoids-empty").use {
            it.save(day, FoodDraft(name = "Суп", energy = "100", grams = "300"))
            it.writableDatabase.version = 20
            it.snapshot()
        }
        FoodStore(app, "deltoids-empty").use { store ->
            assertEquals(before.toString(), store.snapshot().toString())
            assertTrue(store.muscleRows().isEmpty())
        }
    }

    @Test fun backups17And18RestoreShouldersAsThreeDelts() {
        for (version in listOf(17, 18)) {
            val backup = FoodStore(app).use { it.snapshot() }
            backup.put("format", "calorietraining/$version")
            backup.put("exercise_muscle", JSONArray(
                """[{"exercise_key":"мой жим","muscle":"Shoulders","is_primary":"1"},
                    {"exercise_key":"мой жим","muscle":"Triceps","is_primary":"0"},
                    {"exercise_key":"мои махи","muscle":"Shoulders","is_primary":"0"},
                    {"exercise_key":"мои махи","muscle":"Chest","is_primary":"1"}]"""))
            FoodStore(app).use { store ->
                restore(store, backup)
                assertEquals(allDelts, store.customMuscles()["мой жим"]!!.primary)
                assertEquals(listOf(Muscle.Triceps), store.customMuscles()["мой жим"]!!.secondary)
                assertEquals(listOf(Muscle.Chest), store.customMuscles()["мои махи"]!!.primary)
                assertEquals(allDelts, store.customMuscles()["мои махи"]!!.secondary)
            }
        }
    }

    @Test fun backup19RoundTripsDeltsAndRejectsShouldersWithoutTouchingData() {
        FoodStore(app).use { store ->
            store.save(day, FoodDraft(name = "Гречка", energy = "130", grams = "200"))
            store.saveCustomMuscles("Мой жим", listOf(Muscle.RearDelts), listOf(Muscle.SideDelts))
            val exported = store.snapshot()
            assertEquals("calorietraining/19", exported.getString("format"))
            store.saveCustomMuscles("Мой жим", emptyList(), emptyList())
            restore(store, exported)
            assertEquals(exported.toString(), store.snapshot().toString())
            assertEquals(ExerciseTechnique(listOf(Muscle.RearDelts), listOf(Muscle.SideDelts), emptyList()),
                store.customMuscles()["мой жим"])

            val bad = JSONObject(exported.toString())
            bad.put("exercise_muscle", JSONArray("""[{"exercise_key":"мой жим","muscle":"Shoulders","is_primary":"1"}]"""))
            assertThrows(IllegalArgumentException::class.java) { restore(store, bad) }
            assertEquals(exported.toString(), store.snapshot().toString())
        }
    }

    private fun restore(store: FoodStore, backup: JSONObject) {
        app.deleteDatabase("deltoids-staged")
        FoodStore(app, "deltoids-staged").use { staged ->
            staged.readBackup(backup.toString().reader())
            store.replaceFrom(staged)
        }
    }
}
