package app.calorietraining

import android.app.Application
import android.database.SQLException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private data class HomeNutrition(val calories: BigDecimal, val macros: Macros, val goals: DailyGoals)

private fun FoodStore.recentWeights(today: LocalDate): List<Measurement> = readableDatabase.rawQuery(
    "SELECT day,weight,fat FROM measurement WHERE day<=? AND weight!='' ORDER BY day DESC LIMIT 2",
    arrayOf(today.toString()),
).use { rows ->
    buildList {
        while (rows.moveToNext()) add(Measurement(LocalDate.parse(rows.getString(0)), rows.getString(1), rows.getString(2)))
    }
}

@Composable
internal fun HomeScreen(
    app: Application,
    workout: WorkoutState,
    openNutrition: (LocalDate) -> Unit,
    addFood: (LocalDate) -> Unit,
    openWorkout: (Workout?) -> Unit,
) {
    val today = LocalDate.now()
    var nutrition by remember { mutableStateOf<HomeNutrition?>(null) }
    var weights by remember { mutableStateOf<List<Measurement>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(app, today, attempt) {
        loadError = false
        try {
            val (summary, recent) = withContext(Dispatchers.IO) {
                FoodStore(app).use { store ->
                    val entries = store.entries(today)
                    HomeNutrition(entries.sumOf { it.calories }, entries.dailyMacros(), store.goalsFor(today)) to
                        store.recentWeights(today)
                }
            }
            nutrition = summary
            weights = recent
        } catch (e: SQLException) {
            loadError = true
        }
    }

    val accents = LocalDiaryAccents.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Главная", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text(today.caption(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        nutrition?.let { TodayCard(it) { openNutrition(today) } }
        Button(onClick = { addFood(today) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить еду") }
        weights?.let { recent ->
            WeightCard(today, recent) { record ->
                val updated = withContext(Dispatchers.IO) {
                    FoodStore(app).use { store ->
                        store.saveMeasurement(record)
                        store.recentWeights(today)
                    }
                }
                weights = updated
            }
        }
        if (loadError) {
            Text("Не удалось загрузить сводку", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }) { Text("Повторить") }
        }
        DelayedProgress(!loadError && nutrition == null)
        GlassCard(accents.workout, Modifier.fillMaxWidth()) {
            val active = workout.activeWorkout
            val last = workout.list.firstOrNull()
            val minutes by produceState(0L, active?.startedAt, workout.clock) {
                val start = active?.startedAt ?: return@produceState
                while (true) {
                    val seconds = Duration.between(start, LocalDateTime.now(workout.clock)).seconds.coerceAtLeast(0)
                    value = seconds / 60
                    delay((60 - seconds % 60) * 1000)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Тренировка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    active != null -> Text("Идёт тренировка · $minutes мин")
                    last != null -> Text("Последняя тренировка: ${last.day.caption()}")
                    !workout.busy && workout.error == null -> Text("Тренировок пока нет")
                }
                workout.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                FilledTonalButton(onClick = { openWorkout(active) }, enabled = !workout.busy) {
                    Text(if (active == null) "Начать тренировку" else "Открыть")
                }
            }
        }
    }
}

@Composable
private fun TodayCard(summary: HomeNutrition, onClick: () -> Unit) {
    val accents = LocalDiaryAccents.current
    val goal = summary.goals.kcal
    GlassCard(accents.kcal, Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Сегодня", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (goal == null) {
                Text("${summary.calories.ringNumber(grouped = false)} ккал", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClick) { Text("Задать цель") }
            } else {
                val fraction = ringState(summary.calories, goal)?.fraction ?: 0f
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { fraction.coerceAtMost(1f) },
                        modifier = Modifier.size(38.dp),
                        color = accents.kcal,
                        strokeWidth = 4.dp,
                    )
                    Column {
                        Text("съедено ${summary.calories.ringNumber(grouped = false)} из $goal ккал")
                        Text(
                            calorieGoalStatus(summary.calories, goal, today = true, grouped = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroBar("Б", summary.macros.protein, summary.goals.macros.protein, accents.protein, Modifier.weight(1f))
                    MacroBar("Ж", summary.macros.fat, summary.goals.macros.fat, accents.fat, Modifier.weight(1f))
                    MacroBar("У", summary.macros.carbs, summary.goals.macros.carbs, accents.carbs, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, total: String, goal: Int?, color: Color, modifier: Modifier) {
    val value = total.amount()
    val fraction = ringState(value, goal)?.fraction ?: 0f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label ${value?.ringNumber(grouped = false) ?: "—"}${goal?.let { "/$it" }.orEmpty()} г",
            style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { fraction.coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = color)
    }
}

@Composable
private fun WeightCard(today: LocalDate, recent: List<Measurement>, save: suspend (Measurement) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val latest = recent.firstOrNull()
    GlassCard(LocalDiaryAccents.current.workout, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Вес", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (latest == null) {
                Text("Вес ещё не записан")
            } else {
                val weight = latest.weight.amount()
                Text("${weight?.display() ?: latest.weight} кг", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(latest.day.caption(), style = MaterialTheme.typography.bodySmall)
                    val previous = recent.getOrNull(1)?.weight?.amount()
                    if (weight != null && previous != null) {
                        val delta = weight - previous
                        val change = when (delta.signum()) {
                            1 -> "+${delta.display()} кг"
                            -1 -> "−${delta.abs().display()} кг"
                            else -> "без изменений"
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                change,
                                modifier = Modifier.semantics {
                                    contentDescription = "Изменение с прошлого замера: $change"
                                }.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = { editing = true }) { Text("Записать вес") }
        }
    }
    if (editing) WeightDialog(today, { editing = false }, save)
}

@Composable
private fun WeightDialog(today: LocalDate, close: () -> Unit, save: suspend (Measurement) -> Unit) {
    var weight by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val record = Measurement(today, weight.trim(), fat.trim())
    AlertDialog(
        onDismissRequest = { if (!saving) close() },
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Записать вес") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiaryTextField(weight, { weight = it }, Modifier.fillMaxWidth(), enabled = !saving, singleLine = true,
                    label = { Text("Вес, кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                DiaryTextField(fat, { fat = it }, Modifier.fillMaxWidth(), enabled = !saving, singleLine = true,
                    label = { Text("Жир, % (необязательно)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (error) Text("Не удалось сохранить замер", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && record.weight.isNotEmpty() && record.valid, onClick = {
                saving = true
                error = false
                scope.launch {
                    try {
                        save(record)
                        close()
                    } catch (e: SQLException) {
                        error = true
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = close) { Text("Отмена") } },
    )
}
