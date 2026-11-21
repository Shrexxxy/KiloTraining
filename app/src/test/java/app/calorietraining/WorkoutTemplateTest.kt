package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutTemplateTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.now()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun snapshotRestoresAndStartsAsPlan() {
        FoodStore(context).use { store ->
            val session = store.newWorkout(day)
            val warmup = SetDraft(exercise = "Жим штанги лёжа", weight = "20", reps = "15", warmup = true)
            store.saveSet(session, warmup)
            store.saveSet(session, warmup.copy(weight = "60", reps = "8", warmup = false))
            store.saveTemplate(session, "Верх А")
            val stats = store.profileStats(7).current
            assertEquals("60", stats.benchAverage?.display())
            assertEquals(2, stats.sets)
            val original = store.workouts(day).single().sets
            store.saveSet(session, original.last().copy(weight = "65"))
            val backup = StringWriter().also(store::exportDiary)
            FoodStore(context, "restored").use { saved ->
                saved.readBackup(backup.toString().reader())
                val template = saved.templates().single()
                val date = day.minusDays(1)
                saved.startTemplate(template.id, date)
                val workout = saved.workouts(date).single()
                assertEquals("Верх А", workout.title)
                assertTrue(workout.sets.all { it.planned })
                assertEquals(listOf("20", "60"), workout.sets.map { it.weight })
                assertEquals(listOf(true, false), workout.sets.map { it.warmup })
                assertEquals(1, workout.sets.distinctBy { it.group }.size)
                assertNotEquals(original.first().group, workout.sets.first().group)
                assertEquals(1, saved.profileStats(7).current.workouts)
            }
        }
    }

    @Test fun existingSetsMigrateAsWorkingSets() {
        context.openOrCreateDatabase("legacy", 0, null).use { db ->
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT)")
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER)")
            db.execSQL("INSERT INTO workout VALUES(1,?,'')", arrayOf(day.toString()))
            db.execSQL("INSERT INTO lifting VALUES(1,1,'a','Жим лёжа','40',10,0)")
            db.version = 8
        }
        FoodStore(context, "legacy").use { store ->
            assertFalse(store.workouts(day).single().sets.single().warmup)
            assertTrue(store.templates().isEmpty())
        }
    }

    @Test fun previousBackupRestoresWithWorkingSets() {
        val backup = """{"format":"calorietraining/7","food":[],"product":[],
            "workout":[{"id":"1","day":"$day","title":"Силовая"}],
            "lifting":[{"id":"1","session":"1","exercise_group":"a","exercise":"Жим лёжа","weight":"50","reps":"5","planned":"0"}],
            "daily_goal":[],"measurement":[]}"""
        FoodStore(context).use { store ->
            store.readBackup(backup.reader())
            assertFalse(store.workouts(day).single().sets.single().warmup)
            assertEquals("50", store.profileStats(7).current.benchAverage?.display())
        }
    }

    @Test fun markExistingSetAsWarmup() {
        val set = SetDraft(id = 7, exercise = "Тяга", weight = "20", reps = "12")
        var action: WorkoutEvent? = null
        compose.setContent { MaterialTheme {
            WorkoutContent(WorkoutState(day = day, session = 2, draft = set), { action = it })
        } }
        compose.onNodeWithText("Разминочный").performClick()
        assertEquals(WorkoutEvent.Edit(2, set.copy(warmup = true)), action)
    }

    @Test fun chooseTemplateForSelectedDay() {
        var action: WorkoutEvent? = null
        compose.setContent { MaterialTheme {
            WorkoutContent(WorkoutState(day = day, templates = listOf(WorkoutTemplate(4, "Верх А"))), { action = it })
        } }
        compose.onNodeWithText("Из шаблона").performClick()
        compose.onNodeWithText("Верх А").performClick()
        assertEquals(WorkoutEvent.StartTemplate(4), action)
    }
}
