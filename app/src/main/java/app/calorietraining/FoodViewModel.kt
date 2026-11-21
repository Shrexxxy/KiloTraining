package app.calorietraining

import android.app.Application
import android.database.SQLException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface CatalogSearch {
    data object Idle : CatalogSearch
    data class Loading(val term: String) : CatalogSearch
    data class Results(val term: String, val foods: List<FoodDraft>) : CatalogSearch
    data class Failed(val term: String) : CatalogSearch
}

data class FoodUiState(
    val day: LocalDate = LocalDate.now(),
    val rows: List<FoodEntry> = emptyList(),
    val recent: List<FoodDraft> = emptyList(),
    val library: List<FoodDraft>? = null,
    val online: CatalogSearch = CatalogSearch.Idle,
    val goal: Int? = null,
    val draft: FoodDraft? = null,
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val scanning: Boolean = false,
    val hint: String = "",
    val confirmingDelete: Boolean = false
)

sealed interface FoodEvent {
    data class Day(val value: LocalDate) : FoodEvent
    data class Edit(val value: FoodDraft) : FoodEvent
    data class FindFood(val term: String) : FoodEvent
    data object BrowseFoods : FoodEvent
    data object BackToEditor : FoodEvent
    data object Scan : FoodEvent
    data class Barcode(val code: String) : FoodEvent
    data class Goal(val kcal: Int?) : FoodEvent
    data class CopyMeal(val source: LocalDate, val destination: LocalDate, val meal: Meal) : FoodEvent
    data object Close : FoodEvent
    data object Save : FoodEvent
    data object AskDelete : FoodEvent
    data object CancelDelete : FoodEvent
    data object Delete : FoodEvent
}

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(FoodUiState())
    val uiState = state.asStateFlow()

    init { refresh(state.value.day) }

    fun onEvent(event: FoodEvent) {
        val current = state.value
        if (current.busy) return
        when (event) {
            is FoodEvent.Day -> refresh(event.value)
            is FoodEvent.Goal -> refresh(current.day) { setDailyGoal(event.kcal) }
            is FoodEvent.CopyMeal -> if (current.day == event.source && current.rows.any { it.meal == event.meal }) {
                refresh(event.destination) { copyMeal(event.source, event.destination, event.meal) }
            }
            is FoodEvent.Edit -> state.value = current.copy(draft = if (current.draft == null && event.value.id == null) event.value.copy(mealTime = defaultMealTime(current.day)) else event.value, error = null, library = null)
            is FoodEvent.FindFood -> findFood(event.term.trim())
            FoodEvent.BackToEditor -> state.value = current.copy(library = null)
            FoodEvent.BrowseFoods -> {
                state.value = current.copy(busy = true, error = null)
                viewModelScope.launch {
                    try {
                        val foods = withContext(Dispatchers.IO) { FoodStore(getApplication()).use { it.foodSuggestions() } }
                        state.value = current.copy(busy = false, library = foods, draft = current.draft ?: FoodDraft(mealTime = defaultMealTime(current.day)), online = CatalogSearch.Idle, error = null)
                    } catch (e: SQLException) {
                        state.value = current.copy(busy = false, error = "Не удалось загрузить список продуктов")
                    }
                }
            }
            FoodEvent.Scan -> state.value = current.copy(scanning = true, error = null)
            is FoodEvent.Barcode -> lookup(event.code)
            FoodEvent.Close -> state.value = current.copy(draft = null, scanning = false, hint = "", confirmingDelete = false, error = null)
            FoodEvent.Save -> current.draft?.takeIf { it.calories != null }?.let { draft ->
                refresh(current.day) { save(current.day, draft) }
            }
            FoodEvent.AskDelete -> state.value = current.copy(confirmingDelete = true)
            FoodEvent.CancelDelete -> state.value = current.copy(confirmingDelete = false)
            FoodEvent.Delete -> current.draft?.id?.let { id ->
                refresh(current.day) { delete(current.day, id) }
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun findFood(term: String) {
        if (term.length < 2 || state.value.library == null) return
        searchJob?.cancel()
        val pending = CatalogSearch.Loading(term)
        state.value = state.value.copy(online = pending)
        searchJob = viewModelScope.launch {
            val result = try {
                CatalogSearch.Results(term, withContext(Dispatchers.IO) { searchCatalog(term) })
            } catch (error: java.io.IOException) {
                CatalogSearch.Failed(term)
            }
            if (state.value.library != null && state.value.online === pending) {
                state.value = state.value.copy(online = result)
            }
        }
    }

    private fun lookup(code: String) {
        if (!validBarcode(code)) return
        state.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val product = withContext(Dispatchers.IO) { FoodStore(getApplication()).use { it.lookup(code) } }
                state.value = state.value.copy(draft = product.draft.copy(mealTime = defaultMealTime(state.value.day)), hint = product.hint, busy = false, scanning = false, error = null)
            } catch (e: SQLException) {
                state.value = state.value.copy(error = "Не удалось прочитать каталог", busy = false)
            }
        }
    }

    private fun refresh(day: LocalDate, change: (FoodStore.() -> Unit)? = null) {
        state.value = state.value.copy(busy = true, error = null, confirmingDelete = false)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    FoodStore(getApplication()).use { store ->
                        fun snapshot() = FoodUiState(day = day, rows = store.entries(day), recent = store.foodSuggestions(day, 12),
                            goal = store.dailyGoal(), loaded = true)
                        if (change == null) return@use snapshot()
                        val db = store.writableDatabase
                        db.beginTransaction()
                        try {
                            store.change()
                            snapshot().also { db.setTransactionSuccessful() }
                        } finally { db.endTransaction() }
                    }
                }
                state.value = snapshot
            } catch (e: SQLException) {
                state.value = state.value.copy(busy = false, error = "Не удалось открыть или сохранить дневник. Попробуйте ещё раз.")
            }
        }
    }
}
