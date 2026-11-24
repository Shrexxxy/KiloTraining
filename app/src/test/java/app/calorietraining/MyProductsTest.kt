package app.calorietraining

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
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
class MyProductsTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 20)
    private val code = "4600000000017"
    private val cheese = MyProduct(name = "Сырок глазированный", barcode = code, weighted = true, energy = "410")
    private val coffee = MyProduct(name = "Кофе", weighted = false, energy = "25")

    @Test fun barcodeUpdatesExistingCard() = FoodStore(context).use { store ->
        store.saveMyProduct(cheese)
        store.saveMyProduct(cheese.copy(energy = "420"))
        assertEquals("420", store.myProducts().single().energy)
        assertEquals(code, store.myProductByBarcode(code)?.barcode)
    }

    @Test fun normalizedNameAndServingTypeIdentifyUnbarcodedCards() = FoodStore(context).use { store ->
        val buckwheat = MyProduct(name = "Гречка варёная", weighted = true, energy = "110")
        store.saveMyProduct(buckwheat)
        store.saveMyProduct(buckwheat.copy(name = "гречка   вареная"))
        store.saveMyProduct(buckwheat.copy(weighted = false))
        assertEquals(2, store.myProducts("ВАРЁНАЯ гречка").size)
        assertEquals(setOf(true, false), store.myProducts().map { it.weighted }.toSet())
    }

    @Test fun manualDiaryEntryCreatesCard() = FoodStore(context).use { store ->
        store.save(day, FoodDraft(name = "Кефир 1%", grams = "150", weighted = true, energy = "40"))
        val card = store.myProducts("кефир").single()
        assertEquals("Кефир 1%", card.name)
        assertEquals("40", card.energy)
        assertEquals("manual", card.source)
    }

    @Test fun editingCatalogNutritionChangesSource() = FoodStore(context).use { store ->
        val draft = store.lookup(code) { Product(cheese.name, cheese.energy) }.draft.copy(grams = "50")
        store.save(day, draft)
        assertEquals("openfoodfacts", store.myProducts().single().source)
        store.save(day, draft.copy(energy = "420"))
        assertEquals("manual", store.myProducts().single().source)
    }

    @Test fun savedBarcodeWorksOffline() = FoodStore(context).use { store ->
        store.saveMyProduct(cheese)
        val result = store.lookup(code) { throw IOException() }
        assertEquals(ScanOutcome.PHONE, result.outcome)
        assertEquals(cheese.name, result.draft.name)
        assertEquals("410", result.draft.energy)
    }

    @Test fun editingCardKeepsDiaryNutrition() = FoodStore(context).use { store ->
        store.save(day, FoodDraft(name = cheese.name, energy = "410", grams = "100", weighted = true, barcode = code))
        val card = requireNotNull(store.myProductByBarcode(code))
        store.saveMyProduct(card.copy(energy = "430"))
        assertEquals("430", store.myProducts().single().energy)
        assertEquals("410", store.entries(day).single().calories.display())
    }

    @Test fun version13MigratesCacheAndLatestFood() {
        context.openOrCreateDatabase("food.db", 0, null).use { db ->
            db.execSQL("""CREATE TABLE product (
                code TEXT PRIMARY KEY, name TEXT, energy TEXT, protein TEXT, fat TEXT, carbs TEXT)""")
            db.execSQL("INSERT INTO product VALUES (?, ?, '410', '', '', '')", arrayOf(code, cheese.name))
            db.execSQL("""CREATE TABLE food (
                id INTEGER PRIMARY KEY, day TEXT, name TEXT, energy TEXT, grams TEXT, calories TEXT,
                meal TEXT, meal_time TEXT, protein TEXT, fat TEXT, carbs TEXT)""")
            db.execSQL("""INSERT INTO food VALUES
                (3,'2026-11-19','Творог 5%','180','100','180','UNSPECIFIED','','','',''),
                (2,'2026-11-20','Творог 5%','200','100','200','UNSPECIFIED','','','',''),
                (1,'2026-11-20','Творог 5%','190','100','190','UNSPECIFIED','','','',''),
                (4,'2026-11-20','Кофе','25','','25','UNSPECIFIED','','','',''),
                (5,'2026-11-20','Сырок глазированный','399','100','399','UNSPECIFIED','','','','')""")
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT, minutes INTEGER)")
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER, warmup INTEGER)")
            db.execSQL("CREATE TABLE goal(start_day TEXT PRIMARY KEY, kcal INTEGER, protein INTEGER, fat INTEGER, carbs INTEGER)")
            db.version = 13
        }
        FoodStore(context).use { store ->
            assertEquals(20, store.readableDatabase.version)
            assertEquals(3, store.myProducts().size)
            assertEquals("openfoodfacts", store.myProductByBarcode(code)?.source)
            assertEquals("200", store.myProducts("творог 5%").single().energy)
            assertFalse(store.myProducts("кофе").single().weighted)
            store.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE name='product'", null).use {
                assertFalse(it.moveToFirst())
            }
        }
    }

    @Test fun restoresVersions13And12() {
        val cards = listOf(cheese.copy(brand = "Молочная", source = "openfoodfacts"), coffee)
        val output = StringWriter()
        val saved = FoodStore(context).use { store ->
            cards.forEach { store.saveMyProduct(it) }
            store.exportDiary(output)
            store.myProducts()
        }
        assertTrue(output.toString().contains("calorietraining/18"))
        FoodStore(context, "import13.db").use { store ->
            assertTrue(store.readBackup(StringReader(output.toString())).contains("Продуктов: 2"))
            assertEquals(saved, store.myProducts())
        }

        val legacy = """{
            "format":"calorietraining/12","recipe":[],"ingredient":[],
            "food":[{"id":"1","day":"2026-11-20","name":"Кофе","energy":"25","grams":"",
                "calories":"25","meal":"UNSPECIFIED","meal_time":"","protein":"","fat":"","carbs":""}],
            "product":[{"code":"$code","name":"Сырок глазированный","energy":"410","protein":"","fat":"","carbs":""}],
            "workout":[],"lifting":[],"workout_template":[],"template_set":[],"goal":[],"measurement":[]
        }"""
        FoodStore(context, "import12.db").use { store ->
            assertTrue(store.readBackup(StringReader(legacy)).contains("Продуктов: 2"))
            assertEquals("410", store.myProductByBarcode(code)?.energy)
            assertEquals("25", store.myProducts("кофе").single().energy)
        }
    }

    @Test fun managesAndDeletesMatchingProduct() = FoodStore(context).use { store ->
        val cards = mutableStateOf(seed(store))
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                MyProductsScreen(
                    products = cards.value,
                    onSave = { store.saveMyProduct(it); cards.value = store.myProducts() },
                    onDelete = { store.deleteMyProduct(it); cards.value = store.myProducts() },
                    onClose = {}
                )
            }
        }
        ui.onNodeWithText(coffee.name).assertIsDisplayed()
        ui.onNode(hasSetTextAction()).performTextInput("сыр")
        ui.onNodeWithText(coffee.name).assertDoesNotExist()
        ui.onNodeWithText(cheese.name).performClick()
        ui.onNodeWithText("Удалить").performClick()
        ui.onNode(hasText("Удалить") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithText(cheese.name).assertDoesNotExist()
        assertEquals(listOf(coffee.name), store.myProducts().map { it.name })
    }

    @Test fun foodSearchListsLocalCardsWithoutNetwork() = FoodStore(context).use { store ->
        val cards = seed(store)
        val state = mutableStateOf(FoodUiState(loaded = true))
        var selected: FoodDraft? = null
        val screen = ui.setScreenshotContent(ThemeMode.DARK) {
            FoodContent(state.value, { event ->
                when (event) {
                    is FoodEvent.AddInMeal -> state.value = state.value.copy(
                        addingMeal = event.meal, myProducts = cards
                    )
                    is FoodEvent.Edit -> selected = event.value
                    is FoodEvent.FindFood -> error("Локальный поиск не обращается к сети")
                    else -> Unit
                }
            })
        }
        ui.addInMeal(Meal.BREAKFAST)
        ui.onNodeWithText("Мои продукты").performClick()
        ui.onNodeWithText(cheese.name).assertIsDisplayed()
        screen.captureScreen("food-search-dark.png")
        ui.onNode(hasSetTextAction()).performTextInput("сыр")
        ui.onNode(hasText("Мои продукты") and !hasClickAction()).assertIsDisplayed()
        ui.onNodeWithText(coffee.name).assertDoesNotExist()
        ui.onNodeWithText(cheese.name).performClick()
        assertEquals(cheese.name, selected?.name)
        assertEquals("", selected?.grams)
    }

    private fun seed(store: FoodStore): List<MyProduct> {
        store.saveMyProduct(cheese.copy(brand = "Молочная", source = "openfoodfacts"))
        store.saveMyProduct(coffee)
        return store.myProducts()
    }
}
