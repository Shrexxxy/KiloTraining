package app.calorietraining

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
internal fun ProfileScreen(
    model: ProfileViewModel, onSettings: () -> Unit,
    personalData: UserProfile? = null, lastMeasurement: Measurement? = null, onRecalculate: ((Measurement?) -> Unit)? = null,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { if (model.uiState.value.draft == null) model.refresh() }
    Box {
        ProfileContent(state, model::event, onSettings, myData = onRecalculate?.let { recalculate ->
            {
                val measurement = state.stats?.let { stats ->
                    stats.measurements.firstOrNull { it.weight.isNotEmpty() && it.day <= LocalDate.now() }
                } ?: lastMeasurement.takeIf { state.stats == null }
                MyDataBlock(personalData, measurement) { recalculate(measurement) }
            }
        })
        DelayedProgress(state.busy)
    }
}

@Composable
private fun ProfileContent(state: ProfileState, event: (ProfileEvent) -> Unit,
                           onSettings: () -> Unit, modifier: Modifier = Modifier,
                           myData: (@Composable () -> Unit)?) {
    val draft = state.draft
    var history by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { padding ->
        val body = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        if (draft != null) {
            var confirm by rememberSaveable(draft.day.toEpochDay()) { mutableStateOf(false) }
            Column(body.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenHeader("Замер тела", onBack = { event(ProfileEvent.Close) })
                DiaryDaySelector(draft.day, true, { event(ProfileEvent.Open(it)) })
                DiaryTextField(draft.weight, { event(ProfileEvent.Edit(draft.copy(weight = it))) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Вес, кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                DiaryTextField(draft.fat, { event(ProfileEvent.Edit(draft.copy(fat = it))) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Жир, %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Можно заполнить одно поле. На каждую дату сохраняется один замер.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = draft.valid && draft.day <= LocalDate.now(), onClick = { event(ProfileEvent.Save) }) { Text("Сохранить замер") }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Профиль", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Настройки")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((days,label) in listOf(7 to "7 дней",30 to "30 дней",0 to "Всё время")) {
                        FilterChip(selected = state.days == days, onClick = { event(ProfileEvent.Period(days)) }, label = { Text(label) })
                    }
                }
            }
            if (myData != null) item { myData() }
            state.error?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { event(ProfileEvent.Period(state.days)) }) { Text("Повторить") }
            } }
            val profile = state.stats
            if (profile != null) {
                val stats = profile.current
                profile.previous?.takeIf { state.days == 7 || state.days == 30 }?.let { previous ->
                    item { ProfileBlock("Сравнение периодов", LocalDiaryAccents.current.kcal) {
                        PeriodComparison(stats, previous, profile.end, state.days)
                    } }
                }
                item { ProfileBlock("Тренировки", LocalDiaryAccents.current.workout) {
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
                item { ProfileBlock("Жим штанги лёжа", LocalDiaryAccents.current.workout) {
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
                item { ProfileBlock("Питание", LocalDiaryAccents.current.kcal) {
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
                item { ProfileBlock("Мои замеры", LocalDiaryAccents.current.protein) {
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
private fun MyDataBlock(profile: UserProfile?, measurement: Measurement?, recalculate: () -> Unit) {
    ProfileBlock("Мои данные", LocalDiaryAccents.current.kcal) {
        val values = listOf(
            "Цель" to profile?.kind?.label,
            "Пол" to profile?.sex?.label,
            "Возраст" to profile?.let { (LocalDate.now().year - it.birthYear).toString() },
            "Рост" to profile?.let { "${it.heightCm} см" },
            "Активность" to profile?.activity?.label,
            "Последний вес" to measurement?.let { "${it.weight} кг" },
        )
        values.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                Text(value ?: "—", Modifier.weight(1.35f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(recalculate, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("Пересчитать цель") }
    }
}

@Composable
internal fun ProfileBlock(
    title: String,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(accent, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier, caption: String? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHighest.copy(alpha = .55f)) {
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
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHighest.copy(alpha = .55f)) {
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
