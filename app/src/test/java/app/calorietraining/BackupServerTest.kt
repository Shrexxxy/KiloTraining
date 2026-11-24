package app.calorietraining

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.StringWriter
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val Id = "ccd94ec1-a3db-49b7-a8bd-2c8a1d11503d"
private const val OtherId = "6639f3cd-9af1-47fa-bd13-96b93a050495"
private const val Key = "backup-test-key"
private const val Payload = """ {"format":"calorietraining/14","note":"Гречка"} """
private const val Metadata = """{"id":"$Id","createdAt":"2026-11-22T12:15:00Z","sizeBytes":97,"format":"calorietraining/14"}"""
private const val OtherMetadata = """{"id":"$OtherId","createdAt":"2026-11-21T10:00:00Z","sizeBytes":192,"format":"calorietraining/14"}"""

private class BackupHttpFixture : AutoCloseable {
    val uploaded = AtomicReference<String>()
    val key = AtomicReference<String>()
    val download = AtomicReference(Payload)
    val posts = AtomicInteger()
    val status = AtomicInteger(200)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val address = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/backups") { request ->
            request.use {
                key.set(it.requestHeaders.getFirst("X-Api-Key"))
                val post = it.requestMethod == "POST"
                if (post) {
                    uploaded.set(it.requestBody.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() })
                    posts.incrementAndGet()
                }
                val result = when {
                    post -> Metadata
                    it.requestURI.path == "/backups" -> "[$Metadata,$OtherMetadata]"
                    else -> download.get()
                }.toByteArray(Charsets.UTF_8)
                val code = status.get()
                it.responseHeaders.set("Content-Type", "application/json")
                it.sendResponseHeaders(if (post && code == 200) 201 else code, result.size.toLong())
                it.responseBody.write(result)
            }
        }
        server.start()
    }

    fun configure(context: Context) {
        context.getSharedPreferences("server", Context.MODE_PRIVATE).edit()
            .putString("address", address).putString("key", Key).commit()
    }

    override fun close() = server.stop(0)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupServerTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun sendsOriginalJsonAndKeyAndReadsBackups() {
        BackupHttpFixture().use { fixture ->
            val client = HttpBackupServer(fixture.address, Key)
            runBlocking {
                assertEquals(Id, client.uploadBackup(Payload).id.toString())
                assertEquals(Key, fixture.key.get())
                assertEquals(Payload, fixture.uploaded.get())
                val list = client.listBackups()
                assertEquals(listOf(Id, OtherId), list.map { it.id.toString() })
                assertEquals(Instant.parse("2026-11-22T12:15:00Z"), list.first().createdAt)
                assertEquals(97, list.first().sizeBytes)
                assertEquals("calorietraining/14", list.first().format)
                assertEquals(Payload, client.downloadBackup(list.first().id))

                fixture.status.set(401)
                val error = assertThrows(ProductServerException::class.java) {
                    runBlocking { client.listBackups() }
                }
                assertEquals(ServerFailure.UNAUTHORIZED, error.failure)
                assertEquals("Неверный ключ", backupServerMessage(error))
            }
        }
    }

    @Test fun autoBackupRunsWhenDueAndRecordsOnlySuccess() {
        BackupHttpFixture().use { fixture ->
            val now = Instant.parse("2026-11-23T12:00:00Z").toEpochMilli()
            val preferences = context.getSharedPreferences("server", Context.MODE_PRIVATE)
            fun previous(hours: Long) = now - hours * 3_600_000
            fun setPrevious(hours: Long) {
                preferences.edit().putLong("lastAutoBackup", previous(hours)).commit()
            }
            fun runAuto() = runBlocking { autoBackupDiary(context, now) }
            fun recorded() = preferences.getLong("lastAutoBackup", 0)

            setPrevious(25)
            runAuto()
            assertEquals(0, fixture.posts.get())

            fixture.configure(context)
            setPrevious(2)
            runAuto()
            assertEquals(0, fixture.posts.get())
            assertEquals(previous(2), recorded())

            setPrevious(25)
            fixture.status.set(500)
            runAuto()
            assertEquals(1, fixture.posts.get())
            assertEquals(previous(25), recorded())

            fixture.status.set(200)
            runAuto()
            assertEquals(2, fixture.posts.get())
            assertTrue(fixture.uploaded.get().contains("calorietraining/"))
            assertEquals(now, recorded())
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h900dp")
class BackupServerScreenTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun listsTwoCopiesAndUploadsFromDarkScreen() {
        BackupHttpFixture().use { fixture ->
            fixture.configure(context)
            val screen = ui.setScreenshotContent(ThemeMode.DARK) {
                BackupScreen(androidx.lifecycle.viewmodel.compose.viewModel { BackupViewModel(context) }, {})
            }
            backupRow().assertIsDisplayed()
            awaitText("21.11.2026", substring = true)
            ui.onNodeWithText("На сервере").assertIsDisplayed()
            screen.captureScreen("backup-server-dark.png")
            ui.onNodeWithText("Сохранить на сервер").performClick()
            awaitText("Копия сохранена на сервере")
            assertEquals(1, fixture.posts.get())
        }
    }

    @Test fun previewsAndRestoresDownloadedCopyAndRejectsCorruption() {
        BackupHttpFixture().use { fixture ->
            fixture.configure(context)
            val day = LocalDate.of(2026, 11, 22)
            val path = File(context.cacheDir, "backup-test-source.db")
            fixture.download.set(FoodStore(context, path.absolutePath).use { source ->
                source.save(day, FoodDraft(name = "Гречка", energy = "100"))
                StringWriter().also(source::exportDiary).toString()
            })
            FoodStore(context).use { it.save(day, FoodDraft(name = "Йогурт", energy = "120")) }
            ui.setContent {
                DiaryTheme(ThemeMode.DARK) {
                    BackupScreen(androidx.lifecycle.viewmodel.compose.viewModel { BackupViewModel(context) }, {})
                }
            }

            backupRow().performClick()
            awaitText("Записей питания: 1", substring = true)
            FoodStore(context).use { assertEquals("Йогурт", it.entries(day).single().name) }
            ui.onNode(hasText("Заменить", substring = true) and hasClickAction()).performClick()
            awaitText("Дневник восстановлен")
            FoodStore(context).use { assertEquals("Гречка", it.entries(day).single().name) }

            fixture.download.set("{")
            backupRow().performClick()
            awaitText("Копия повреждена")
            FoodStore(context).use { assertEquals("Гречка", it.entries(day).single().name) }
        }
    }

    private fun backupRow(): SemanticsNodeInteraction {
        val row = hasText("22.11.2026", substring = true) and hasClickAction()
        ui.awaitUntil(5_000) { ui.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        return ui.onNode(row)
    }

    private fun awaitText(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        try {
            ui.awaitUntil(5_000) { ui.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Не найдено «$text».\n${ui.onRoot().printToString()}", error)
        }
        return ui.onNodeWithText(text, substring)
    }
}
