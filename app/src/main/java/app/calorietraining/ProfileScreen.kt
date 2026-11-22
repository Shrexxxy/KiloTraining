package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Показывает статистику профиля и выбор темы дневника. */
@Composable
fun ProfileScreen(model: ProfileViewModel, mode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { if (model.uiState.value.draft == null) model.refresh() }
    Box {
        ProfileContent(state, model::event, mode, onThemeChange, server = { ProductServerBlock(model) })
        DelayedProgress(state.busy)
    }
}

@Composable
private fun ProfileContent(state: ProfileState, event: (ProfileEvent) -> Unit,
                           mode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier,
                           server: @Composable () -> Unit) {
    val draft = state.draft
    var history by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        if (draft != null) {
            BackHandler { event(ProfileEvent.Close) }
            var confirm by rememberSaveable(draft.day.toEpochDay()) { mutableStateOf(false) }
            Column(body.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Замер тела", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                DiaryDaySelector(draft.day, true, { event(ProfileEvent.Open(it)) })
                OutlinedTextField(draft.weight, { event(ProfileEvent.Edit(draft.copy(weight = it))) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Вес, кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(draft.fat, { event(ProfileEvent.Edit(draft.copy(fat = it))) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Жир, %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Можно заполнить одно поле. На каждую дату сохраняется один замер.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = draft.valid && draft.day <= LocalDate.now(), onClick = { event(ProfileEvent.Save) }) { Text("Сохранить замер") }
                TextButton(onClick = { event(ProfileEvent.Close) }) { Text("Отмена") }
                if (state.stats?.measurements?.any { it.day == draft.day } == true) {
                    TextButton(onClick = { confirm = true }) { Text("Удалить замер") }
                }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Удалить замер?") },
                    text = { Text(draft.day.caption()) },
                    confirmButton = { TextButton(onClick = { confirm = false; event(ProfileEvent.Delete) }) { Text("Удалить") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
            }
        } else LazyColumn(body, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text("Профиль", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((days,label) in listOf(7 to "7 дней",30 to "30 дней",0 to "Всё время")) {
                        FilterChip(selected = state.days == days, onClick = { event(ProfileEvent.Period(days)) }, label = { Text(label) })
                    }
                }
            }
            item { ProfileBlock("Оформление") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { onThemeChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) {
                            Text(when (option) {
                                ThemeMode.SYSTEM -> "Авто"
                                ThemeMode.LIGHT -> "Светлая"
                                ThemeMode.DARK -> "Тёмная"
                            }, maxLines = 1, softWrap = false)
                        }
                    }
                }
            } }
            item { server() }
            state.error?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { event(ProfileEvent.Period(state.days)) }) { Text("Повторить") }
            } }
            val profile = state.stats
            if (profile != null) {
                val stats = profile.current
                profile.previous?.takeIf { state.days == 7 || state.days == 30 }?.let { previous ->
                    item { ProfileBlock("Сравнение периодов") {
                        PeriodComparison(stats, previous, profile.end, state.days)
                    } }
                }
                item { ProfileBlock("Тренировки") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileMetric("ЗАНЯТИЙ", stats.workouts.toBigDecimal().groupedNumber(), Modifier.weight(1f))
                        ProfileMetric("ПОДХОДОВ", stats.sets.toBigDecimal().groupedNumber(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileMetric("ПОВТОРЕНИЙ", stats.reps.toBigDecimal().groupedNumber(), Modifier.weight(1f))
                        ProfileMetric("ОБЪЁМ", "${stats.volume.groupedNumber()} кг", Modifier.weight(1f))
                    }
                    stats.averageMinutes?.let { average ->
                        val hours = stats.totalMinutes / 60
                        val minutes = stats.totalMinutes % 60
                        Text("Время занятий · ${if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"}")
                        Text("В среднем · ${average.display()} мин")
                        Text("Длительность указана у ${stats.timedWorkouts} из ${stats.workouts} занятий",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } }
                item { ProfileBlock("Жим штанги лёжа") {
                    if (stats.benchSets == 0) Text("За период нет выполненных рабочих подходов жима штанги лёжа.")
                    else {
                        ProfileValue("СРЕДНИЙ РАБОЧИЙ ВЕС", "${stats.benchAverage?.groupedNumber()} кг",
                            LocalDiaryAccents.current.workout)
                        Text("Лучший: ${stats.benchMax?.groupedNumber()} кг · Подходов: ${stats.benchSets}")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            stats.benchRecords.forEach { (reps, weight) ->
                                WorkoutSetTile(weight.groupedNumber(), reps.toString())
                            }
                        }
                    }
                } }
                item { ProfileBlock("Питание") {
                    val accents = LocalDiaryAccents.current
                    if (stats.averageCalories != null) {
                        ProfileValue("В СРЕДНЕМ ЗА ДЕНЬ", "${stats.averageCalories.groupedNumber()} ккал", accents.kcal)
                        Text("Дней с записями: ${stats.foodDays}", style = MaterialTheme.typography.bodySmall)
                        if (stats.averageProtein != null) {
                            ProfileValue("БЕЛОК В СРЕДНЕМ ЗА ДЕНЬ", "${stats.averageProtein.groupedNumber()} г", accents.protein)
                            Text("Дней с полными данными по белку: ${stats.proteinDays} из ${stats.foodDays}",
                                style = MaterialTheme.typography.bodySmall)
                        } else Text("Для среднего белка нужен хотя бы один день с белком во всех записях.")
                    } else Text("За период нет записей питания.")
                    HorizontalDivider()
                    if (profile.todayEntries.isEmpty()) Text("Сегодня пока нет записей") else {
                        Text("Сегодня: ${profile.todayEntries.sumOf { it.calories }.groupedNumber()} ккал")
                        DailyNutrition(profile.todayEntries.dailyMacros())
                    }
                    MealHours(profile.hours)
                } }
                item { ProfileBlock("Мои замеры") {
                    val weight = profile.measurements.firstOrNull { it.weight.isNotEmpty() }
                    val fat = profile.measurements.firstOrNull { it.fat.isNotEmpty() }
                    if (weight == null && fat == null) Text("Добавь первый замер, чтобы отслеживать изменения.")
                    if (weight != null || fat != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            weight?.let { ProfileMetric("ВЕС", "${it.weight} кг", Modifier.weight(1f), it.day.caption()) }
                            fat?.let { ProfileMetric("ЖИР", "${it.fat}%", Modifier.weight(1f), it.day.caption()) }
                        }
                    }
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
private fun ProductServerBlock(model: ProfileViewModel) {
    val state by model.productServerState.collectAsStateWithLifecycle()
    val configured = state.address.isNotBlank()
    ProfileBlock("Сервер продуктов") {
        OutlinedTextField(state.address, model::changeServerAddress, Modifier.fillMaxWidth(),
            enabled = !state.busy, label = { Text("Адрес") },
            placeholder = { Text("http://192.168.1.10:5080") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(state.key, model::changeServerKey, Modifier.fillMaxWidth(),
            enabled = !state.busy, label = { Text("Ключ") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TextButton(onClick = model::checkProductServer, enabled = configured && !state.busy) {
            Text("Проверить связь")
        }
        Text(if (configured) state.status.orEmpty().ifBlank { "Связь не проверена" } else "Не настроен")
        state.pending?.let { Text("Ждут отправки: $it") }
        val lastSent = state.lastSent?.let {
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(it)
        } ?: "—"
        Text("Последняя отправка: $lastSent")
        if (state.unsuitable > 0) Text("Не подходят для отправки: ${state.unsuitable}")
        TextButton(onClick = model::sendProducts, enabled = configured && !state.busy) {
            Text("Отправить сейчас")
        }
    }
}

@Composable
private fun ProfileBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier, caption: String? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHighest) {
        Column(Modifier.padding(10.dp)) {
            ProfileValue(label, value, colors.onSurface)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileValue(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MealHours(hours: List<Int>) {
    val colors = MaterialTheme.colorScheme
    val kcal = LocalDiaryAccents.current.kcal
    val peak = hours.maxOrNull() ?: 0
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Когда я обычно ем", style = MaterialTheme.typography.titleSmall)
            if (peak == 0) Text("Укажи время еды в дневнике, чтобы увидеть распределение по часам.")
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    hours.forEachIndexed { hour, count ->
                        Box(Modifier.weight(1f).height(64.dp)
                            .background(colors.surfaceContainer, MaterialTheme.shapes.extraSmall)
                            .semantics { contentDescription = "$hour:00, приёмов пищи: $count" },
                            contentAlignment = Alignment.BottomCenter) {
                            if (count > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(count.toFloat() / peak)
                                .background(kcal, MaterialTheme.shapes.extraSmall))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (hour in listOf("00:00", "06:00", "12:00", "18:00", "23:00")) {
                        Text(hour, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("Число приёмов пищи по часам. Блюда одной категории в течение одного часа объединены.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
