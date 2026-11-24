package app.calorietraining

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
class PhotoBackupScreenTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 23)
    private val metadata = """{"id":"73f6e8d1-5334-4f9e-bac8-bef40b421b61","createdAt":"2026-11-22T12:15:00Z","sizeBytes":97,"format":"calorietraining/18"}"""

    @Test fun uploadsLegacyBytesOnce() = Fixture().use { server ->
        val photo = localPhoto(1)
        FoodStore(app).use {
            it.save(day, FoodDraft(name = "Суп", energy = "100", photoPath = photo.path))
            it.writableDatabase.execSQL("UPDATE food SET photo_hash = NULL")
        }
        show()
        upload()
        awaitText("Копия сохранена на сервере")
        assertArrayEquals(photo.readBytes(), server.stored[hash(photo.readBytes())])
        assertTrue(server.backup.contains(hash(photo.readBytes())))
        upload()
        ui.awaitUntil(5_000) { server.posts.get() == 2 && server.missing.get() == 2 }
        awaitText("Копия сохранена на сервере")
        assertEquals(1, server.puts.get())
    }

    @Test fun restoresFoodAndLogoWithExactBytes() = Fixture().use { server ->
        val food = jpeg(2)
        val logo = jpeg(3)
        server.backup = backup(food, logo)
        server.stored[hash(food)] = food
        server.stored[hash(logo)] = logo
        val model = BackupViewModel(app)
        val diaryRows = mutableStateOf<List<FoodEntry>?>(null)
        ui.setScreenshotContent(ThemeMode.DARK) {
            val rows = diaryRows.value
            if (rows == null) BackupScreen(model, {})
            else FoodContent(FoodUiState(day = day, rows = rows, loaded = true), {})
        }
        awaitText("22.11.2026", substring = true)
        model.prepare(Uri.fromFile(File(app.cacheDir, "backup-with-photos.json").apply { writeText(server.backup) }))
        confirmRestore()
        ui.awaitUntil(5_000) { foodPath() != null && logoPath() != null }
        assertArrayEquals(food, File(requireNotNull(foodPath())).readBytes())
        assertArrayEquals(logo, File(requireNotNull(logoPath())).readBytes())
        val rows = FoodStore(app).use { it.entries(day) }
        ui.runOnIdle { diaryRows.value = rows }
        awaitText("Суп")
        ui.awaitUntil(5_000) { ui.onNodeWithContentDescription("Фото блюда").isDisplayed() }
    }

    @Test fun restoresPhotosVenueAndMusclesOnCleanPhone() = Fixture().use { server ->
        val photo = localPhoto(5)
        val logo = localPhoto(6)
        val photoBytes = photo.readBytes()
        val logoBytes = logo.readBytes()
        val muscles = ExerciseTechnique(listOf(Muscle.Chest), listOf(Muscle.Triceps), emptyList())
        FoodStore(app).use { store ->
            store.save(day, FoodDraft(name = "Суп", energy = "100", venueName = "Кафе", photoPath = photo.path))
            store.setVenueLogo(store.venues().single().id, logo.path)
            store.saveCustomMuscles("Мой жим", muscles.primary, muscles.secondary)
        }
        show()
        upload()
        awaitText("Копия сохранена на сервере")
        assertTrue(app.deleteDatabase("food.db"))
        assertTrue(app.photosDirectory.deleteRecursively())
        FoodStore(app).use {
            assertTrue(it.entries(day).isEmpty())
            assertTrue(it.customMuscles().isEmpty())
        }
        restore()
        ui.awaitUntil(5_000) { foodPath() != null && logoPath() != null }
        assertArrayEquals(photoBytes, File(requireNotNull(foodPath())).readBytes())
        assertArrayEquals(logoBytes, File(requireNotNull(logoPath())).readBytes())
        FoodStore(app).use { store ->
            val entry = store.entries(day).single()
            assertEquals("Суп", entry.name)
            assertEquals("Кафе", entry.venueName)
            assertEquals(hash(photoBytes), entry.photoHash)
            assertEquals(mapOf("мой жим" to muscles), store.customMuscles())
        }
        assertEquals(2, server.gets.get())
    }

    @Test fun retriesAfterOfflineAndRejectsWrongBytes() = Fixture().use { server ->
        val food = jpeg(4)
        server.backup = backup(food)
        server.downloadStatus = 503
        val screen = show(ThemeMode.LIGHT)
        restore()
        awaitText("Докачать фото")
        screen.captureScreen("backup-photos-missing-light.png")
        assertNull(foodPath())
        assertTrue(app.getFileStreamPath("photos").listFiles().orEmpty().isEmpty())
        server.downloadStatus = 200
        server.stored[hash(food)] = jpeg(5)
        ui.onNodeWithText("Докачать фото").performClick()
        ui.awaitUntil(5_000) { server.gets.get() >= 2 }
        awaitText("Докачать фото")
        assertNull(foodPath())
        assertTrue(app.getFileStreamPath("photos").listFiles().orEmpty().isEmpty())
        server.stored[hash(food)] = food
        ui.onNodeWithText("Докачать фото").performClick()
        ui.awaitUntil(5_000) { foodPath() != null }
        assertArrayEquals(food, File(requireNotNull(foodPath())).readBytes())
    }

    @Test fun reusesReferencedLocalFile() = Fixture().use { server ->
        val file = localPhoto(6)
        val food = file.readBytes()
        server.backup = backup(food)
        FoodStore(app).use { it.save(day, FoodDraft(name = "Старый суп", energy = "100", photoPath = file.path)) }
        show()
        restore()
        assertEquals(file.canonicalPath, foodPath())
        assertArrayEquals(food, file.readBytes())
        assertEquals(0, server.gets.get())
    }

    @Test fun downloadLeavesUserReplacementIntact() = Fixture().use { server ->
        val food = jpeg(7)
        server.backup = backup(food)
        server.stored[hash(food)] = food
        val gate = CountDownLatch(1)
        server.downloadGate = gate
        try {
            show()
            restore()
            ui.awaitUntil(5_000) { server.gets.get() == 1 }
            val replacement = localPhoto(8)
            FoodStore(app).use {
                it.save(day, it.entries(day).single().edit().copy(photoPath = replacement.path))
            }
            gate.countDown()
            ui.awaitUntil(5_000) { ui.onAllNodesWithText("Фото: 0 из 1").fetchSemanticsNodes().isEmpty() }
            assertEquals(replacement.path, foodPath())
        } finally {
            gate.countDown()
        }
    }

    @Test fun reportsRemainingPhotosAfterSecondPutFails() = Fixture().use { server ->
        val gate = CountDownLatch(1)
        server.secondPutGate = gate
        try {
            FoodStore(app).use { store ->
                for (index in 1..3) store.save(day, FoodDraft(name = "Блюдо $index", energy = "100",
                    photoPath = localPhoto(index).path))
            }
            val screen = show()
            upload()
            awaitText("Фото: 1 из 3")
            screen.captureScreen("backup-photos-progress-dark.png")
            gate.countDown()
            awaitText("Копия сохранена на сервере. Фото отправлены не все: 2")
            assertEquals(1, server.posts.get())
            assertEquals(2, server.puts.get())
        } finally {
            gate.countDown()
        }
    }

    @Test fun showsAutoBackupPhotoFailureWhenOpeningBackupScreen() = Fixture().use { server ->
        val file = localPhoto(9)
        FoodStore(app).use { it.save(day, FoodDraft(name = "Суп", energy = "100", photoPath = file.path)) }
        server.uploadStatus = 503
        runBlocking { autoBackupDiary(app) }
        assertEquals(1, server.posts.get())
        assertEquals(1, server.puts.get())
        show()
        awaitText("Копия сохранена на сервере. Фото отправлены не все: 1")
        server.uploadStatus = 201
        upload()
        awaitText("Копия сохранена на сервере")
        assertArrayEquals(file.readBytes(), server.stored[hash(file.readBytes())])
    }

    private fun show(theme: ThemeMode = ThemeMode.DARK) = ui.setScreenshotContent(theme) {
        BackupScreen(androidx.lifecycle.viewmodel.compose.viewModel { BackupViewModel(app) }, {})
    }.also { awaitText("22.11.2026", substring = true) }

    private fun upload() = ui.onNodeWithText("Сохранить на сервер").performClick()

    private fun restore() {
        ui.onNode(hasText("22.11.2026", substring = true) and hasClickAction()).performClick()
        confirmRestore()
    }

    private fun confirmRestore() {
        awaitText("Записей питания: 1", substring = true)
        ui.onNode(hasText("Заменить", substring = true) and hasClickAction()).performClick()
        awaitText("Дневник восстановлен")
    }

    private fun awaitText(text: String, substring: Boolean = false) =
        ui.awaitUntil(5_000) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun foodPath() = FoodStore(app).use { it.entries(day).single().photoPath }
    private fun logoPath() = FoodStore(app).use { it.venues().single().logoPath }

    private fun backup(food: ByteArray, logo: ByteArray? = null): String {
        val path = File(app.cacheDir, "photos-source.db")
        return FoodStore(app, path.path).use { store ->
            store.save(day, FoodDraft(name = "Суп", energy = "100", venueName = "Кафе"))
            JSONObject(StringWriter().also(store::exportDiary).toString()).apply {
                put("format", "calorietraining/18")
                getJSONArray("food").getJSONObject(0).put("photo_hash", hash(food))
                getJSONArray("venue").getJSONObject(0).put("logo_hash", logo?.let(::hash) ?: JSONObject.NULL)
            }.toString()
        }
    }

    private fun localPhoto(number: Int) = File(app.filesDir, "photos/local-$number.jpg").apply {
        parentFile!!.mkdirs()
        writeBytes(jpeg(number))
    }

    private fun jpeg(number: Int): ByteArray = ByteArrayOutputStream().also { output ->
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff000000.toInt() or (number * 25 shl 16))
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
    }.toByteArray()

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private inner class Fixture : AutoCloseable {
        val stored = ConcurrentHashMap<String, ByteArray>()
        val posts = AtomicInteger()
        val puts = AtomicInteger()
        val gets = AtomicInteger()
        val missing = AtomicInteger()
        @Volatile var backup = ""
        @Volatile var downloadStatus = 200
        @Volatile var uploadStatus = 201
        var secondPutGate: CountDownLatch? = null
        var downloadGate: CountDownLatch? = null
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/backups") { exchange ->
                exchange.use {
                    when {
                        it.requestMethod == "POST" -> {
                            backup = it.requestBody.bufferedReader().use { reader -> reader.readText() }
                            posts.incrementAndGet()
                            it.reply(201, metadata.toByteArray())
                        }
                        it.requestURI.path == "/backups" -> it.reply(200, "[$metadata]".toByteArray())
                        else -> it.reply(200, backup.toByteArray())
                    }
                }
            }
            server.createContext("/photos") { exchange ->
                exchange.use {
                    val hash = it.requestURI.path.substringAfterLast('/')
                    when (it.requestMethod) {
                        "POST" -> {
                            val hashes = JSONObject(it.requestBody.bufferedReader().use { reader -> reader.readText() })
                                .getJSONArray("hashes")
                            val absent = (0 until hashes.length()).map(hashes::getString).filterNot(stored::containsKey)
                            missing.incrementAndGet()
                            it.reply(200, JSONArray(absent).toString().toByteArray())
                        }
                        "PUT" -> {
                            val number = puts.incrementAndGet()
                            val bytes = it.requestBody.readBytes()
                            if (number == 2 && secondPutGate != null) {
                                secondPutGate!!.await(10, TimeUnit.SECONDS)
                                it.reply(503)
                            } else {
                                if (uploadStatus == 201) stored[hash] = bytes
                                it.reply(uploadStatus)
                            }
                        }
                        else -> {
                            gets.incrementAndGet()
                            downloadGate?.await(10, TimeUnit.SECONDS)
                            val bytes = stored[hash]
                            when {
                                downloadStatus != 200 -> it.reply(downloadStatus)
                                bytes == null -> it.reply(404)
                                else -> it.reply(200, bytes)
                            }
                        }
                    }
                }
            }
            server.start()
            app.getSharedPreferences("server", Context.MODE_PRIVATE).edit()
                .putString("address", "http://127.0.0.1:${server.address.port}").putString("key", "photos-test-key").commit()
        }

        private fun HttpExchange.reply(status: Int, bytes: ByteArray? = null) {
            sendResponseHeaders(status, bytes?.size?.toLong() ?: -1)
            if (bytes != null) responseBody.write(bytes)
        }

        override fun close() = server.stop(0)
    }
}
