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

data class ProfileState(val days: Int = 30, val stats: ProfileStats? = null, val draft: Measurement? = null,
    val busy: Boolean = false, val error: String? = null)

sealed interface ProfileEvent {
    data class Period(val days: Int) : ProfileEvent
    data class Open(val day: LocalDate) : ProfileEvent
    data class Edit(val value: Measurement) : ProfileEvent
    data object Close : ProfileEvent
    data object Save : ProfileEvent
    data object Delete : ProfileEvent
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(ProfileState())
    val uiState = state.asStateFlow()

    fun event(event: ProfileEvent) {
        val s = state.value
        if (s.busy) return
        when (event) {
            is ProfileEvent.Period -> refresh(event.days)
            is ProfileEvent.Open -> state.value = s.copy(draft = s.stats?.measurements?.find { it.day == event.day }
                ?: Measurement(event.day), error = null)
            is ProfileEvent.Edit -> state.value = s.copy(draft = event.value)
            ProfileEvent.Close -> state.value = s.copy(draft = null, error = null)
            ProfileEvent.Save -> s.draft?.takeIf { it.valid }?.let { refresh(s.days) { saveMeasurement(it) } }
            ProfileEvent.Delete -> s.draft?.let { record -> refresh(s.days) {
                writableDatabase.delete("measurement","day=?",arrayOf(record.day.toString()))
            } }
        }
    }

    fun refresh(days: Int = state.value.days, change: (FoodStore.() -> Unit)? = null) {
        if (state.value.busy) return
        state.value = state.value.copy(days = days, busy = true, error = null)
        viewModelScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    FoodStore(getApplication()).use { store ->
                        if (change == null) return@use store.profileStats(days)
                        val db = store.writableDatabase
                        db.beginTransaction()
                        try {
                            store.change()
                            store.profileStats(days).also { db.setTransactionSuccessful() }
                        } finally { db.endTransaction() }
                    }
                }
                state.value = ProfileState(days = days, stats = stats)
            } catch (e: SQLException) {
                state.value = state.value.copy(busy = false, error = "Не удалось прочитать или сохранить данные профиля.")
            }
        }
    }
}
