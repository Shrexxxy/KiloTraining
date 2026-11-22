package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
@Config(sdk=[35])
class WorkoutDurationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileCountsTimeOncePerRecordedWorkoutAndBackupRetainsIt() {
        val day = LocalDate.now()
        val context = RuntimeEnvironment.getApplication()
        FoodStore(context).use { store ->
            val first = store.newWorkout(day)
            val set = SetDraft(exercise="Тяга", weight="40", reps="10")
            repeat(2) { store.saveSet(first, set) }
            store.setDuration(first, 45)
            val second = store.newWorkout(day)
            store.saveSet(second,set)
            val planned = store.newWorkout(day)
            store.saveSet(planned,set,planned=true)
            store.setDuration(planned,90)
            val stats = store.profileStats(7).current
            assertEquals(2, stats.workouts)
            assertEquals(1, stats.timedWorkouts)
            assertEquals(45L, stats.totalMinutes)
            store.setDuration(second,60)
            assertEquals("52,5",store.profileStats(7).current.averageMinutes?.display())
            store.repeatWorkout(store.workouts(day).first(), day.plusDays(1))
            assertNull(store.workouts(day.plusDays(1)).single().minutes)
            FoodStore(context,"restored").use { restored ->
                restored.readBackup(StringWriter().also(store::exportDiary).toString().reader())
                assertEquals(45,restored.workouts(day).first().minutes)
                restored.setDuration(first,null)
                assertEquals("60",restored.profileStats(7).current.averageMinutes?.display())
            }
        }
    }

    @Test fun previousReleaseOpensWithoutInventingDuration() {
        val day = LocalDate.now()
        val context = RuntimeEnvironment.getApplication()
        context.openOrCreateDatabase("upgrade", 0, null).use { db ->
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT)")
            db.execSQL("INSERT INTO workout VALUES(7,?,'Силовая')", arrayOf(day.toString()))
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER, warmup INTEGER)")
            db.version = 10
        }
        FoodStore(context,"upgrade").use { store ->
            store.readableDatabase.rawQuery("SELECT title,minutes FROM workout WHERE id=7",null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Силовая",it.getString(0))
                assertEquals(0,it.getInt(1))
            }
        }
        val backup = """{"format":"calorietraining/9","workout":[{"id":"7","day":"$day","title":"Силовая"}],
            "lifting":[],"food":[],"product":[],"measurement":[],"daily_goal":[],
            "recipe":[],"ingredient":[],"workout_template":[],"template_set":[]}"""
        FoodStore(context,"oldBackup").use { restored ->
            restored.readBackup(backup.reader())
            assertNull(restored.workouts(day).single().minutes)
        }
    }

    @Test fun durationEditorValidatesMinutes() {
        var event: WorkoutEvent? = null
        compose.setContent { MaterialTheme {
            WorkoutContent(WorkoutState(day=LocalDate.now().minusDays(1), list=listOf(Workout(1,emptyList())), opened=1), { event=it })
        } }
        compose.onNodeWithText("Действия").performClick()
        compose.onNodeWithText("Длительность").performClick()
        compose.onNodeWithText("Минуты").performTextReplacement("0")
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithText("Минуты").performTextReplacement("45")
        compose.onNodeWithText("Сохранить").performClick()
        assertEquals(WorkoutEvent.Duration(1,45),event)
    }
}
