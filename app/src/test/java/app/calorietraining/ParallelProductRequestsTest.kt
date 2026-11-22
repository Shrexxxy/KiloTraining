package app.calorietraining

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParallelProductRequestsTest {
    private val application get() = RuntimeEnvironment.getApplication()
    private val code = "4600000000017"
    private val catalogProduct = Product("Каталог", "50")
    private val serverProduct = ServerProduct("Из базы", 123.0, code)

    @Test fun scanReturnsCatalogWhileServerIsOffline() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val server = barcodeServer {
            delay(3_000)
            throw ProductServerException(ServerFailure.OFFLINE)
        }
        FoodStore(application).use { store ->
            val result = store.lookup(code, server, dispatcher) {
                delay(200)
                catalogProduct
            }
            assertEquals(ScanOutcome.CATALOG, result.outcome)
            assertTrue("Время ответа: ${currentTime} мс", currentTime < 1_300)
        }
    }

    @Test fun scanCancelsCatalogWhenServerFindsCard() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val server = barcodeServer {
            delay(300)
            serverProduct
        }
        var cancelled = false
        FoodStore(application).use { store ->
            val result = store.lookup(code, server, dispatcher) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            assertEquals(ScanOutcome.SERVER, result.outcome)
            assertEquals(300L, currentTime)
            assertTrue(cancelled)
        }
    }

    @Test fun scanPrefersServerBothBeforeAndWithinOneSecondAfterCatalog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        FoodStore(application).use { store ->
            for (serverTime in listOf(100L, 700L)) {
                val startedAt = currentTime
                val server = barcodeServer {
                    delay(serverTime)
                    serverProduct
                }
                var catalogStartedAt: Long? = null
                val result = store.lookup(code, server, dispatcher) {
                    catalogStartedAt = currentTime
                    delay(200)
                    catalogProduct
                }
                assertEquals(ScanOutcome.SERVER, result.outcome)
                assertEquals(serverTime, currentTime - startedAt)
                assertEquals(startedAt, catalogStartedAt)
            }
        }
    }

    @Test fun searchPublishesCatalogBeforeServerResponds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val server = object : FakeProductServer() {
            override suspend fun search(query: String, limit: Int): List<ServerProduct> {
                delay(3_000)
                return listOf(serverProduct)
            }
        }
        val catalogDraft = FoodDraft(name = "Из каталога", energy = "50")
        val model = FoodViewModel(application, dispatcher, { server }) {
            delay(200)
            listOf(catalogDraft)
        }
        try {
            runCurrent()
            model.onEvent(FoodEvent.BrowseFoods)
            runCurrent()
            model.onEvent(FoodEvent.FindFood("сыр"))
            advanceTimeBy(200)
            runCurrent()
            assertEquals(CatalogSearch.Results("сыр", listOf(catalogDraft)), model.uiState.value.online)
            advanceTimeBy(2_800)
            runCurrent()
            assertEquals(CatalogSearch.Results("сыр", listOf(catalogDraft), listOf(serverProduct.toDraft())),
                model.uiState.value.online)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun interruptedSyncClearsPendingOnlyForSuccessfullySentCards() = runTest {
        for (failure in listOf(ServerFailure.OFFLINE, ServerFailure.UNAUTHORIZED)) {
            FoodStore(application, "${failure.name}.db").use { store ->
                val names = listOf("Первая", "Вторая", "Третья")
                names.forEach { store.saveMyProduct(MyProduct(name = it, energy = "50")) }
                val server = object : FakeProductServer() {
                    override suspend fun post(card: ServerProduct) {
                        if (card.name == names.last()) throw ProductServerException(failure)
                    }
                }
                assertEquals(ProductSyncReport(sent = 2, failure = failure), store.syncProducts(server))
                val flags = store.readableDatabase.rawQuery(
                    "SELECT pending_sync FROM my_product ORDER BY id", null
                ).use { rows ->
                    buildList { while (rows.moveToNext()) add(rows.getInt(0)) }
                }
                assertEquals(listOf(0, 0, 1), flags)
            }
        }
    }

    private fun barcodeServer(fetch: suspend () -> ServerProduct?) = object : FakeProductServer() {
        override suspend fun byBarcode(code: String): ServerProduct? = fetch()
    }
}
