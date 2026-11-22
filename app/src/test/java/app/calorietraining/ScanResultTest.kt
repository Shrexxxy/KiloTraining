package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ScanResultTest {
    @get:Rule val ui = createComposeRule()
    private val code = "4600000000000"
    private val productName = "Йогурт"

    private fun lookup(fetch: (String) -> Product?): ProductResult =
        FoodStore(RuntimeEnvironment.getApplication()).use { it.lookup(code, fetch) }

    @Test fun missingProductKeepsBarcode() {
        val result = lookup { null }
        assertEquals(ScanOutcome.MISSING, result.outcome)
        assertEquals(code, result.draft.barcode)
        assertEquals("", result.draft.name)
    }

    @Test fun productWithoutEnergyOffersItsName() {
        val result = lookup { Product(productName, "") }
        assertEquals(ScanOutcome.NO_ENERGY, result.outcome)
        assertEquals(productName, result.draft.name)
        assertEquals("", result.draft.energy)
    }

    @Test fun networkFailureHasItsOwnOutcome() {
        assertEquals(ScanOutcome.UNAVAILABLE, lookup { throw IOException() }.outcome)
    }

    @Test fun savedProductIsFoundOnPhone() {
        FoodStore(RuntimeEnvironment.getApplication()).use {
            it.save(LocalDate.now(), FoodDraft(name = productName, energy = "120",
                grams = "100", weighted = true, barcode = code))
        }
        val result = lookup { error("Товар должен быть в памяти телефона") }
        assertEquals(ScanOutcome.PHONE, result.outcome)
        assertEquals(productName, result.draft.name)
        assertEquals("120", result.draft.energy)
    }

    private fun scanState(outcome: ScanOutcome) =
        FoodUiState(draft = FoodDraft(weighted = true, barcode = code), scanOutcome = outcome)

    @Test fun missingProductShowsBarcodeAndSearchInBothThemes() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val state = mutableStateOf(FoodUiState())
        lateinit var view: View
        ui.setContent {
            DiaryTheme(theme.value) {
                view = LocalView.current
                FoodContent(state.value, {})
            }
        }
        ui.runOnIdle { state.value = scanState(ScanOutcome.MISSING) }
        for ((mode, suffix) in listOf(ThemeMode.LIGHT to "light", ThemeMode.DARK to "dark")) {
            ui.runOnIdle { theme.value = mode }
            ui.onNodeWithText("Штрихкод $code").assertIsDisplayed()
            ui.onNodeWithText("Этого товара нет в Open Food Facts. Заполните название и калорийность с упаковки — после сохранения товар запомнится, и в следующий раз всё подставится само").assertIsDisplayed()
            ui.onNodeWithText("Найти по названию").assertHasClickAction()
            ui.onNode(hasText("Название") and hasSetTextAction()).assertIsFocused()
            val bitmap = ui.runOnIdle {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val dir = File("build/screenshots").apply { mkdirs() }
            File(dir, "scan-missing-$suffix.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Test fun retryUsesScannedBarcode() {
        var received: FoodEvent? = null
        ui.setContent { FoodContent(scanState(ScanOutcome.UNAVAILABLE), { received = it }) }
        ui.onNodeWithText("Повторить поиск").assertIsDisplayed().performClick()
        assertEquals(FoodEvent.Barcode(code), received)
    }

    @Test fun choosingSearchResultKeepsScannedBarcode() {
        val state = mutableStateOf(scanState(ScanOutcome.MISSING))
        val food = FoodDraft(name = productName, energy = "120", weighted = true)
        var chosen: FoodDraft? = null
        ui.setContent {
            FoodContent(state.value, { event ->
                when (event) {
                    FoodEvent.BrowseFoods -> state.value = state.value.copy(library = listOf(food))
                    is FoodEvent.Edit -> chosen = event.value
                    else -> Unit
                }
            })
        }
        ui.onNodeWithText("Найти по названию").performClick()
        ui.onNodeWithText("Поиск продуктов").assertIsDisplayed()
        ui.onNodeWithText(productName).performClick()
        assertEquals(code, chosen?.barcode)
        assertEquals(productName, chosen?.name)
    }
}
