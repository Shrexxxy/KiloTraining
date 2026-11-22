package app.calorietraining

import java.io.StringWriter
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductSyncTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val code = "4600000000017"
    private val cheese = MyProduct(name = "Сырок", barcode = code, energy = "410,5",
        macros = Macros(protein = "5,5", fat = "101"))
    private val milk = MyProduct(name = "Молоко", energy = "50")
    private val serving = MyProduct(name = "Кофе", weighted = false, energy = "80")

    @Test fun lookupPrefersLocalThenServerThenCatalog() = FoodStore(context).use { store ->
        var calls = 0
        val server = object : FakeProductServer() {
            override suspend fun byBarcode(code: String): ServerProduct {
                calls++
                if (code == "00000000") throw ProductServerException(ServerFailure.OFFLINE)
                return ServerProduct(name = "Из базы", barcode = code, kcal = 123.0, source = "openfoodfacts")
            }
        }
        runBlocking {
            store.saveMyProduct(cheese)
            assertEquals(ScanOutcome.PHONE, store.lookup(code, server) { error("Вызван каталог") }.outcome)
            assertEquals(0, calls)
            val found = store.lookup("11111111", server) { Product("Каталог", "50") }
            assertEquals(ScanOutcome.SERVER, found.outcome)
            assertEquals("Найдено в вашей базе", found.hint)
            val draft = found.draft.copy(grams = "100")
            store.save(LocalDate.of(2026, 11, 20), draft)
            assertEquals("openfoodfacts", store.myProductByBarcode("11111111")?.source)
            store.save(LocalDate.of(2026, 11, 20), draft.copy(energy = "124"))
            assertEquals("manual", store.myProductByBarcode("11111111")?.source)
            assertEquals(ScanOutcome.CATALOG, store.lookup("00000000", server) { Product("Каталог", "50") }.outcome)
            assertEquals(2, calls)
        }
    }

    @Test fun uploadsWeightedCardsOnlyOnce() = FoodStore(context).use { store ->
        val server = RecordingServer()
        listOf(cheese, milk, serving).forEach(store::saveMyProduct)
        assertEquals(3, store.pendingSyncCount())
        runBlocking {
            store.syncProducts(server)
            assertEquals(0, store.pendingSyncCount())
            store.syncProducts(server)
        }
        assertEquals(listOf("PUT", "POST").sorted(), server.requests.map { it.first }.sorted())
        val uploadedCheese = server.requests.single { it.first == "PUT" }.second
        assertEquals(code, uploadedCheese.barcode)
        assertEquals(410.5, uploadedCheese.kcal, 0.0)
        assertEquals(5.5, requireNotNull(uploadedCheese.protein), 0.0)
        assertNull(uploadedCheese.fat)
        assertNull(uploadedCheese.carbs)
        val uploadedMilk = server.requests.single { it.first == "POST" }.second
        assertNull(uploadedMilk.barcode)
        assertEquals(50.0, uploadedMilk.kcal, 0.0)
    }

    @Test fun unsuitableCaloriesStayPendingWhileOtherCardsUpload() = FoodStore(context).use { store ->
        val server = RecordingServer()
        listOf(cheese.copy(energy = "901"), MyProduct(name = "Без ккал"), milk).forEach(store::saveMyProduct)
        val report = runBlocking { store.syncProducts(server) }
        assertEquals(2, report.unsuitable)
        assertEquals(2, store.pendingSyncCount())
        assertEquals("Молоко", server.requests.single().second.name)
    }

    @Test fun unauthorizedAndOfflineStopAtFirstRequest() = FoodStore(context).use { store ->
        listOf(cheese, milk).forEach(store::saveMyProduct)
        for (failure in listOf(ServerFailure.UNAUTHORIZED, ServerFailure.OFFLINE)) {
            val server = RecordingServer { failure }
            assertEquals(failure, runBlocking { store.syncProducts(server) }.failure)
            assertEquals(1, server.requests.size)
            assertEquals(2, store.pendingSyncCount())
        }
    }

    @Test fun invalidCardDoesNotBlockOtherUploads() = FoodStore(context).use { store ->
        listOf(cheese, milk).forEach(store::saveMyProduct)
        val server = RecordingServer { if (it.barcode != null) ServerFailure.INVALID_DATA else null }
        runBlocking { store.syncProducts(server) }
        assertEquals(setOf("PUT", "POST"), server.requests.map { it.first }.toSet())
        assertEquals(1, store.pendingSyncCount())
    }

    @Test fun version14CardsAndRestoredBackupArePending() {
        context.openOrCreateDatabase("upgrade14.db", 0, null).use { db ->
            db.execSQL("""CREATE TABLE my_product (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL, brand TEXT NOT NULL, barcode TEXT,
                weighted INTEGER NOT NULL, energy TEXT NOT NULL, protein TEXT NOT NULL, fat TEXT NOT NULL,
                carbs TEXT NOT NULL, source TEXT NOT NULL, search_key TEXT NOT NULL, updated_at TEXT NOT NULL)""")
            db.execSQL("""INSERT INTO my_product VALUES
                (1,'Сырок','',?,1,'410','','','','manual','сырок','2026-11-20T00:00:00Z')""", arrayOf(code))
            db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY, day TEXT, title TEXT, minutes INTEGER)")
            db.execSQL("CREATE TABLE lifting(id INTEGER PRIMARY KEY, session INTEGER, exercise_group TEXT, exercise TEXT, weight TEXT, reps INTEGER, planned INTEGER, warmup INTEGER)")
            db.execSQL("CREATE TABLE goal(start_day TEXT PRIMARY KEY, kcal INTEGER, protein INTEGER, fat INTEGER, carbs INTEGER)")
            db.version = 14
        }
        FoodStore(context, "upgrade14.db").use { store ->
            assertEquals(17, store.readableDatabase.version)
            assertEquals(1, store.pendingSyncCount())
            assertEquals("Сырок", store.myProductByBarcode(code)?.name)
        }

        val backup = StringWriter()
        FoodStore(context).use { store ->
            store.saveMyProduct(cheese)
            runBlocking { store.syncProducts(RecordingServer()) }
            assertEquals(0, store.pendingSyncCount())
            store.exportDiary(backup)
        }
        assertTrue(backup.toString().contains("calorietraining/15"))
        assertFalse(backup.toString().contains("pending_sync"))
        FoodStore(context, "restored.db").use { store ->
            store.readBackup(backup.toString().reader())
            assertEquals(1, store.pendingSyncCount())
            assertEquals(cheese.name, store.myProductByBarcode(code)?.name)
        }
    }

    private class RecordingServer(private val fail: (ServerProduct) -> ServerFailure? = { null }) : FakeProductServer() {
        val requests = mutableListOf<Pair<String, ServerProduct>>()

        override suspend fun put(card: ServerProduct) = record("PUT", card)
        override suspend fun post(card: ServerProduct) = record("POST", card)

        private fun record(method: String, card: ServerProduct) {
            requests += method to card
            fail(card)?.let { throw ProductServerException(it) }
        }
    }
}
