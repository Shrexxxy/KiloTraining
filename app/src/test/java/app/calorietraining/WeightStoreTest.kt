package app.calorietraining

import android.database.SQLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Раздельное сохранение веса и жира на настоящей базе: второе поле дня не теряется. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeightStoreTest {
    private val today = LocalDate.now()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun row(store: FoodStore, day: LocalDate = today): Pair<String, String>? =
        store.readableDatabase.rawQuery("SELECT weight,fat FROM measurement WHERE day=?", arrayOf(day.toString())).use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
        }

    @Test fun weightKeepsFat() = FoodStore(app).use { store ->
        store.saveMeasurement(Measurement(today, fat = "18"))
        store.saveWeight(today, "82")
        assertEquals("82" to "18", row(store))
    }

    @Test fun fatKeepsWeight() = FoodStore(app).use { store ->
        store.saveMeasurement(Measurement(today, weight = "82"))
        store.saveFat(today, "19")
        assertEquals("82" to "19", row(store))
    }

    @Test fun twoSavesInARowKeepBothFields() = FoodStore(app).use { store ->
        store.saveWeight(today, "82")
        store.saveFat(today, "19")
        store.saveWeight(today, "81.5")
        store.saveFat(today, "18.5")
        assertEquals("81.5" to "18.5", row(store))
    }

    @Test fun savesIntoEmptyDayWithoutOtherField() = FoodStore(app).use { store ->
        store.saveWeight(today, "80")
        store.saveFat(today.minusDays(3), "20")
        assertEquals("80" to "", row(store))
        assertEquals("" to "20", row(store, today.minusDays(3)))
    }

    @Test fun pastDateKeepsOtherFieldAndOtherDays() = FoodStore(app).use { store ->
        store.saveMeasurement(Measurement(today.minusDays(5), "83", "22"))
        store.saveMeasurement(Measurement(today, "82", "21"))
        store.saveWeight(today.minusDays(5), "84")
        assertEquals("84" to "22", row(store, today.minusDays(5)))
        assertEquals("82" to "21", row(store))
    }

    @Test fun invalidValuesWriteNothing() = FoodStore(app).use { store ->
        store.saveMeasurement(Measurement(today, "82", "21"))
        listOf("", "0", "-1", "abc").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { store.saveWeight(today, value) }
        }
        listOf("", "0", "101", "abc").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { store.saveFat(today, value) }
        }
        assertEquals("82" to "21", row(store))
    }

    @Test fun databaseErrorLeavesRowUntouched() = FoodStore(app).use { store ->
        store.saveMeasurement(Measurement(today, "82", "21"))
        store.writableDatabase.execSQL(
            "CREATE TRIGGER block_update BEFORE UPDATE ON measurement BEGIN SELECT RAISE(ABORT, 'x'); END")
        store.writableDatabase.execSQL(
            "CREATE TRIGGER block_insert BEFORE INSERT ON measurement BEGIN SELECT RAISE(ABORT, 'x'); END")
        assertThrows(SQLException::class.java) { store.saveWeight(today, "70") }
        assertThrows(SQLException::class.java) { store.saveFat(today, "10") }
        assertEquals("82" to "21", row(store))
    }

    @Test fun editedFormKeepsFieldChangedElsewhere() = FoodStore(app).use { store ->
        val opened = Measurement(today, "82", "21")
        store.saveMeasurement(opened)
        store.saveWeight(today, "80")
        store.saveEdited(opened.copy(fat = "19"), opened)
        assertEquals("80" to "19", row(store))
    }

    @Test fun editedFormWritesBothChangedFields() = FoodStore(app).use { store ->
        val opened = Measurement(today, "82", "21")
        store.saveMeasurement(opened)
        store.saveEdited(Measurement(today, "81", ""), opened)
        assertEquals("81" to "", row(store))
    }
}
