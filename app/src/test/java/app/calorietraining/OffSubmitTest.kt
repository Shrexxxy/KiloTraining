package app.calorietraining

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
class OffSubmitTest {
    @get:Rule val ui = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private val code = "4600000000015"
    private val card = MyProduct(name = "Сырок", brand = "Марка", barcode = code,
        energy = "402", macros = Macros("8", "26", "34"))
    private val action = "Поделиться в Open Food Facts"
    private val consent = "Я сверил значения с упаковкой"

    @Test fun noActionWithoutConfiguredServer() {
        show()
        ui.onNodeWithText(action).assertDoesNotExist()
    }

    @Test fun noActionWhenDisabled() = fixture(enabled = false) { server ->
        show()
        ui.awaitUntil(5_000) { server.requests.contains("GET") }
        ui.onNodeWithText(action).assertDoesNotExist()
    }

    @Test fun rejectsUnsuitableCards() = fixture { server ->
        val invalid = listOf(card.copy(barcode = null), card.copy(barcode = "4600000000018"),
            card.copy(macros = Macros("", "26", "34")), card.copy(source = "openfoodfacts"),
            card.copy(weighted = false))
        val products = (listOf(card) + invalid).mapIndexed { index, value ->
            value.copy(id = index.toLong() + 1, name = "Продукт ${index + 1}")
        }
        ui.setScreenshotContent { MyProductsScreen(products, {}, {}, {}) }
        openCard(products.first().name)
        awaitAction()
        for (product in products.drop(1)) {
            ui.onNodeWithContentDescription("Назад").performClick()
            openCard(product.name)
            ui.onNodeWithText(action).assertDoesNotExist()
        }
        assertEquals(listOf("GET"), server.requests)
    }

    @Test fun consentAndPendingPutBeforeOnePost() = fixture { server ->
        val saved = show()
        awaitAction()
        openDialog()
        for (value in listOf(code, "Сырок", "Марка", "402 ккал", "8 г", "26 г", "34 г")) {
            ui.onNode(hasAnyAncestor(isDialog()) and hasText(value, substring = true)).assertIsDisplayed()
        }
        ui.onNodeWithText("Данные станут общедоступными по лицензии ODbL").assertIsDisplayed()
        ui.onNodeWithText("Тестовая площадка, настоящая карточка не изменится").assertIsDisplayed()
        ui.onNodeWithText("Отправить").assertIsNotEnabled()
        accept()
        awaitText("Отправлено")
        assertEquals(listOf("GET", "PUT", "POST"), server.requests)
        FoodStore(context).use {
            assertEquals(saved, it.myProductByBarcode(code))
            assertEquals(0, it.pendingSyncCount())
        }
    }

    @Test fun failedPutStopsSubmission() = fixture { server ->
        server.putStatus = 500
        val saved = show()
        awaitAction()
        openDialog()
        accept()
        awaitText("Сначала сохраните товар на своём сервере")
        assertEquals(listOf("GET", "PUT"), server.requests)
        FoodStore(context).use {
            assertEquals(saved, it.myProductByBarcode(code))
            assertEquals(1, it.pendingSyncCount())
        }
    }

    @Test fun changedCardAfterPutStopsSubmission() = fixture { server ->
        server.onPut = { FoodStore(context).use { it.saveMyProduct(card.copy(energy = "403")) } }
        show()
        awaitAction()
        openDialog()
        accept()
        awaitText("Сначала сохраните товар на своём сервере")
        assertEquals(listOf("GET", "PUT"), server.requests)
        FoodStore(context).use {
            assertEquals("403", it.myProductByBarcode(code)?.energy)
            assertEquals(1, it.pendingSyncCount())
        }
    }

    @Test fun outcomesPreserveProduct() = fixture { server ->
        val saved = show()
        awaitAction()
        for ((status, message) in listOf(
            409 to "Товар уже есть в Open Food Facts",
            503 to "Отправка выключена на сервере",
            429 to "Open Food Facts не принял данные",
            502 to "Open Food Facts не принял данные",
            -1 to "Нет связи с сервером"
        )) {
            server.postStatus = status
            openDialog()
            accept()
            awaitText(message)
            FoodStore(context).use { assertEquals(saved, it.myProductByBarcode(code)) }
        }
        assertEquals(1, server.requests.count { it == "PUT" })
        assertEquals(5, server.requests.count { it == "POST" })
    }

    @Test fun restoringDuringSubmissionDoesNotPostAgainOrKeepConsent() = fixture { server ->
        val restored = StateRestorationTester(ui)
        val saved = save(card.copy(energy = "230"))
        restored.setContent { DiaryTheme(ThemeMode.DARK) { MyProductsScreen(listOf(saved), {}, {}, {}) } }
        openCard(saved.name)
        awaitAction()
        openDialog()
        ui.onNodeWithText("Калорийность расходится", substring = true).assertIsDisplayed()
        val release = CountDownLatch(1)
        server.onPost = { release.await() }
        try {
            accept()
            ui.awaitUntil(5_000) { server.requests.contains("POST") }
            ui.onNodeWithText("Отправить").assertIsNotEnabled()
            restored.emulateSavedInstanceStateRestore()
            awaitText(consent)
            ui.onNodeWithText("Отправить").assertIsNotEnabled()
            assertEquals(1, server.requests.count { it == "POST" })
        } finally {
            release.countDown()
        }
        awaitText("Отправлено")
        assertEquals(1, server.requests.count { it == "POST" })
    }

    @Test fun darkScreenshot() = screenshot("off-submit-dark.png", ThemeMode.DARK, card)
    @Test fun lightScreenshot() = screenshot("off-submit-light.png", ThemeMode.LIGHT, card)
    @Test fun warningScreenshot() = screenshot("off-submit-warning-dark.png", ThemeMode.DARK, card.copy(energy = "230"))

    private fun screenshot(filename: String, mode: ThemeMode, product: MyProduct) = fixture {
        val saved = save(product)
        val shot = ui.setScreenshotContent(mode) { MyProductsScreen(listOf(saved), {}, {}, {}) }
        openCard(saved.name)
        awaitAction()
        openDialog()
        shot.captureScreen(filename, includeDialog = true)
        Unit
    }

    private fun save(product: MyProduct) = FoodStore(context).use {
        it.saveMyProduct(product)
        it.myProducts().single()
    }

    private fun show(): MyProduct {
        val saved = save(card)
        ui.setScreenshotContent { MyProductsScreen(listOf(saved), {}, {}, {}) }
        openCard(saved.name)
        return saved
    }

    private fun openCard(name: String) = ui.onNodeWithText(name).performClick()
    private fun openDialog() = ui.onNodeWithText(action).performClick()
    private fun accept() {
        ui.onNodeWithText(consent).performClick()
        ui.onNodeWithText("Отправить").assertIsEnabled().performClick()
    }

    private fun awaitAction() = awaitText(action)
    private fun awaitText(text: String) = ui.awaitUntil(5_000) {
        ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun fixture(enabled: Boolean = true, check: (SubmissionServer) -> Unit) {
        SubmissionServer(enabled).use { server ->
            ProductServerSettings(context).apply {
                setAddress(server.address)
                setKey("off-test-key")
            }
            check(server)
        }
    }

    private class SubmissionServer(enabled: Boolean) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val address = "http://127.0.0.1:${server.address.port}"
        val requests = CopyOnWriteArrayList<String>()
        @Volatile var putStatus = 200
        @Volatile var postStatus = 200
        @Volatile var onPut: () -> Unit = {}
        @Volatile var onPost: () -> Unit = {}

        init {
            server.createContext("/") { request ->
                request.use {
                    assertEquals("off-test-key", it.requestHeaders.getFirst("X-Api-Key"))
                    requests += it.requestMethod
                    when (it.requestMethod) {
                        "GET" -> {
                            assertEquals("/off-submissions/status", it.requestURI.path)
                            val body = """{"enabled":$enabled,"test":true}""".toByteArray()
                            it.sendResponseHeaders(200, body.size.toLong())
                            it.responseBody.write(body)
                        }
                        "PUT" -> {
                            assertEquals("/products/4600000000015", it.requestURI.path)
                            it.requestBody.readBytes()
                            onPut()
                            it.sendResponseHeaders(putStatus, -1)
                        }
                        "POST" -> {
                            assertEquals("/products/4600000000015/off-submission", it.requestURI.path)
                            onPost()
                            when (postStatus) {
                                409 -> {
                                    val body = """{"title":"Товар уже есть в Open Food Facts"}""".toByteArray()
                                    it.sendResponseHeaders(409, body.size.toLong())
                                    it.responseBody.write(body)
                                }
                                429 -> {
                                    it.responseHeaders.set("Retry-After", "30")
                                    it.sendResponseHeaders(503, -1)
                                }
                                in 200..599 -> it.sendResponseHeaders(postStatus, -1)
                            }
                        }
                    }
                }
            }
            server.start()
        }

        override fun close() = server.stop(0)
    }
}
