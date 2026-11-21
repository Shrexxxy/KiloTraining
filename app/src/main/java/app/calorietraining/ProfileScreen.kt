package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun ProfileScreen(model: ProfileViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { if (model.uiState.value.draft == null) model.refresh() }
    ProfileContent(state, model::event)
}

@Composable
private fun ProfileContent(state: ProfileState, event: (ProfileEvent) -> Unit, modifier: Modifier = Modifier) {
    val draft = state.draft
    var history by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        if (draft != null) {
            BackHandler { event(ProfileEvent.Close) }
            var confirm by rememberSaveable(draft.day.toEpochDay()) { mutableStateOf(false) }
            Column(body.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Замер тела", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium)
                DiaryDaySelector(draft.day, !state.busy, { event(ProfileEvent.Open(it)) })
                OutlinedTextField(draft.weight, { event(ProfileEvent.Edit(draft.copy(weight = it))) }, singleLine = true,
                    enabled = !state.busy, label = { Text("Вес, кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(draft.fat, { event(ProfileEvent.Edit(draft.copy(fat = it))) }, singleLine = true,
                    enabled = !state.busy, label = { Text("Жир, %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Можно заполнить одно поле. На каждую дату сохраняется один замер.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !state.busy && draft.valid && draft.day <= LocalDate.now(), onClick = { event(ProfileEvent.Save) }) { Text("Сохранить замер") }
                TextButton(enabled = !state.busy, onClick = { event(ProfileEvent.Close) }) { Text("Отмена") }
                if (state.stats?.measurements?.any { it.day == draft.day } == true) {
                    TextButton(enabled = !state.busy, onClick = { confirm = true }) { Text("Удалить замер") }
                }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Удалить замер?") },
                    text = { Text(draft.day.caption()) },
                    confirmButton = { TextButton(onClick = { confirm = false; event(ProfileEvent.Delete) }) { Text("Удалить") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
            }
        } else LazyColumn(body, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text("Мой профиль", style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((days,label) in listOf(7 to "7 дней",30 to "30 дней",0 to "Всё время")) {
                        FilterChip(selected = state.days == days, enabled = !state.busy,
                            onClick = { event(ProfileEvent.Period(days)) }, label = { Text(label) })
                    }
                }
            }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { event(ProfileEvent.Period(state.days)) }) { Text("Повторить") }
            } }
            val profile = state.stats
            if (profile != null && !state.busy) {
                val stats = profile.current
                profile.previous?.let { previous ->
                    item { ProfileBlock("Изменения за период") {
                        PeriodComparison(stats, previous, profile.end, state.days)
                    } }
                }
                item { ProfileBlock("Тренировки") {
                    Text("Занятий с выполненными подходами: ${stats.workouts}", style = MaterialTheme.typography.titleMedium)
                    Text("Подходов: ${stats.sets} · Повторений: ${stats.reps}")
                    Text("Объём с отягощением: ${stats.volume.display()} кг", style = MaterialTheme.typography.bodyMedium)
                } }
                item { ProfileBlock("Жим штанги лёжа") {
                    if (stats.benchSets == 0) Text("За период нет выполненных рабочих подходов жима штанги лёжа.")
                    else {
                        Text("Средний рабочий вес: ${stats.benchAverage?.display()} кг", style = MaterialTheme.typography.titleLarge)
                        Text("Лучший рабочий вес: ${stats.benchMax?.display()} кг · Подходов: ${stats.benchSets}")
                        Text("Лучшие подходы за период", style = MaterialTheme.typography.titleMedium)
                        stats.benchRecords.forEach { (reps, weight) -> Text("${weight.display()} кг × $reps повт.") }
                    }
                } }
                item { ProfileBlock("Питание сегодня") {
                    if (profile.todayEntries.isEmpty()) Text("Сегодня пока нет записей") else {
                        Text("${profile.todayEntries.sumOf { it.calories }.display()} ккал")
                        DailyNutrition(profile.todayEntries.dailyMacros())
                    }
                } }
                item { ProfileBlock("Питание") {
                    if (stats.averageCalories != null) {
                        Text("${stats.averageCalories.display()} ккал в среднем", style = MaterialTheme.typography.titleLarge)
                        if (stats.averageProtein != null) {
                            Text("Белок · ${stats.averageProtein.display()} г в среднем за день")
                            Text("Дней с полными данными по белку: ${stats.proteinDays} из ${stats.foodDays}",
                                style = MaterialTheme.typography.bodySmall)
                        } else Text("Для среднего белка нужен хотя бы один день с белком во всех записях.")
                        Text("Дней с записями: ${stats.foodDays}")
                    } else Text("За период нет записей питания.")
                    HorizontalDivider()
                    Text("Когда я обычно ем", style = MaterialTheme.typography.titleMedium)
                    val peak = profile.hours.maxOrNull() ?: 0
                    if (peak == 0) Text("Укажи время еды в дневнике, чтобы увидеть распределение по часам.")
                    else {
                        profile.hours.forEachIndexed { hour, count -> if (count > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(hour.toString().padStart(2,'0') + ":00", style = MaterialTheme.typography.labelLarge)
                                LinearProgressIndicator(progress = { count.toFloat() / peak }, modifier = Modifier.weight(1f))
                                Text(count.toString())
                            }
                        } }
                        Text("Число приёмов пищи по часам. Блюда одной категории в течение одного часа объединены.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } }
                item { ProfileBlock("Мои замеры") {
                    val weight = profile.measurements.firstOrNull { it.weight.isNotEmpty() }
                    val fat = profile.measurements.firstOrNull { it.fat.isNotEmpty() }
                    if (weight == null && fat == null) Text("Добавь первый замер, чтобы отслеживать изменения.")
                    weight?.let { Text("Вес: ${it.weight} кг · ${it.day.caption()}") }
                    fat?.let { Text("Жир: ${it.fat}% · ${it.day.caption()}") }
                    MeasurementTrends(profile.measurements, profile.end, state.days)
                    Button(onClick = { event(ProfileEvent.Open(LocalDate.now())) }) { Text("Записать замер") }
                    if (profile.measurements.isNotEmpty()) TextButton(onClick = { history = !history }) {
                        Text(if (history) "Скрыть историю" else "История всех замеров")
                    }
                } }
                if (history) items(profile.measurements, key = { it.day.toEpochDay() }) { m ->
                    OutlinedCard(onClick = { event(ProfileEvent.Open(m.day)) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.day.caption(), style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(m.weight.takeIf(String::isNotEmpty)?.let { "$it кг" },
                                m.fat.takeIf(String::isNotEmpty)?.let { "Жир $it%" }).joinToString(" · "))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
