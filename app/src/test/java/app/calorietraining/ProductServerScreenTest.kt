package app.calorietraining

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import java.io.File
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h900dp")
class ProductServerScreenTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun searchDisplaysSectionsInOrderAndHidesLowerDuplicates() {
        val code = "4600000000017"
        val local = FoodDraft(name = "Чай с телефона", barcode = code, energy = "50", weighted = true)
        val server = FoodDraft(name = "Чай из базы", barcode = "11111111", energy = "20", weighted = true)
        val catalog = FoodDraft(name = "Чай из каталога", energy = "10", weighted = true)
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                FoodLibrary(listOf(local), {}, {}, search = {}, online = CatalogSearch.Results(
                    "чай", listOf(catalog, server.copy(name = "Чай повтор из каталога")),
                    serverFoods = listOf(local.copy(name = "Чай повтор из базы"), server)
                ))
            }
        }
        ui.onNodeWithText("Название продукта или блюда").performTextInput("чай")
        val headings = listOf("Мои продукты", "Ваша база", "Open Food Facts · ODbL").map {
            ui.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(headings.sorted(), headings)
        assertEquals(3, headings.distinct().size)
        listOf(local, server, catalog).forEach { ui.onNodeWithText(it.name).assertIsDisplayed() }
        ui.onNodeWithText("Чай повтор из базы").assertDoesNotExist()
        ui.onNodeWithText("Чай повтор из каталога").assertDoesNotExist()
    }

    @Test fun persistsSettingsAndVerifiesConnection() {
        val address = "http://192.168.1.10:5080"
        val key = "test-key"
        val model = ProfileViewModel(context, serverFactory = { configuredAddress, configuredKey ->
            assertEquals(address, configuredAddress)
            assertEquals(key, configuredKey)
            object : FakeProductServer() {
                override suspend fun search(query: String, limit: Int): List<ServerProduct> {
                    assertEquals("те", query)
                    assertEquals(1, limit)
                    return emptyList()
                }
            }
        })
        lateinit var view: View
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                view = LocalView.current
                SettingsScreen(model, ThemeMode.DARK, {}, {}, {})
            }
        }
        ui.onNodeWithText("Сервер продуктов").performScrollTo()
        ui.onNodeWithText("Сервер продуктов").assertExists()
        ui.onNode(hasSetTextAction() and hasText("Адрес")).performTextInput(address)
        ui.onNode(hasSetTextAction() and hasText("Ключ")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).performTextInput(key)
        ui.onNodeWithText("Проверить связь").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Связь есть").fetchSemanticsNodes().isNotEmpty() }
        val preferences = context.getSharedPreferences("server", Context.MODE_PRIVATE)
        assertEquals(address, preferences.getString("address", null))
        assertEquals(key, preferences.getString("key", null))
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, "server-settings-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun settingsDisplayUnauthorizedStatusAfterSend() {
        val preferences = context.getSharedPreferences("server", Context.MODE_PRIVATE)
        preferences.edit().putString("address", "http://127.0.0.1:5080").putString("key", "test-key").commit()
        FoodStore(context).use { it.saveMyProduct(MyProduct(name = "Чай", energy = "20")) }
        val model = ProfileViewModel(context, serverFactory = { _, _ ->
            object : FakeProductServer() {
                override suspend fun post(card: ServerProduct) {
                    throw ProductServerException(ServerFailure.UNAUTHORIZED)
                }
            }
        })
        ui.setContent { DiaryTheme(ThemeMode.DARK) { SettingsScreen(model, ThemeMode.DARK, {}, {}, {}) } }
        ui.onNodeWithText("Отправить сейчас").performScrollTo()
        ui.onNodeWithText("Отправить сейчас").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Неверный ключ").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Ждут отправки: 1").assertExists()
    }
}
