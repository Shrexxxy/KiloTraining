package app.calorietraining

import android.app.Application
import android.database.SQLException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

internal data class ProfileState(val days: Int = 30, val stats: ProfileStats? = null, val draft: Measurement? = null,
    val busy: Boolean = false, val error: String? = null, val weeklyMuscles: MuscleLoad? = null)

sealed interface ProfileEvent {
    data class Period(val days: Int) : ProfileEvent
    data class Open(val day: LocalDate) : ProfileEvent
    data class Edit(val value: Measurement) : ProfileEvent
    data object Close : ProfileEvent
    data object Save : ProfileEvent
    data object Delete : ProfileEvent
}

internal data class ProductServerState(
    val address: String,
    val key: String,
    val status: String? = null,
    val pending: Int? = null,
    val lastSent: Instant? = null,
    val busy: Boolean = false,
    val unsuitable: Int = 0
)

class ProfileViewModel internal constructor(
    application: Application,
    serverFactory: (String, String) -> ProductServer
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ::HttpProductServer)

    private val settings = ProductServerSettings(application, serverFactory)
    private val serverState = MutableStateFlow(ProductServerState(settings.address, settings.key, lastSent = settings.lastSent))
    internal val productServerState = serverState.asStateFlow()

    init {
        viewModelScope.launch {
            ProductServerSettings.changes.collect {
                val pending = withContext(Dispatchers.IO) {
                    try {
                        FoodStore(application).use { it.pendingSyncCount() }
                    } catch (_: SQLException) {
                        null
                    }
                }
                serverState.update { it.copy(pending = pending, lastSent = settings.lastSent) }
            }
        }
    }

    internal fun changeServerAddress(value: String) {
        settings.setAddress(value)
        serverState.update { it.copy(address = value, status = null, unsuitable = 0) }
    }

    internal fun changeServerKey(value: String) {
        settings.setKey(value)
        serverState.update { it.copy(key = value, status = null, unsuitable = 0) }
    }

    internal fun checkProductServer() = serverAction {
        val status = try {
            val client = checkNotNull(settings.client())
            if (client.health()) {
                client.search("те", limit = 1)
                "Связь есть"
            } else "Сервер не отвечает"
        } catch (error: ProductServerException) {
            error.failure.connectionStatus()
        }
        serverState.update { it.copy(status = status) }
    }

    internal fun sendProducts() = serverAction {
        val report = settings.send() ?: return@serverAction
        serverState.update {
            it.copy(status = report.failure?.connectionStatus() ?: if (report.sent > 0) "Связь есть" else it.status,
                lastSent = settings.lastSent, unsuitable = report.unsuitable)
        }
    }

    private fun serverAction(action: suspend () -> Unit) {
        if (serverState.value.busy || serverState.value.address.isBlank()) return
        serverState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                serverState.update { it.copy(busy = false) }
            }
        }
    }

    private fun ServerFailure.connectionStatus() =
        if (this == ServerFailure.UNAUTHORIZED) "Неверный ключ" else "Сервер не отвечает"

    private val state = MutableStateFlow(ProfileState())
    internal val uiState = state.asStateFlow()

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
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                state.value = withContext(Dispatchers.IO) {
                    FoodStore(getApplication()).use { store ->
                        fun readProfile(): ProfileState {
                            val stats = store.profileStats(days)
                            val today = stats.end
                            val workouts = store.workouts(today.minusDays(MUSCLE_LOAD_DAYS - 1), today)
                            val custom = store.customMuscles()
                            val load = muscleLoad(workouts, today) { name ->
                                photoForExercise(name)?.technique ?: custom[name.searchKey()]
                            }
                            return ProfileState(days = days, stats = stats, weeklyMuscles = load)
                        }

                        if (change == null) return@use readProfile()
                        val db = store.writableDatabase
                        db.beginTransaction()
                        try {
                            store.change()
                            readProfile().also { db.setTransactionSuccessful() }
                        } finally { db.endTransaction() }
                    }
                }
            } catch (e: SQLException) {
                state.value = state.value.copy(busy = false, error = "Не удалось прочитать или сохранить данные профиля.")
            }
        }
    }
}
