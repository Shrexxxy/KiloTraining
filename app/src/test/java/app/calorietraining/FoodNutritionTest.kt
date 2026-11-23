package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoodNutritionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weighedPortionSurvivesCopyAndBackup() {
        val day = LocalDate.now()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        FoodStore(context, "source").use { store ->
            val macros = Macros("10","0","")
            store.save(day, FoodDraft(name="Творог", weighted=true, energy="90", grams="150", macros=macros))
            store.copyMeal(day,day.plusDays(1),Meal.UNSPECIFIED)
            val backup = StringWriter().also(store::exportDiary)
            FoodStore(context,"restore").use { restored ->
                restored.readBackup(backup.toString().reader())
                val draft = restored.entries(day.plusDays(1)).single().edit()
                assertEquals(macros, draft.macros)
                assertEquals(Macros("15","0",""), draft.portionMacros)
            }
        }
    }

    @Test fun incompleteDayKeepsOnlyKnownTotals() {
        val a = FoodEntry(1,"A","80","",80.toBigDecimal(), macros = Macros("4","0","8"))
        val b = FoodEntry(2,"B","50","",50.toBigDecimal(), macros = Macros("2","","0"))
        assertEquals(Macros("6","","8"), listOf(a,b).dailyMacros())
    }

    @Test fun failedAndEmptySearchKeepManualEntryAvailable() {
        val online = androidx.compose.runtime.mutableStateOf<CatalogSearch>(CatalogSearch.Failed("чай"))
        var closed = false
        compose.setContent { MaterialTheme {
            FoodLibrary(emptyList(), {}, { closed = true }, search = {}, online = online.value)
        } }
        compose.onNodeWithText("Название продукта или блюда").performTextInput("чай")
        compose.onNodeWithText("Каталог недоступен", substring = true).assertExists()
        compose.runOnIdle { online.value = CatalogSearch.Results("чай", emptyList()) }
        compose.onNodeWithText("В каталоге нет совпадений с калорийностью").assertExists()
        compose.headerBack()
        assertTrue(closed)
    }

    @Test fun catalogSearchSendsNameAndSelectsResult() {
        var request = ""
        var chosen: FoodDraft? = null
        compose.setContent { MaterialTheme {
            FoodLibrary(emptyList(), { chosen = it }, {}, search = { request = it },
                online = CatalogSearch.Results("кефир", listOf(FoodDraft(name="Кефир", weighted=true, energy="50"))))
        } }
        compose.onNodeWithText("Название продукта или блюда").performTextInput("кефир")
        compose.onNodeWithText("Найти в интернете").performClick()
        assertEquals("кефир", request)
        compose.onNodeWithText("Кефир").performClick()
        assertEquals("50", chosen?.energy)
    }
}
