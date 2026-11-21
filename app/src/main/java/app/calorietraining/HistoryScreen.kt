package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun HistoryScreen(model: HistoryViewModel, onBackup: () -> Unit,
    onFoodDay: (LocalDate) -> Unit, onWorkout: (SessionHistory) -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    HistoryContent(state, model::load, onBackup = onBackup, onFoodDay = onFoodDay, onWorkout = onWorkout)
}

@Composable
private fun HistoryContent(state: HistoryState, load: (LocalDate, String?) -> Unit, modifier: Modifier = Modifier,
    onBackup: () -> Unit = {}, onFoodDay: (LocalDate) -> Unit = {}, onWorkout: (SessionHistory) -> Unit = {}) {
    var showTraining by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("История", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.headlineLarge)
                TextButton(onClick = onBackup) { Text("Резервная копия") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showTraining, { showTraining = false }, label = { Text("Калории") })
                FilterChip(showTraining, { showTraining = true }, label = { Text("Упражнения") })
            }
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error -> {
                    Text("Не удалось загрузить историю")
                    TextButton(onClick = { load(state.monday, state.exercise) }) { Text("Повторить") }
                }
                showTraining -> {
                    if (state.exercise == null) {
                        Text("Здесь появятся упражнения после записи первого подхода.", Modifier.padding(vertical = 16.dp))
                    } else {
                        Box {
                            OutlinedButton(onClick = { choosing = true }) { Text("Упражнение · ${state.exercise} ▾") }
                            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                                state.exercises.forEach { name ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = { choosing = false; load(state.monday, name) })
                                }
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                            item(key = "progress-${state.exercise}") {
                                ExerciseProgress(state.exercise, state.sessions, onWorkout)
                            }
                            items(state.sessions, key = { it.id }) { session ->
                                OutlinedCard(onClick = { onWorkout(session) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(session.date.caption(), style = MaterialTheme.typography.titleMedium)
                                        if (session.title.isNotEmpty()) Text(session.title, style = MaterialTheme.typography.titleSmall)
                                        session.sets.forEachIndexed { i, set ->
                                            val weight = if (set.weight.isBlank()) "Свой вес" else "${set.weight} кг"
                                            Text("Подход ${i + 1} · $weight × ${set.reps}" + if (set.warmup) " · разминка" else "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            DateArrow("‹", "Предыдущая неделя", true) { load(state.monday.minusWeeks(1), state.exercise) }
                            Text("${state.monday.caption()} — ${state.monday.plusDays(6).caption()}",
                                Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall)
                            DateArrow("›", "Следующая неделя", true) { load(state.monday.plusWeeks(1), state.exercise) }
                        }
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("${state.total.display()} ккал за неделю", style = MaterialTheme.typography.titleLarge)
                                state.average?.let { Text("Среднее за день с записями · ${it.display()} ккал") }
                            }
                        }
                    }
                    items(7) { i ->
                        val day = state.monday.plusDays(i.toLong())
                        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Открыть дневник питания") { onFoodDay(day) }
                            .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(day.caption())
                            Text(state.totals[day]?.let { "${it.display()} ккал" } ?: "Нет записей")
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Preview(locale = "ru")
@Composable
private fun HistoryPreview() {
    MaterialTheme { HistoryContent(HistoryState(loading = false), { _, _ -> }) }
}
