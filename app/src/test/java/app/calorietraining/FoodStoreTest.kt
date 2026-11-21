package app.calorietraining

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoodStoreTest {
    @Test fun reopensExactPortionFromDisk() {
        val day = LocalDate.of(2026, 11, 20)
        val draft = FoodDraft(name = "Йогурт", weighted = true, energy = "120", grams = "75,5")
        val context = RuntimeEnvironment.getApplication()
        FoodStore(context).use { it.save(day, draft) }
        FoodStore(context).use {
            val entry = it.entries(day).single()
            assertEquals("90.6", entry.calories.stripTrailingZeros().toPlainString())
            assertEquals("75,5", entry.grams)
            assertTrue(it.entries(day.plusDays(1)).isEmpty())
        }
    }

    @Test fun rejectsInvalidAmountsAndAllowsZeroCalorieFoods() {
        val draft = FoodDraft(name = "Вода", energy = "0")
        assertNotNull(draft.calories)
        assertNull(draft.copy(name = " ").calories)
        assertNull(draft.copy(energy = "-10").calories)
        assertNull(draft.copy(weighted = true, grams = "0").calories)
        assertNull(draft.copy(energy = "1e9999999").calories)
    }
}
