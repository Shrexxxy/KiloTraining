package app.calorietraining

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VenueCompatibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 23)

    @Test fun openingVersion17PreservesExistingFood() {
        val name = "venues-v17.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.createLegacyFoodTable()
            db.execSQL("INSERT INTO food VALUES (1, ?, 'Шаурма', '550', '', '550', 'LUNCH', '15:30', '32', '24', '51')",
                arrayOf(day.toString()))
            db.version = 17
        }
        FoodStore(context, name).use { store ->
            assertEquals(20, store.readableDatabase.version)
            val entry = store.entries(day).single()
            assertEquals("Шаурма", entry.name)
            assertEquals(Meal.LUNCH, entry.meal)
            assertEquals(Macros("32", "24", "51"), entry.macros)
            store.readableDatabase.rawQuery("SELECT venue_id, photo_path FROM food", null).use {
                it.moveToFirst()
                assertEquals(null, it.getString(0))
                assertEquals(null, it.getString(1))
            }
        }
    }

    @Test fun format15StillRestoresFood() {
        val legacy = """{
            "format":"calorietraining/15",
            "recipe":[],"ingredient":[],
            "food":[{"id":"1","day":"2026-11-23","name":"Шаурма","energy":"550","grams":"",
                "calories":"550","meal":"LUNCH","meal_time":"15:30","protein":"32","fat":"24","carbs":"51"}],
            "my_product":[],"workout":[],"lifting":[],"workout_template":[],"template_set":[],
            "goal":[],"measurement":[],"user_profile":[]
        }"""
        FoodStore(context, "venues-v15.db").use { store ->
            store.readBackup(legacy.reader())
            val entry = store.entries(day).single()
            assertEquals("Шаурма", entry.name)
            assertEquals("15:30", entry.mealTime)
        }
    }
}

internal fun SQLiteDatabase.createLegacyFoodTable() {
    execSQL("""CREATE TABLE food (
        id INTEGER PRIMARY KEY, day TEXT NOT NULL, name TEXT NOT NULL,
        energy TEXT NOT NULL, grams TEXT NOT NULL, calories TEXT NOT NULL,
        meal TEXT NOT NULL DEFAULT 'UNSPECIFIED', meal_time TEXT NOT NULL DEFAULT '',
        protein TEXT NOT NULL DEFAULT '', fat TEXT NOT NULL DEFAULT '', carbs TEXT NOT NULL DEFAULT ''
    )""")
    execSQL("CREATE INDEX food_day ON food(day)")
}
