package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ExerciseMusclesUiTest {
    @get:Rule val ui = createComposeRule()
    private val row = "Тяга горизонтального блока"
    private val bench = "Жим штанги лёжа"
    private val icon = hasContentDescription("Мышцы и техника")
    private var selected: String? = null

    @Test fun catalogOpensDetailsAndReturnsWithoutSelecting() {
        showCatalogRow()
        ui.onNode(icon).performClick()
        ui.onNodeWithText(row).assertIsDisplayed()
        ui.onNodeWithContentDescription("Основные мышцы: середина спины", substring = true).assertIsDisplayed()
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNodeWithText("Каталог упражнений").assertIsDisplayed()
        ui.onNodeWithText(row).assertIsDisplayed()
        assertNull(selected)
    }

    @Test fun tappingCatalogRowSelectsExercise() {
        showCatalogRow()
        ui.onNodeWithText(row).performClick()
        ui.runOnIdle { assertEquals(row, selected) }
    }

    @Test fun workoutKeepsCatalogDetailsAndShowsCustomIcon() {
        val set = SetDraft(1, "bench", bench, "60", "8")
        val workout = Workout(1, listOf(set, set.copy(id = 2, group = "custom", exercise = "Мой жим")))
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})
            }
        }
        val list = ui.onNode(hasScrollToIndexAction())
        val customCard = hasTestTag("glass-card") and hasAnyDescendant(hasText("Мой жим"))
        list.performScrollToNode(customCard)
        ui.onNode(customCard).assertIsDisplayed()
        ui.onNode(hasContentDescription("Мышцы упражнения") and hasAnyAncestor(customCard)).assertIsDisplayed()
        list.performScrollToNode(icon)
        ui.onNode(icon).performClick()
        ui.onNodeWithText(bench).assertIsDisplayed()
        ui.onNodeWithText("Как делать").assertExists()
        ui.onNodeWithText("Сохранить").assertDoesNotExist()
        ui.onNodeWithText("грудь").assertDoesNotExist()
    }

    @Test fun catalogWinsOverExistingCustomMusclesAndBackupKeepsThem() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val name = "Жим Арнольда"
        val key = name.searchKey()
        val backup = FoodStore(app).use { store ->
            store.writableDatabase.execSQL(
                "INSERT INTO exercise_muscle(exercise_key, muscle, is_primary) VALUES (?, ?, 1)",
                arrayOf(key, "Chest"),
            )
            java.io.StringWriter().also(store::exportDiary).toString()
        }
        FoodStore(app, "catalog-collision-backup").use { restored ->
            restored.readBackup(backup.reader())
            val copy = org.json.JSONObject(java.io.StringWriter().also(restored::exportDiary).toString())
            assertEquals(key, copy.getJSONArray("exercise_muscle").getJSONObject(0).getString("exercise_key"))
            assertEquals("Chest", copy.getJSONArray("exercise_muscle").getJSONObject(0).getString("muscle"))
        }
        val workout = Workout(1, listOf(SetDraft(1, "arnold", name, "20", "8")))
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})
            }
        }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(icon)
        ui.onNode(icon).performClick()
        ui.onNodeWithContentDescription("Основные мышцы: плечи", substring = true).assertExists()
        FoodStore(app).use { store ->
            store.readableDatabase.rawQuery("SELECT muscle FROM exercise_muscle WHERE exercise_key=?", arrayOf(key)).use {
                assertTrue(it.moveToFirst())
                assertEquals("Chest", it.getString(0))
            }
        }
    }

    private fun showCatalogRow() {
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) { ExercisePicker({ selected = it.name }, {}) }
        }
        ui.onNodeWithText("Название или группа мышц").performTextInput("сидя нижний блок")
    }
}
