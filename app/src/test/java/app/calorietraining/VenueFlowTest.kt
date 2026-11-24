package app.calorietraining

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.ShadowPicker
import java.io.File
import java.io.StringWriter
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp", shadows = [WindowsVenueFileProvider::class])
class VenueFlowTest {
    @get:Rule(order = 0) val settings = object : TestWatcher() {
        override fun starting(description: Description) {
            val context = RuntimeEnvironment.getApplication()
            OnboardingSettings(context).skipped = true
            ThemeSettings(context).mode = ThemeMode.DARK
        }
    }

    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    private val day = LocalDate.now()
    private val context get() = ui.activity.application

    @Test fun oneVenueForBothSpellings() {
        openNutrition()
        saveDish("Шаурма", "Chicken Dener")
        saveDish("Лаваш", "chicken dener")
        FoodStore(context).use { store ->
            val db = store.readableDatabase
            assertEquals(listOf("Chicken Dener"), db.values("SELECT name FROM venue"))
            val links = db.values("SELECT venue_id FROM food ORDER BY id")
            assertEquals(2, links.size)
            assertNotNull(links.first())
            assertEquals(links.first(), links.last())
        }
    }

    @Test fun venueShowsUniqueDishesWithMostRecentNutrition() {
        openNutrition()
        saveDish("Шаурма", energy = "400", macros = Macros("20", "15", "40"))
        saveDish("Шаурма", energy = "550", macros = Macros("32", "24", "51"))
        saveDish("Лаваш")
        openVenue()
        ui.onAllNodesWithText("Шаурма").assertCountEquals(1)
        ui.onAllNodesWithText("Лаваш").assertCountEquals(1)
        node("Шаурма").performClick()
        waitFor("Новая запись")
        listOf("550", "32", "24", "51").forEach { node(it).assertExists() }
    }

    @Test fun backupRetainsVenueLinkAndOmitsBothMediaPaths() {
        openNutrition()
        saveDish("Шаурма", withPhoto = true)
        FoodStore(context).use { source ->
            val photoPath = source.readableDatabase.values("SELECT photo_path FROM food").single()!!
            val logo = File(context.filesDir, "photos/test-logo.jpg")
            File(context.filesDir, "photos").mkdirs()
            photoFile(photoPath).copyTo(logo)
            source.writableDatabase.execSQL("UPDATE venue SET logo_path=?", arrayOf(logo.absolutePath))
            val exported = StringWriter().also(source::exportDiary).toString()
            val json = JSONObject(exported)
            assertEquals("calorietraining/18", json.getString("format"))
            for (excluded in listOf("photo_path", "logo_path", File(photoPath).name, logo.name)) {
                assertFalse(exported.contains(excluded))
            }
            FoodStore(context, "staged-venues.db").use { staged ->
                staged.readBackup(exported.reader())
                FoodStore(context, "restored-venues.db").use { restored ->
                    restored.replaceFrom(staged)
                    val db = restored.readableDatabase
                    assertEquals(listOf("Chicken Dener"), db.values(
                        "SELECT venue.name FROM food JOIN venue ON venue.id = food.venue_id"))
                    assertArrayEquals(File(photoPath).readBytes(), File(db.values("SELECT photo_path FROM food").single()!!).readBytes())
                    assertArrayEquals(logo.readBytes(), File(db.values("SELECT logo_path FROM venue").single()!!).readBytes())
                }
            }
        }
    }

    @Test fun cameraPhotoIsPreviewedAndResizedWhenSaved() {
        openNutrition()
        saveDish("Шаурма", withPhoto = true)
        FoodStore(context).use { store ->
            val path = store.readableDatabase.values("SELECT photo_path FROM food").single()!!
            val file = photoFile(path)
            assertEquals(File(context.filesDir, "photos").canonicalFile, file.canonicalFile.parentFile)
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, size)
            assertEquals(1280, size.outWidth)
            assertEquals(640, size.outHeight)
            assertEquals("image/jpeg", size.outMimeType)
        }
    }

    @Test fun sharedPhotoSurvivesUntilLastEntryIsDeleted() {
        openNutrition()
        saveDish("Шаурма", withPhoto = true)
        openVenue()
        node("Шаурма").performClick()
        saveEditor()
        FoodStore(context).use { store ->
            val paths = store.readableDatabase.values("SELECT photo_path FROM food ORDER BY id")
            assertEquals(2, paths.size)
            assertNotNull(paths.first())
            assertEquals(paths.first(), paths.last())
            val photo = photoFile(paths.first()!!)
            val (first, second) = store.entries(day)
            store.delete(day, first.id)
            assertTrue(photo.isFile)
            store.delete(day, second.id)
            assertTrue(store.entries(day).isEmpty())
            if (photo.exists()) throw AssertionError("Общий файл остался после удаления обеих записей",
                org.robolectric.shadows.ShadowLog.getLogsForTag("VenuePhotos").lastOrNull()?.throwable)
        }
    }

    @Test fun choosingVenueDishPrefillsLunchAndShowsVenueInMeals() {
        openNutrition()
        saveDish("Шаурма")
        openVenue()
        node("Шаурма").performClick()
        waitFor("Новая запись")
        node("Chicken Dener").assertExists()
        ui.onNode(hasText("Обед") and isSelected()).assertExists()
        saveEditor()
        ui.mealList().performScrollToNode(hasText("Chicken Dener ·", substring = true))
        ui.onAllNodesWithText("Chicken Dener ·", substring = true).assertCountEquals(2)
    }

    @Test fun capturesFourDarkScreensForReview() {
        openNutrition()
        saveDish("Шаурма", withPhoto = true, macros = Macros("32", "24", "51"))
        ui.mealList().performScrollToNode(hasText("Chicken Dener ·", substring = true))
        waitForPhotoThumbnail()
        capture("meals-entry-photo-dark")
        openVenue(review = true)
        node("Шаурма").performClick()
        waitFor("Новая запись")
        waitForPhotoThumbnail()
        capture("food-editor-venue-dark")
    }

    private fun openNutrition() {
        ui.onNode(hasText("Питание") and isSelectable()).performClick()
        waitFor("Завтрак")
    }

    private fun openVenue(review: Boolean = false) {
        ui.addInMeal(Meal.LUNCH)
        node("Заведения").performClick()
        waitFor("Chicken Dener")
        if (review) capture("venues-tab-dark")
        node("Chicken Dener").performClick()
        waitFor("Шаурма")
        if (review) {
            waitForPhotoThumbnail()
            capture("venue-screen-dark")
        }
    }

    private fun saveDish(
        name: String, venue: String = "Chicken Dener", energy: String = "500",
        macros: Macros = Macros(), withPhoto: Boolean = false
    ) {
        ui.addInMeal(Meal.LUNCH)
        node("Новый продукт").performClick()
        waitFor("Новая запись")
        enter("Название", name)
        enter("Заведение", venue)
        if (withPhoto) takePhoto()
        enter("Время еды", "15:30")
        enter("Ккал на порцию", energy)
        if (macros.protein.isNotBlank()) enter("Белки, г", macros.protein)
        if (macros.fat.isNotBlank()) enter("Жиры, г", macros.fat)
        if (macros.carbs.isNotBlank()) enter("Углеводы, г", macros.carbs)
        saveEditor()
    }

    private fun saveEditor() {
        node("Сохранить").performClick()
        ui.awaitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun takePhoto() {
        node("Фото блюда").performScrollTo().performClick()
        ui.waitForIdle()
        val started = shadowOf(ui.activity).nextStartedActivityForResult
            ?: throw AssertionError("Камера не запустилась", org.robolectric.shadows.ShadowLog
                .getLogsForTag("VenueCamera").lastOrNull()?.throwable)
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, started.intent.action)
        val output = requireNotNull(started.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java))
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFB86530.toInt())
        assertEquals("content", output.scheme)
        assertEquals("${context.packageName}.venue-camera", output.authority)
        assertTrue(started.intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        val stream = if (File.separatorChar == '\\') {
            File(File(context.cacheDir, "venue-camera"), requireNotNull(output.lastPathSegment)).outputStream()
        } else requireNotNull(context.contentResolver.openOutputStream(output))
        stream.use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
        }
        bitmap.recycle()
        ui.runOnUiThread {
            ui.activity.activityResultRegistry.dispatchResult(started.requestCode, Activity.RESULT_OK, null)
        }
        ui.awaitUntil(10_000) {
            ui.onAllNodesWithContentDescription("Фото блюда").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithText("Не удалось прочитать или сохранить фото").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithText("Не удалось прочитать или сохранить фото").fetchSemanticsNodes().isNotEmpty()) {
            throw AssertionError("Не удалось обработать тестовое фото", org.robolectric.shadows.ShadowLog
                .getLogsForTag("VenueCamera").lastOrNull()?.throwable)
        }
        ui.onNodeWithContentDescription("Убрать фото").assertExists()
    }

    private fun waitForPhotoThumbnail() = ui.awaitUntil(10_000) {
        ui.onAllNodesWithContentDescription("Фото блюда").fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String) {
        // Активность сама рисует тему и общий фон.
        ScreenCapture(ui) { ui.activity.window.decorView }.captureScreen("$name.png").recycle()
    }

    private fun photoFile(path: String) =
        File(path).takeIf { it.isAbsolute } ?: File(context.filesDir, "photos/${File(path).name}")

    private fun enter(label: String, value: String) {
        ui.onNode(hasText(label, substring = true) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }

    private fun node(label: String) = ui.onNodeWithText(label)

    private fun waitFor(label: String) = ui.awaitUntil(10_000) {
        ui.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }
}

// AndroidX проверяет границу пути через '/', а Robolectric получает пути файловой системы Windows.
@Implements(value = FileProvider::class, isInAndroidSdk = false, shadowPicker = WindowsVenueFileProvider.Picker::class)
class WindowsVenueFileProvider {
    class Picker : ShadowPicker<WindowsVenueFileProvider> {
        override fun pickShadowClass() = if (File.separatorChar == '\\') WindowsVenueFileProvider::class.java else null
    }

    companion object {
        @JvmStatic
        @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            require(file.canonicalFile.parentFile == File(context.cacheDir, "venue-camera").canonicalFile)
            return Uri.Builder().scheme("content").authority(authority)
                .appendPath("venue-camera").appendPath(file.name).build()
        }
    }
}

private fun SQLiteDatabase.values(sql: String): List<String?> = rawQuery(sql, null).use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
}
