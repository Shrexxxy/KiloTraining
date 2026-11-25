package app.calorietraining

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
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
class LabelEntryTest {
    @get:Rule val ui = createComposeRule()
    private val code = "4607112814141"
    private val draft = FoodDraft(name = "Йогурт", barcode = code, weighted = true, grams = "75",
        meal = Meal.entries.first { it != Meal.UNSPECIFIED }, venueName = "Кафе", photoPath = "photo.png")
    private val entry = "Ввести с этикетки"
    private val paste = "Вставить текст этикетки"
    private val warning = "Калории не сходятся с БЖУ. Проверьте запятые"

    @Test fun scanPortionSavesAndIsFoundOnPhone() {
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            val missing = store.lookup(code) { null }
            assertEquals(ScanOutcome.MISSING, missing.outcome)
            val state = mutableStateOf(FoodUiState(draft = missing.draft.copy(
                name = draft.name, grams = draft.grams, meal = draft.meal, venueName = draft.venueName, photoPath = draft.photoPath),
                scanOutcome = missing.outcome))
            var saved: FoodDraft? = null
            ui.setContent {
                FoodContent(state.value, { event ->
                    when (event) {
                        is FoodEvent.Edit -> state.value = state.value.copy(draft = event.value)
                        FoodEvent.Save -> {
                            saved = state.value.draft
                            store.save(LocalDate.now(), requireNotNull(saved))
                        }
                        else -> Unit
                    }
                })
            }
            click(entry)
            click("На порцию")
            input("Масса порции, г", "25")
            amounts("50", "2", "2", "6")
            see("200 ккал · Б 8 г · Ж 8 г · У 24 г")
            click("Применить")
            see("Штрихкод $code")
            click("Сохранить")
            assertEquals(draft.copy(energy = "200", macros = Macros("8", "8", "24")), saved)
            val found = store.lookup(code) { error("Товар должен быть на телефоне") }
            assertEquals(ScanOutcome.PHONE, found.outcome)
            assertEquals("200", found.draft.energy)
        }
    }

    @Test fun unitsWarningAndScreenshots() {
        val mode = mutableStateOf(ThemeMode.DARK)
        val screen = ui.setScreenshotContent({ mode.value }) { FoodContent(FoodUiState(draft = draft), {}) }
        click(entry)
        see("С этикетки")
        screen.captureScreen("label-entry-dark.png")
        ui.runOnIdle { mode.value = ThemeMode.LIGHT }
        screen.captureScreen("label-entry-light.png")
        ui.runOnIdle { mode.value = ThemeMode.DARK }
        input("Энергия, ккал", "418,4")
        click("кДж")
        field("Энергия, кДж").assertTextContains("418,4")
        see("100 ккал · Б — · Ж — · У —")
        click("ккал")
        field("Энергия, ккал").assertTextContains("418,4")
        amounts("51", "35", "2", "4,7")
        see(warning)
        see("Всё равно применить")
        screen.captureScreen("label-entry-warning-dark.png")
        input("Белки, г", "3,5")
        ui.onNodeWithText(warning).assertDoesNotExist()
        see("Применить")
    }

    @Test fun pasteConfirmationAndBackSurviveRestoration() {
        val restoration = StateRestorationTester(ui)
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        restoration.setContent { FoodContent(FoodUiState(draft = draft.copy(energy = "70")), {}) }
        click(entry)
        clipboard.clearPrimaryClip()
        click(paste)
        see("В буфере нет текста")
        clipboard.setPrimaryClip(ClipData.newPlainText("", "На 100 г: 418,4 кДж, белки 5 г, жиры 0 г, углеводы 20 г"))
        click(paste)
        see("Заменить введённые значения?")
        restoration.emulateSavedInstanceStateRestore()
        see("Заменить введённые значения?")
        click("Нет")
        field("Энергия, ккал").assertTextContains("70")
        click(paste)
        click("Заменить")
        see("пересчитано из кДж")

        restoration.emulateSavedInstanceStateRestore()
        field("Энергия, ккал").assertTextContains("100")
        see("пересчитано из кДж")
        ui.headerBack()
        field("Ккал на 100 г").assertTextContains("70")
    }

    @Test fun headerBackDiscardsEntered() = verifyBackDiscards { ui.headerBack() }

    @Test fun systemBackDiscardsEntered() = verifyBackDiscards { ui.runOnIdle { it.onBackPressed() } }

    @Test fun pastedScreenshot() {
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val screen = ui.setScreenshotContent { FoodContent(FoodUiState(draft = draft), {}) }
        click(entry)
        clipboard.setPrimaryClip(ClipData.newPlainText("", "На 100 г: 418,4 кДж, белки 5 г, жиры 0 г, углеводы 20 г"))
        click(paste)
        see("пересчитано из кДж")
        screen.captureScreen("label-entry-pasted-dark.png")
    }

    @Test fun pastedEnergyCheckUsesExplicitFractions() {
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ui.setContent { FoodContent(FoodUiState(draft = draft), {}) }
        click(entry)
        clipboard.setPrimaryClip(ClipData.newPlainText("", "На 100 г: 180 ккал, белки 10 г, жиры 4 г, углеводы 30 г, из них полиолы 10 г, из полиолов эритрит 5 г, волокна отдельно 6 г"))
        click(paste)
        see("По БЖУ примерно 180 ккал")
        ui.onNodeWithText(warning).assertDoesNotExist()
        see("Применить")
    }

    @Test fun largeFontKeepsFieldsAndActionsReachable() {
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ui.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                FoodContent(FoodUiState(draft = draft), {})
            }
        }
        click(entry)
        clipboard.setPrimaryClip(ClipData.newPlainText("", "На 100 г: 418,4 кДж, белки 5 г, жиры 0 г, углеводы 20 г"))
        click(paste)
        ui.onNode(hasScrollAction()).assertExists()
        ui.onNodeWithText("пересчитано из кДж").assertIsDisplayed()
        field("Энергия, ккал").assertIsDisplayed()
        field("Углеводы, г").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Применить").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun scanCardOffersEntryForMissingEnergyAndUnavailable() {
        val outcome = mutableStateOf(ScanOutcome.NO_ENERGY)
        ui.setContent { FoodContent(FoodUiState(draft = draft, scanOutcome = outcome.value), {}) }
        click(entry)
        see("С этикетки")
        ui.headerBack()
        ui.runOnIdle { outcome.value = ScanOutcome.UNAVAILABLE }
        click(entry)
        see("С этикетки")
    }

    @Test fun entryRequiresWeightBasis() {
        ui.setContent { FoodContent(FoodUiState(draft = draft.copy(weighted = false, barcode = null)), {}) }
        ui.onNodeWithText(entry).assertDoesNotExist()
    }

    private fun verifyBackDiscards(back: (OnBackPressedDispatcher) -> Unit) {
        val events = mutableListOf<FoodEvent>()
        lateinit var dispatcher: OnBackPressedDispatcher
        ui.setContent {
            dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            FoodContent(FoodUiState(draft = draft.copy(energy = "70")), { events.add(it) })
        }
        click(entry)
        see("С этикетки")
        amounts("200", "8", "8", "24")
        back(dispatcher)
        ui.onNodeWithText("С этикетки").assertDoesNotExist()
        see("Новая запись")
        field("Ккал на 100 г").assertTextContains("70")
        assertEquals(emptyList<FoodEvent>(), events)
    }

    private fun amounts(kcal: String, protein: String, fat: String, carbs: String) {
        input("Энергия, ккал", kcal)
        input("Белки, г", protein)
        input("Жиры, г", fat)
        input("Углеводы, г", carbs)
    }

    private fun field(label: String) = ui.onNode(hasText(label) and hasSetTextAction())
    private fun input(label: String, value: String) = field(label).performScrollTo().performTextReplacement(value)
    private fun click(label: String) {
        val node = ui.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun see(label: String) = ui.onNodeWithText(label).assertExists()
}
