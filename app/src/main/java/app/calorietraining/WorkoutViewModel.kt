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

data class WorkoutState(val day:LocalDate=LocalDate.now(), val list:List<Workout> = emptyList(),
    val session:Long?=null, val draft:SetDraft?=null, val busy:Boolean=false,val error:String?=null,
    val previous: PreviousExercise? = null, val templates: List<WorkoutTemplate> = emptyList())
sealed interface WorkoutEvent {
    data class Day(val day:LocalDate):WorkoutEvent
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
    data object Create:WorkoutEvent
    data object Close:WorkoutEvent
    data class Save(val planned: Boolean = false) : WorkoutEvent
    data class Complete(val session: Long, val setId: Long) : WorkoutEvent
    data object DeleteSet : WorkoutEvent
}

class WorkoutViewModel(app:Application):AndroidViewModel(app) {
    private val state=MutableStateFlow(WorkoutState())
    val uiState=state.asStateFlow()
    private var historyLookup: Job? = null
    init { update(state.value.day) }
    fun event(event:WorkoutEvent) {
        val s=state.value
        if(s.busy) return
        when(event) {
            is WorkoutEvent.Day -> update(event.day)
            is WorkoutEvent.StartTemplate -> update(s.day) { startTemplate(event.id, s.day) }
            is WorkoutEvent.RenameTemplate -> if (event.title.isNotBlank()) update(s.day) { renameTemplate(event.id, event.title) }
            is WorkoutEvent.ReplaceTemplate -> update(s.day) { replaceTemplate(event.id, event.session) }
            is WorkoutEvent.SaveTemplate -> if (event.title.isNotBlank()) update(s.day) { saveTemplate(event.session, event.title) }
            is WorkoutEvent.Repeat -> s.list.find { it.id == event.session && it.sets.any { set -> !set.planned } }?.let { source ->
                update(event.date) { repeatWorkout(source, event.date) }
            }
            is WorkoutEvent.Move -> if (event.date != s.day && s.list.any { it.id == event.session }) {
                update(event.date) {
                    writableDatabase.execSQL("UPDATE workout SET day=? WHERE id=? AND day=?",
                        arrayOf(event.date.toString(), event.session, s.day.toString()))
                }
            }
            is WorkoutEvent.Duration -> if (event.minutes == null || event.minutes in workoutMinutesRange) update(s.day) {
                setDuration(event.session, event.minutes)
            }
            is WorkoutEvent.Rename -> update(s.day) {
                writableDatabase.execSQL("UPDATE workout SET title=? WHERE id=? AND day=?",
                    arrayOf(event.title.trim(), event.session, s.day.toString()))
            }
            WorkoutEvent.Create -> update(s.day) { newWorkout(s.day) }
            is WorkoutEvent.Edit -> {
                val changed = s.session != event.session || s.draft?.exercise != event.draft.exercise || s.draft.id != event.draft.id
                state.value = s.copy(session = event.session, draft = event.draft, error = null,
                    previous = if (changed) null else s.previous)
                if (changed) loadPrevious(s.day, event.session, event.draft)
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
            is WorkoutEvent.Complete -> update(s.day) {
                writableDatabase.execSQL("UPDATE lifting SET planned=0 WHERE session=? AND id=? AND planned=1",
                    arrayOf(event.session, event.setId))
            }
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
                    FoodStore(getApplication()).use { it.previousExercise(day, session, draft.exercise.trim()) }
                }
            } catch (e: SQLException) { null }
            val current = state.value
            if (current.session == session && current.draft?.exercise == draft.exercise && current.draft.id == draft.id) {
                state.value = current.copy(previous = previous)
            }
        }
    }
    private fun update(day:LocalDate,change:(FoodStore.()->Unit)?=null) {
        state.value=state.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                val rows=withContext(Dispatchers.IO) {
                    FoodStore(getApplication()).use { store ->
                        fun snapshot() = WorkoutState(day = day, list = store.workouts(day), templates = store.templates())
                        if(change==null) return@use snapshot()
                        val db=store.writableDatabase
                        db.beginTransaction()
                        try {
                            store.change()
                            snapshot().also { db.setTransactionSuccessful() }
                        } finally { db.endTransaction() }
                    }
                }
                state.value=rows
            } catch(e:SQLException) {state.value=state.value.copy(busy=false,error="Не удалось открыть или сохранить тренировку")}
        }
    }
}
