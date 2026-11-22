package app.calorietraining

import android.app.Application
import android.database.SQLException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SessionHistory(val id: Long, val date: LocalDate, val sets: List<PreviousSet>, val title: String = "")

data class HistoryState(
    val monday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val totals: Map<LocalDate, BigDecimal> = emptyMap(),
    val exercises: List<String> = emptyList(),
    val exercise: String? = null,
    val sessions: List<SessionHistory> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false
) {
    val total: BigDecimal get() = totals.values.fold(BigDecimal.ZERO, BigDecimal::add)
    val average: BigDecimal? get() = if (totals.isEmpty()) null
        else total.divide(totals.size.toBigDecimal(), 2, RoundingMode.HALF_UP)
}

private fun FoodStore.weekTotals(start: LocalDate): Map<LocalDate, BigDecimal> {
    val totals = mutableMapOf<LocalDate, BigDecimal>()
    readableDatabase.rawQuery("SELECT day, calories FROM food WHERE day >= ? AND day < ?",
        arrayOf(start.toString(), start.plusWeeks(1).toString())).use { cursor ->
        while (cursor.moveToNext()) {
            val day = LocalDate.parse(cursor.getString(0))
            totals[day] = (totals[day] ?: BigDecimal.ZERO) + cursor.getString(1).toBigDecimal()
        }
    }
    return totals
}

private fun FoodStore.historyOf(name: String): List<SessionHistory> {
    val result = mutableListOf<SessionHistory>()
    var session: Long? = null
    var sets = mutableListOf<PreviousSet>()
    readableDatabase.rawQuery("""SELECT w.id, w.day, s.weight, s.reps, w.title, s.warmup
        FROM workout w JOIN lifting s ON s.session=w.id WHERE s.planned=0 AND s.exercise=?
        ORDER BY w.day DESC, w.id DESC, s.id""", arrayOf(name)).use { cursor ->
        while (cursor.moveToNext()) {
            if (cursor.getLong(0) != session) {
                session = cursor.getLong(0)
                sets = mutableListOf()
                result.add(SessionHistory(session, LocalDate.parse(cursor.getString(1)), sets, cursor.getString(4)))
            }
            sets.add(PreviousSet(cursor.getString(2), cursor.getString(3), cursor.getInt(5) == 1))
        }
    }
    return result
}

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val state = MutableStateFlow(HistoryState())
    val uiState = state.asStateFlow()
    private var pending: Job? = null

    fun load(monday: LocalDate = state.value.monday, exercise: String? = state.value.exercise) {
        pending?.cancel()
        state.value = state.value.copy(monday = monday, exercise = exercise, loading = true, error = false)
        pending = viewModelScope.launch {
            try {
                state.value = withContext(Dispatchers.IO) {
                    FoodStore(getApplication()).use { store ->
                        val names = store.readableDatabase.rawQuery("""SELECT s.exercise FROM lifting s
                            JOIN workout w ON w.id=s.session WHERE s.planned=0 GROUP BY s.exercise
                            ORDER BY MAX(w.day) DESC, s.exercise""", null).use { cursor ->
                            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                        }
                        val selected = exercise?.takeIf { it in names } ?: names.firstOrNull()
                        HistoryState(monday, store.weekTotals(monday), names, selected,
                            selected?.let(store::historyOf).orEmpty(), loading = false)
                    }
                }
            } catch (e: SQLException) {
                ensureActive()
                state.value = state.value.copy(loading = false, error = true)
            }
        }
    }
}
