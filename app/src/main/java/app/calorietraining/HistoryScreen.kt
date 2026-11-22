package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import java.math.MathContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("История", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onBackup) { Text("Резервная копия") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !showTraining, onClick = { showTraining = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Питание") }
                SegmentedButton(selected = showTraining, onClick = { showTraining = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Упражнения") }
            }
            DelayedProgress(state.loading)
            when {
                state.error -> {
                    Text("Не удалось загрузить историю", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { load(state.monday, state.exercise) }) { Text("Повторить") }
                }
                showTraining -> {
                    if (state.exercise == null) {
                        Text("Здесь появятся упражнения после записи первого подхода.", Modifier.padding(vertical = 16.dp))
                    } else {
                        Box(Modifier.padding(top = 12.dp)) {
                            GlassCard(LocalDiaryAccents.current.workout, Modifier.fillMaxWidth(), onClick = { choosing = true }) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(state.exercise, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold)
                                    Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                                }
                            }
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
                                GlassCard(LocalDiaryAccents.current.workout, Modifier.fillMaxWidth(), onClick = { onWorkout(session) }) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(session.date.caption(), style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold)
                                        if (session.title.isNotEmpty()) Text(session.title, style = MaterialTheme.typography.titleSmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            session.sets.forEach { set -> WorkoutSetTile(set.weight, set.reps, set.warmup) }
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
                            DateArrow(R.drawable.ic_chevron_left, "Предыдущая неделя", true) { load(state.monday.minusWeeks(1), state.exercise) }
                            Text("${state.monday.caption()} — ${state.monday.plusDays(6).caption()}",
                                Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall)
                            DateArrow(R.drawable.ic_chevron_right, "Следующая неделя", true) { load(state.monday.plusWeeks(1), state.exercise) }
                        }
                        WeekNutrition(state)
                    }
                    items(7) { i ->
                        val day = state.monday.plusDays(i.toLong())
                        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Открыть дневник питания") { onFoodDay(day) }
                            .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(day.caption())
                            Text(state.totals[day]?.let { "${it.groupedNumber()} ккал" } ?: "Нет записей")
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekNutrition(state: HistoryState) {
    val colors = MaterialTheme.colorScheme
    val kcal = LocalDiaryAccents.current.kcal
    val peak = state.totals.values.maxOrNull()
    GlassCard(kcal) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ЗА НЕДЕЛЮ", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text("${state.total.groupedNumber()} ккал", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = kcal)
            state.average?.let {
                Text("Среднее за день с записями · ${it.groupedNumber()} ккал",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { index, label ->
                    val day = state.monday.plusDays(index.toLong())
                    val value = state.totals[day]
                    val fraction = if (value != null && peak != null && peak.signum() > 0)
                        value.divide(peak, MathContext.DECIMAL32).toFloat().coerceIn(0f, 1f) else 0f
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(68.dp).testTag("week-bar")
                            .background(colors.surfaceContainerHighest, MaterialTheme.shapes.extraSmall)
                            .semantics {
                                val amount = value?.let { "${it.groupedNumber()} ккал" } ?: "нет записей"
                                contentDescription = "${day.caption()}: $amount"
                            }, contentAlignment = Alignment.BottomCenter) {
                            if (value != null) Box(Modifier.fillMaxWidth().fillMaxHeight(fraction)
                                .heightIn(min = 2.dp).background(kcal, MaterialTheme.shapes.extraSmall))
                        }
                        Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant)
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
