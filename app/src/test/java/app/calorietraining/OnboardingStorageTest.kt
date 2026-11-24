package app.calorietraining

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingStorageTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 23)
    private val goals = DailyGoals(2760, MacroGoals(128, 72, 400))

    @Test fun upgradesDatabase16AndKeepsGoals() {
        context.openOrCreateDatabase("onboarding-v16", 0, null).use { db ->
            db.execSQL("CREATE TABLE goal(start_day TEXT PRIMARY KEY, kcal INTEGER, protein INTEGER, fat INTEGER, carbs INTEGER)")
            db.execSQL("INSERT INTO goal VALUES (?,2760,128,72,400)", arrayOf(day.toString()))
            db.createLegacyFoodTable()
            db.version = 16
        }
        FoodStore(context, "onboarding-v16").use { store ->
            assertEquals(19, store.readableDatabase.version)
            assertEquals(goals, store.goalsFor(day))
            store.readableDatabase.rawQuery("SELECT * FROM user_profile", null).use {
                assertFalse(it.moveToFirst())
            }
        }
    }

    @Test fun roundTripsProfileInBackup15() {
        val backup = FoodStore(context, "source").use { store ->
            store.writableDatabase.execSQL(
                "INSERT INTO user_profile VALUES (1,'MALE',1996,180,'MODERATE','MAINTAIN','2026-11-23T10:15:00Z')"
            )
            store.setGoals(day, goals)
            store.backup()
        }
        assertEquals("calorietraining/17", backup.getString("format"))
        FoodStore(context, "destination").use { store ->
            store.readBackup(backup.toString().reader())
            assertEquals(goals, store.goalsFor(day))
            val expected = backup.getJSONArray("user_profile").getJSONObject(0)
            val actual = store.backup().getJSONArray("user_profile").getJSONObject(0)
            assertEquals(7, actual.length())
            for (key in expected.keys()) assertEquals(expected.getString(key), actual.getString(key))
        }
    }

    @Test fun restoresBackup14WithoutProfile() {
        val backup = """{"format":"calorietraining/14","recipe":[],"ingredient":[],"food":[],
            "my_product":[],"workout":[],"lifting":[],"workout_template":[],"template_set":[],
            "goal":[{"start_day":"$day","kcal":"2760","protein":"128","fat":"72","carbs":"400"}],
            "measurement":[]}"""
        FoodStore(context, "legacy").use { store ->
            store.readBackup(backup.reader())
            assertEquals(goals, store.goalsFor(day))
            assertEquals(0, store.backup().getJSONArray("user_profile").length())
        }
    }

    @Test fun distinguishesZeroCarbsFromMissingCarbsAfterRestore() {
        val zeroCarbs = DailyGoals(3550, MacroGoals(480, 270, 0))
        val nextDay = day.plusDays(1)
        val backup = FoodStore(context, "zero-source").use { store ->
            store.setGoals(day, zeroCarbs)
            store.setGoals(nextDay, DailyGoals(2400))
            assertEquals(zeroCarbs, store.goalsFor(day))
            store.backup()
        }
        FoodStore(context, "zero-destination").use { store ->
            store.readBackup(backup.toString().reader())
            assertEquals(zeroCarbs, store.goalsFor(day))
            assertNull(store.goalsFor(nextDay).macros.carbs)
        }
    }

    private fun FoodStore.backup() = JSONObject(StringWriter().also(::exportDiary).toString())
}
