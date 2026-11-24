package app.calorietraining

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.StringWriter
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class ProfileCustomMusclesTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test fun customMusclesCountInWeeklyLoad() {
        FoodStore(app).use {
            it.addSets("Мой жим", 2)
            it.saveCustomMuscles("Мой жим", listOf(Muscle.Chest), listOf(Muscle.Triceps))
        }
        showProfile()
        awaitMuscles("Грудь — 2")
        ui.onNodeWithText("Трицепс — 1").assertExists()
        assertNoUnknownSets()
    }

    @Test fun nameIsMatchedIgnoringCaseAndSpaces() {
        FoodStore(app).use {
            it.addSets("  МОЙ ЖИМ лЁжа ", 1)
            it.saveCustomMuscles("Мой жим лежа", listOf(Muscle.Chest), emptyList())
        }
        showProfile()
        awaitMuscles("Грудь — 1")
        assertNoUnknownSets()
    }

    @Test fun catalogAndCustomSetsAreAdded() {
        FoodStore(app).use {
            it.addSets("Жим штанги лёжа", 3)
            it.addSets("Мой жим", 2)
            it.saveCustomMuscles("Мой жим", listOf(Muscle.Chest), listOf(Muscle.Triceps))
        }
        showProfile()
        awaitMuscles("Грудь — 5")
        ui.onNodeWithText("Трицепс — 2,5").assertExists()
        ui.onNodeWithText("Плечи — 1,5").assertExists()
        assertNoUnknownSets()
    }

    @Test fun customExerciseWithoutMusclesStaysUnknown() {
        FoodStore(app).use {
            it.addSets("Мой жим", 2)
            it.addSets("Другое", 1)
            it.saveCustomMuscles("Мой жим", listOf(Muscle.Chest), emptyList())
        }
        showProfile()
        awaitMuscles("Грудь — 2")
        ui.onNodeWithText("Подходов без данных о мышцах: 1").assertExists()
    }

    @Test fun musclesSavedInWorkoutAppearOnReturnToProfile() {
        FoodStore(app).use { it.addSets("Мой жим", 2) }
        showProfile()
        awaitMuscles("Подходов без данных о мышцах: 2")

        openTab("Тренировки")
        awaitText("Моя тренировка").performClick()
        ui.awaitUntil(10_000) {
            ui.onAllNodesWithContentDescription("Мышцы упражнения").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("Мышцы упражнения").performClick()
        awaitText("грудь").performScrollTo().performClick()
        ui.onNodeWithText("трицепс").performScrollTo().performClick().performClick()
        ui.onNodeWithText("Сохранить").performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Сохранить").fetchSemanticsNodes().isEmpty() }

        openTab("Профиль")
        awaitMuscles("Грудь — 2")
        ui.onNodeWithText("Трицепс — 1").assertExists()
        assertNoUnknownSets()
    }

    @Test fun restoredCopyReplacesWeeklyLoad() {
        val copy = FoodStore(app).use { store ->
            store.addSets("Мой жим", 2)
            store.saveCustomMuscles("Мой жим", listOf(Muscle.Chest), emptyList())
            StringWriter().also(store::exportDiary).toString().also {
                store.saveCustomMuscles("Мой жим", listOf(Muscle.Biceps), emptyList())
            }
        }
        showProfile()
        awaitMuscles("Бицепс — 2")

        val uri = Uri.parse("content://copies/diary.json")
        shadowOf(app.contentResolver).registerInputStream(uri, copy.byteInputStream())
        // Файл выбирает системное окно, поэтому копия передаётся модели экрана копий напрямую.
        val backup = ViewModelProvider(
            owner, viewModelFactory { initializer { BackupViewModel(app) } },
        )[BackupViewModel::class.java]
        ui.runOnIdle { backup.prepare(uri) }
        ui.awaitUntil(10_000) { backup.uiState.value.preview != null }
        ui.runOnIdle { backup.restore() }
        ui.awaitUntil(10_000) { backup.uiState.value.revision == 1 }

        awaitMuscles("Грудь — 2")
        ui.onNodeWithText("Бицепс — 2").assertDoesNotExist()
        assertNoUnknownSets()
    }

    private fun FoodStore.addSets(exercise: String, count: Int) {
        val workout = workouts(LocalDate.now()).firstOrNull()?.id ?: newWorkout(LocalDate.now()).also {
            writableDatabase.execSQL("UPDATE workout SET title='Моя тренировка' WHERE id=$it")
        }
        repeat(count) {
            saveSet(workout, SetDraft(group = exercise, exercise = exercise, weight = "60", reps = "8"))
        }
    }

    private fun showProfile() {
        ui.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                DiaryTheme(ThemeMode.DARK) { DiaryApp(ThemeMode.DARK, {}) }
            }
        }
        openTab("Профиль")
    }

    private fun openTab(label: String) {
        ui.onNode(hasText(label) and isSelectable()).performClick()
    }

    private fun awaitText(text: String): SemanticsNodeInteraction {
        ui.awaitUntil(10_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return ui.onNodeWithText(text)
    }

    private fun awaitMuscles(line: String) {
        ui.awaitUntil(10_000) {
            runCatching { ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Мышцы за неделю")) }.isSuccess &&
                ui.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoUnknownSets() {
        ui.onNodeWithText("Подходов без данных о мышцах", substring = true).assertDoesNotExist()
    }
}
