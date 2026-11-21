package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.json.JSONArray
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
class GoalHistoryTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val first = LocalDate.of(2026, 11, 1)
    private val third = first.plusDays(2)
    private val initial = DailyGoals(2200, MacroGoals(protein = 140))

    private fun FoodStore.countGoals() =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM goal", null).use { it.moveToFirst(); it.getInt(0) }

    @Test fun lastChangeAppliesUntilNextChange() {
        FoodStore(context).use { store ->
            store.setGoals(first, initial)
            store.setGoals(third, DailyGoals(2000))
            assertEquals(DailyGoals(), store.goalsFor(first.minusDays(1)))
            for (day in listOf(first, first.plusDays(1))) assertEquals(initial, store.goalsFor(day))
            for (day in listOf(third, first.plusDays(9))) assertEquals(DailyGoals(2000), store.goalsFor(day))
        }
    }

    @Test fun editingSameDayReplacesRow() {
        FoodStore(context).use { store ->
            store.setGoals(third, DailyGoals(2000))
            store.setGoals(third, DailyGoals(1900))
            assertEquals(1, store.countGoals())
            assertEquals(DailyGoals(1900), store.goalsFor(third))
        }
    }

    @Test fun disablingPreservesEarlierDays() {
        FoodStore(context).use { store ->
            store.setGoals(first, initial)
            store.setGoals(third, DailyGoals(2000))
            store.setGoals(first.plusDays(4), DailyGoals())
            assertEquals(DailyGoals(), store.goalsFor(first.plusDays(4)))
            assertEquals(DailyGoals(2000), store.goalsFor(first.plusDays(3)))
        }
    }

    @Test fun zeroCaloriesAreRejected() {
        FoodStore(context).use { store ->
            assertThrows(IllegalArgumentException::class.java) { store.setGoals(first, DailyGoals(0)) }
        }
    }

    private fun previousDatabase(name: String, goals: Boolean, vararg days: LocalDate) {
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE food(day TEXT)")
            db.execSQL("CREATE TABLE daily_goal(id INTEGER PRIMARY KEY, kcal INTEGER)")
            db.execSQL("CREATE TABLE macro_goal(id INTEGER PRIMARY KEY, protein INTEGER, fat INTEGER, carbs INTEGER)")
            for (day in days) db.execSQL("INSERT INTO food VALUES(?)", arrayOf(day.toString()))
            if (goals) {
                db.execSQL("INSERT INTO daily_goal VALUES(1,2200)")
                db.execSQL("INSERT INTO macro_goal VALUES(1,140,0,0)")
            }
            db.version = 12
        }
    }

    @Test fun migrationStartsAtEarliestMeal() {
        previousDatabase("old", true, first.plusDays(9), first)
        FoodStore(context, "old").use { store ->
            assertEquals(1, store.countGoals())
            store.readableDatabase.rawQuery("SELECT start_day,kcal,protein,fat,carbs FROM goal", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(listOf(first.toString(),"2200","140","0","0"), (0..4).map(it::getString))
            }
            assertEquals(DailyGoals(), store.goalsFor(first.minusDays(1)))
            store.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE name IN ('daily_goal','macro_goal')", null).use {
                assertFalse(it.moveToFirst())
            }
        }
    }

    @Test fun migrationHandlesMissingMealsOrGoals() {
        previousDatabase("no_meals", true)
        FoodStore(context, "no_meals").use { store ->
            val today = LocalDate.now()
            assertEquals(initial, store.goalsFor(today))
            assertEquals(DailyGoals(), store.goalsFor(today.minusDays(1)))
        }
        previousDatabase("no_goals", false, first)
        FoodStore(context, "no_goals").use { assertEquals(0, it.countGoals()) }
    }

    private fun FoodStore.backup() = JSONObject(StringWriter().also(::exportDiary).toString())

    @Test fun backupRestoresBothChanges() {
        val backup = FoodStore(context).use { store ->
            store.setGoals(first, initial)
            store.setGoals(third, DailyGoals(2000))
            store.backup()
        }
        assertEquals("calorietraining/12", backup.getString("format"))
        FoodStore(context, "restored").use { store ->
            store.readBackup(backup.toString().reader())
            assertEquals(2, store.countGoals())
            assertEquals(initial, store.goalsFor(first))
            assertEquals(DailyGoals(2000), store.goalsFor(third))
        }
    }

    @Test fun legacyBackupsStartAtFirstImportedMeal() {
        val day = first.plusDays(4)
        for (version in listOf(11, 10)) {
            val backup = """{"daily_goal":[{"id":"1","kcal":"2200"}],
                "food":[{"id":"1","day":"$day","name":"Рис","energy":"100","grams":"","calories":"100",
                "meal":"LUNCH","meal_time":"","protein":"","fat":"","carbs":""}],
                "product":[],"workout":[],"lifting":[],"measurement":[],"recipe":[],"ingredient":[],
                "workout_template":[],"template_set":[],"format":"calorietraining/$version"}"""
            val json = JSONObject(backup)
            if (version == 11) json.put("macro_goal", JSONArray("""[{"id":"1","protein":"140","fat":"0","carbs":"0"}]"""))
            FoodStore(context, "restore$version").use { store ->
                store.readBackup(json.toString().reader())
                assertEquals(1, store.countGoals())
                assertEquals(DailyGoals(), store.goalsFor(day.minusDays(1)))
                assertEquals(if (version == 11) initial else DailyGoals(2200), store.goalsFor(day))
            }
        }
    }

    @Test fun invalidBackupRollsBackEarlierGoal() {
        val backup = FoodStore(context).use { store ->
            store.setGoals(first, initial)
            store.backup()
        }
        val rows = backup.getJSONArray("goal")
        for ((field,value) in listOf("start_day" to "вчера", "kcal" to "-1", "start_day" to first.toString())) {
            rows.put(1, JSONObject(rows.getJSONObject(0).toString()).put("start_day", third.toString()).put(field, value))
            FoodStore(context, "invalid").use { store ->
                assertTrue(runCatching { store.readBackup(backup.toString().reader()) }.isFailure)
                assertEquals(0, store.countGoals())
            }
        }
    }

    @Test fun pastGoalsAreReadOnlyAndTodayCanBeEdited() {
        val today = LocalDate.now()
        val day = mutableStateOf(today.minusDays(1))
        ui.setContent { MaterialTheme {
            FoodContent(FoodUiState(day = day.value, loaded = true, goals = DailyGoals(2200)), {})
        } }
        ui.onNodeWithText("До цели · 2200 ккал").assertExists()
        ui.onNodeWithText("Изменить цель", substring = true).assertDoesNotExist()
        ui.runOnIdle { day.value = today }
        ui.onNodeWithText("Изменить цель · 2200 ккал").assertExists()
    }
}
