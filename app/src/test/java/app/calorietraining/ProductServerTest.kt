package app.calorietraining

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductServerTest {
    @Test fun parsesCardSendsKeyAndDistinguishesFailures() {
        val code = "4600000000017"
        val key = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/products/") { request ->
            key.set(request.requestHeaders.getFirst("X-Api-Key"))
            val status = when (request.requestURI.path.substringAfterLast('/')) {
                code -> 200
                "00000000" -> 404
                else -> 401
            }
            val body = """{"id":"ccd94ec1-a3db-49b7-a8bd-2c8a1d11503d","barcode":"$code",
                "name":"Сырок","brand":null,"kcal":410.5,"protein":null,"fat":12,"carbs":0,
                "source":"openfoodfacts"}""".toByteArray()
            request.sendResponseHeaders(status, if (status == 200) body.size.toLong() else -1)
            request.use { if (status == 200) it.responseBody.write(body) }
        }
        server.start()
        val client = HttpProductServer("http://127.0.0.1:${server.address.port}", "test-key")
        try {
            runBlocking {
                val card = requireNotNull(client.byBarcode(code))
                assertEquals(code, card.barcode)
                assertEquals("Сырок", card.name)
                assertEquals(410.5, card.kcal, 0.0)
                assertNull(card.protein)
                assertEquals(12.0, requireNotNull(card.fat), 0.0)
                assertEquals(0.0, requireNotNull(card.carbs), 0.0)
                assertEquals("openfoodfacts", card.source)
                assertEquals("test-key", key.get())
                assertNull(client.byBarcode("00000000"))
            }
            assertEquals(ServerFailure.UNAUTHORIZED, assertThrows(ProductServerException::class.java) {
                runBlocking { client.byBarcode("11111111") }
            }.failure)
        } finally {
            server.stop(0)
        }
        assertEquals(ServerFailure.OFFLINE, assertThrows(ProductServerException::class.java) {
            runBlocking { client.byBarcode(code) }
        }.failure)
    }
}

internal open class FakeProductServer : ProductServer {
    override suspend fun health() = true
    override suspend fun byBarcode(code: String): ServerProduct? = null
    override suspend fun search(query: String, limit: Int): List<ServerProduct> = emptyList()
    override suspend fun put(card: ServerProduct) = Unit
    override suspend fun post(card: ServerProduct) = Unit
}
