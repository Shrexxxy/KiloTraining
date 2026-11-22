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
@Config(sdk=[35])
class RecipeTest {
    @get:Rule val compose = createComposeRule()
    private val rice = FoodDraft(name="Рис", weighted=true, grams="500", energy="350", macros=Macros("7","0","80"))
    private val sauce = FoodDraft(name="Соус", weighted=true, grams="100", energy="50", macros=Macros("","0","10"))
    private val pilaf = Recipe(name="Плов", cooked="1200", ingredients=listOf(rice,sauce))

    @Test fun portionsAndBackupUseIndependentSnapshots() {
        val context = RuntimeEnvironment.getApplication()
        val day = LocalDate.now()
        FoodStore(context).use { store ->
            store.saveRecipe(pilaf)
            val original = store.recipes().single()
            val portion = original.food()!!.copy(grams="280")
            assertEquals("420", portion.calories?.display())
            assertEquals("", portion.portionMacros.protein)
            assertEquals("0", portion.portionMacros.fat)
            store.save(day, portion)
            store.saveRecipe(original.copy(cooked="600"))
            FoodStore(context,"restored").use { restored ->
                restored.readBackup(StringWriter().also(store::exportDiary).toString().reader())
                assertEquals("420", restored.entries(day).single().calories.display())
                assertEquals("840", restored.recipes().single().food()!!.copy(grams="280").calories?.display())
            }
        }
        assertNull(pilaf.copy(cooked="0").food())
        assertNull(pilaf.copy(ingredients=emptyList()).food())
    }

    @Test fun previousDatabaseGainsEmptyRecipeBook() {
        val context = RuntimeEnvironment.getApplication()
        context.openOrCreateDatabase("beforeRecipes", 0, null).use { db ->
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT)")
            db.execSQL("INSERT INTO workout VALUES(1,'2026-11-01','Верх А')")
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER, warmup INTEGER)")
            db.version = 9
        }
        FoodStore(context, "beforeRecipes").use { store ->
            assertTrue(store.recipes().isEmpty())
            store.saveRecipe(pilaf)
            store.readableDatabase.rawQuery("SELECT title FROM workout", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Верх А", it.getString(0))
            }
        }
    }

    @Test fun preRecipeBackupOpensWithEmptyBook() {
        val backup = """{"format":"calorietraining/8","food":[],"product":[],"workout":[],
            "lifting":[],"workout_template":[],"template_set":[],"daily_goal":[{"id":"1","kcal":"2100"}],"measurement":[]}"""
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.readBackup(backup.reader())
            assertEquals(2100,store.goalsFor(LocalDate.now()).kcal)
            assertTrue(store.recipes().isEmpty())
        }
    }

    @Test fun choosePortionFromRecipeBook() {
        var selected: FoodEvent? = null
        compose.setContent { MaterialTheme {
            RecipeContent(FoodUiState(recipes=listOf(pilaf)), { selected = it })
        } }
        compose.onNodeWithText("Записать порцию").performClick()
        assertEquals(FoodEvent.LogRecipe(pilaf), selected)
    }
}
