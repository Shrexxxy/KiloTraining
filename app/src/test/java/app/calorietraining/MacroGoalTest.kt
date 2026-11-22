package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.json.JSONObject
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
class MacroGoalTest {
    @get:Rule val ui = createComposeRule()
    private val today get() = LocalDate.now()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun storesAndClearsIndividualGoals() {
        FoodStore(context).use { store ->
            store.setGoals(today, DailyGoals(macros = MacroGoals(protein = 140, fat = 70)))
            assertEquals(MacroGoals(140, 70, null), store.goalsFor(today).macros)
            store.setGoals(today, DailyGoals(macros = MacroGoals(fat = 70)))
            assertEquals(MacroGoals(null, 70, null), store.goalsFor(today).macros)
            store.setGoals(today, DailyGoals(macros = MacroGoals()))
            assertEquals(MacroGoals(), store.goalsFor(today).macros)
        }
    }

    @Test fun rejectsZeroGoal() {
        FoodStore(context).use { store ->
            assertThrows(IllegalArgumentException::class.java) {
                store.setGoals(today, DailyGoals(macros = MacroGoals(protein = 0)))
            }
        }
    }

    @Test fun upgradeKeepsCalorieGoal() {
        context.openOrCreateDatabase("upgrade", 0, null).use { db ->
            db.execSQL("CREATE TABLE daily_goal(id INTEGER PRIMARY KEY, kcal INTEGER NOT NULL)")
            db.execSQL("INSERT INTO daily_goal VALUES(1, 2200)")
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT, minutes INTEGER)")
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER, warmup INTEGER)")
            db.version = 11
        }
        FoodStore(context, "upgrade").use { store ->
            assertEquals(17, store.readableDatabase.version)
            assertEquals(2200, store.goalsFor(today).kcal)
            assertEquals(MacroGoals(), store.goalsFor(today).macros)
        }
    }

    @Test fun backupRoundTripPreservesGoals() {
        val backup = FoodStore(context).use { store ->
            store.setGoals(today, DailyGoals(2200, MacroGoals(protein = 140, fat = 70)))
            StringWriter().also(store::exportDiary).toString()
        }
        val json = JSONObject(backup)
        assertEquals("calorietraining/15", json.getString("format"))
        val row = json.getJSONArray("goal").getJSONObject(0)
        assertEquals("140", row.get("protein"))
        assertEquals("0", row.get("carbs"))
        FoodStore(context, "restored").use { store ->
            store.readBackup(backup.reader())
            assertEquals(2200, store.goalsFor(today).kcal)
            assertEquals(MacroGoals(140, 70, null), store.goalsFor(today).macros)
        }
    }

    private val previousBackup = """{"format":"calorietraining/10","food":[],"product":[],
        "workout":[],"lifting":[],"workout_template":[],"template_set":[],"recipe":[],"ingredient":[],
        "measurement":[],"daily_goal":[{"id":"1","kcal":"2200"}]}"""

    @Test fun previousBackupHasNoMacroGoals() {
        FoodStore(context).use { store ->
            store.readBackup(previousBackup.reader())
            assertEquals(2200, store.goalsFor(today).kcal)
            assertEquals(MacroGoals(), store.goalsFor(today).macros)
        }
    }

    @Test fun rejectsNegativeProteinInBackup() {
        val backup = JSONObject(previousBackup).put("format", "calorietraining/11")
        backup.put("macro_goal", org.json.JSONArray("""[{"id":"1","protein":"-5","fat":"0","carbs":"0"}]"""))
        FoodStore(context).use { store ->
            assertThrows(IllegalArgumentException::class.java) { store.readBackup(backup.toString().reader()) }
            assertNull(store.goalsFor(today).kcal)
        }
    }

    @Test fun dialogEmitsGoalsAndRejectsZeroProtein() {
        var event: FoodEvent? = null
        ui.setContent { MaterialTheme { FoodContent(FoodUiState(loaded = true), { event = it }) } }
        ui.onNodeWithText("Задать дневную цель").performClick()
        ui.onNodeWithText("Ккал в день").performTextReplacement("2200")
        ui.onNodeWithText("Белки, г").performTextReplacement("140")
        ui.onNodeWithText("Сохранить").performClick()
        assertEquals(FoodEvent.Goal(2200, MacroGoals(protein = 140)), event)
        ui.onNodeWithText("Задать дневную цель").performClick()
        ui.onNodeWithText("Белки, г").performTextReplacement("0")
        ui.onNodeWithText("Сохранить").assertIsNotEnabled()
    }
}
