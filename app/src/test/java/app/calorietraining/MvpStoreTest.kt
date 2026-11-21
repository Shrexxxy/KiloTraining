package app.calorietraining

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MvpStoreTest {
    val context get() = RuntimeEnvironment.getApplication()
    val day = LocalDate.of(2026,11,20)
    @Test fun scannedProductCanBeCorrectedOfflineWithoutChangingOlderMeal() {
        FoodStore(context).use { db ->
            val draft = db.lookup("12345678") { Product("Йогурт","120") }.draft.copy(grams="75")
            db.save(day,draft)
            val cached = db.lookup("12345678") { error("offline") }.draft
            db.save(day,cached.copy(energy="140", grams="75"))
            assertEquals(listOf("90","105"), db.entries(day).map { it.calories.display() })
        }
        FoodStore(context).use { assertEquals("140",it.lookup("12345678") { error("offline") }.draft.energy) }
    }
    @Test fun missingNutritionOrNetworkAllowsManualCard() {
        FoodStore(context).use { db ->
            for ((index, fetch) in listOf<() -> Product?>({null},{Product("Сыр","")},{throw IOException()}).withIndex()) {
                val code = "1234567$index"
                val result = db.lookup(code) { fetch() }
                assertTrue(result.draft.energy.isEmpty())
                db.save(day,result.draft.copy(name="Сыр",energy="200",grams="150"))
                assertEquals("200",db.lookup(code){error("offline")}.draft.energy)
            }
        }
    }
    @Test fun upgradesFirstReleaseWithoutLosingMeals() {
        context.openOrCreateDatabase("food.db",0,null).use {
            it.execSQL("CREATE TABLE food(id INTEGER PRIMARY KEY,day TEXT,name TEXT,energy TEXT,grams TEXT,calories TEXT)")
            it.execSQL("INSERT INTO food VALUES(1,'2026-11-20','Обед','200','','200')")
            it.version=1
        }
        FoodStore(context).use {
            assertEquals("Обед",it.entries(day).single().name)
            it.newWorkout(day)
            assertEquals(1,it.workouts(day).size)
            it.cacheProduct("12345678",Product("Сыр","250"))
            assertEquals("250",it.lookup("12345678"){error("offline")}.draft.energy)
        }
    }

    @Test fun workoutsReopenWithOrderedSetsAndBodyweight() {
        FoodStore(context).use { db ->
            val session = db.newWorkout(day)
            db.saveSet(session,SetDraft(exercise="Жим",group="a",weight="40",reps="12"))
            db.saveSet(session,SetDraft(exercise="Жим",group="a",weight="45",reps="8"))
            db.saveSet(session,SetDraft(exercise="Турник",group="b",reps="6"))
        }
        FoodStore(context).use { db ->
            val workout=db.workouts(day).single()
            assertEquals(listOf("40","45",""),workout.sets.map { it.weight })
            db.saveSet(workout.id,workout.sets[1].copy(reps="9"))
            assertEquals(listOf("12","9","6"),db.workouts(day).single().sets.map { it.reps })
            assertTrue(db.workouts(day.plusDays(1)).isEmpty())
        }
    }
}
