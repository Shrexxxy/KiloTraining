package app.calorietraining

import android.app.Application
import android.database.SQLException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Clock

data class WorkoutState(val day:LocalDate=LocalDate.now(), val list:List<Workout> = emptyList(),
    val session:Long?=null, val draft:SetDraft?=null, val busy:Boolean=false,val error:String?=null,
    val previous: PreviousExercise? = null, val templates: List<WorkoutTemplate> = emptyList(),
    val opened: Long? = null, val clock: Clock = Clock.systemDefaultZone()) {
    val activeWorkout get() = list.firstOrNull { it.active }
}
sealed interface WorkoutEvent {
    data class Edit(val session:Long,val draft:SetDraft):WorkoutEvent
    data class Remove(val session:Long):WorkoutEvent
    data class Duration(val session: Long, val minutes: Int?) : WorkoutEvent
    data class Rename(val session: Long, val title: String) : WorkoutEvent
    data class Move(val session: Long, val date: LocalDate) : WorkoutEvent
    data class StartTemplate(val id: Long) : WorkoutEvent
    data class RenameTemplate(val id: Long, val title: String) : WorkoutEvent
    data class ReplaceTemplate(val id: Long, val session: Long) : WorkoutEvent
    data class SaveTemplate(val session: Long, val title: String) : WorkoutEvent
    data class Repeat(val session: Long, val date: LocalDate) : WorkoutEvent
    data object StartLive : WorkoutEvent
    data class Record(val start: LocalDateTime) : WorkoutEvent
    data class Open(val id: Long) : WorkoutEvent
    data object BackToList : WorkoutEvent
    data object Refresh : WorkoutEvent
    data class Finish(val id: Long) : WorkoutEvent
    data class ChangeStart(val id: Long, val start: LocalDateTime) : WorkoutEvent
    data object Close:WorkoutEvent
    data class Save(val planned: Boolean = false) : WorkoutEvent
    data class Complete(val session: Long, val setId: Long) : WorkoutEvent
    data object DeleteSet : WorkoutEvent
}

class WorkoutViewModel(app: Application, private val clock: Clock = Clock.systemDefaultZone()) : AndroidViewModel(app) {
    private val state = MutableStateFlow(WorkoutState(day = LocalDate.now(clock), clock = clock))
    val uiState=state.asStateFlow()
    private var historyLookup: Job? = null
    private var navigatedDuringUpdate = false
    init { update(state.value.day) }
    fun event(event:WorkoutEvent) {
        val s=state.value
        if (event is WorkoutEvent.Open || event == WorkoutEvent.BackToList) {
            val opened = (event as? WorkoutEvent.Open)?.id
            navigatedDuringUpdate = s.busy
            state.value = s.copy(opened = opened, draft = null, session = null)
            if (!s.busy && event == WorkoutEvent.BackToList) update(s.day)
            return
        }
        if(s.busy) return
        when(event) {
            is WorkoutEvent.StartTemplate -> update(s.day, openCreated = true) { startTemplate(event.id, LocalDateTime.now(clock)) }
            is WorkoutEvent.RenameTemplate -> if (event.title.isNotBlank()) update(s.day) { renameTemplate(event.id, event.title) }
            is WorkoutEvent.ReplaceTemplate -> update(s.day) { replaceTemplate(event.id, event.session) }
            is WorkoutEvent.SaveTemplate -> if (event.title.isNotBlank()) update(s.day) { saveTemplate(event.session, event.title) }
            is WorkoutEvent.Repeat -> s.list.find { it.id == event.session && it.sets.any { set -> !set.planned } }?.let { source ->
                update(event.date, openCreated = true) { repeatWorkout(source, event.date) }
            }
            is WorkoutEvent.Move -> if (s.list.any { it.id == event.session && it.day != event.date }) {
                update(event.date) { moveWorkout(event.session, event.date) }
            }
            is WorkoutEvent.Duration -> if (event.minutes == null || event.minutes in workoutMinutesRange) update(s.day) {
                setDuration(event.session, event.minutes)
            }
            is WorkoutEvent.Rename -> update(s.day) {
                writableDatabase.execSQL("UPDATE workout SET title=? WHERE id=?",
                    arrayOf<Any>(event.title.trim(), event.session))
            }
            WorkoutEvent.StartLive -> update(s.day, openCreated = true) { newWorkout(LocalDateTime.now(clock), live = true) }
            is WorkoutEvent.Record -> update(event.start.toLocalDate(), openCreated = true) { newWorkout(event.start, live = false) }
            is WorkoutEvent.Open, WorkoutEvent.BackToList -> Unit
            WorkoutEvent.Refresh -> update(s.day)
            is WorkoutEvent.Finish -> update(s.day) { finishWorkout(event.id) }
            is WorkoutEvent.ChangeStart -> update(event.start.toLocalDate()) { changeWorkoutStart(event.id, event.start) }
            is WorkoutEvent.Edit -> {
                val changed = s.session != event.session || s.draft?.exercise != event.draft.exercise || s.draft.id != event.draft.id
                state.value = s.copy(session = event.session, draft = event.draft, error = null,
                    previous = if (changed) null else s.previous)
                if (changed) loadPrevious(s.list.firstOrNull { it.id == event.session }?.day ?: s.day,
                    event.session, event.draft)
            }
            WorkoutEvent.Close -> state.value=s.copy(draft=null,error=null)
            WorkoutEvent.DeleteSet -> {
                val id = s.draft?.id
                if (s.session != null && id != null) update(s.day) {
                    writableDatabase.delete("lifting", "session=? AND id=?", arrayOf(s.session.toString(), id.toString()))
                }
            }
            is WorkoutEvent.Save -> if(s.session!=null && s.draft?.valid == true) update(s.day) {
                saveSet(s.session, s.draft, planned = event.planned)
            }
            is WorkoutEvent.Complete -> update(s.day) { completeSet(event.session, event.setId) }
            is WorkoutEvent.Remove -> update(s.day) {
                val args=arrayOf(event.session.toString())
                writableDatabase.delete("lifting","session=?",args)
                writableDatabase.delete("workout","id=?",args)
            }
        }
    }
    private fun loadPrevious(day: LocalDate, session: Long, draft: SetDraft) {
        historyLookup?.cancel()
        if ((!draft.planned && draft.id != null) || draft.exercise.isBlank()) return
        historyLookup = viewModelScope.launch {
            val previous = try {
                withContext(Dispatchers.IO) {
                    FoodStore(getApplication(), clock = clock).use { it.previousExercise(day, session, draft.exercise.trim()) }
                }
            } catch (e: SQLException) { null }
            val current = state.value
            if (current.session == session && current.draft?.exercise == draft.exercise && current.draft.id == draft.id) {
                state.value = current.copy(previous = previous)
            }
        }
    }
    private fun update(
        day: LocalDate,
        openCreated: Boolean = false,
        change: (FoodStore.() -> Any?)? = null,
    ) {
        val opened = state.value.opened
        navigatedDuringUpdate = false
        state.value=state.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                val rows=withContext(Dispatchers.IO) {
                    FoodStore(getApplication(), clock = clock).use { store ->
                        val db=store.writableDatabase
                        db.beginTransaction()
                        try {
                            store.finishInactiveWorkouts()
                            val created = change?.invoke(store)
                            val list = store.workouts()
                            val selected = if (openCreated) created as Long else opened
                            WorkoutState(day = day, list = list, templates = store.templates(),
                                opened = selected, clock = clock)
                                .also { db.setTransactionSuccessful() }
                        } finally { db.endTransaction() }
                    }
                }
                val selected = if (navigatedDuringUpdate) state.value.opened else rows.opened
                state.value = rows.copy(opened = selected?.takeIf { id -> rows.list.any { it.id == id } })
            } catch(e:SQLException) {state.value=state.value.copy(busy=false,error="Не удалось открыть или сохранить тренировку")}
        }
    }
}
