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
import androidx.compose.ui.platform.testTag
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
                MyDataBlock(personalData, measurement, state.stats?.series?.bmi) { recalculate(measurement) }
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
                DiaryDaySelector(draft.day, true, { event(if (state.fatOnly) ProfileEvent.OpenFat(it) else ProfileEvent.Open(it)) })
                if (!state.fatOnly) DiaryTextField(draft.weight, { event(ProfileEvent.Edit(draft.copy(weight = it))) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Вес, кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                DiaryTextField(draft.fat, { event(ProfileEvent.Edit(draft.copy(fat = it))) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Жир, %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text(if (state.fatOnly) "Вес этого дня не меняется." else "Можно заполнить одно поле. На каждую дату сохраняется один замер.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = draft.valid && draft.day <= LocalDate.now(), onClick = { event(ProfileEvent.Save) }) { Text("Сохранить замер") }
                if (!state.fatOnly && state.stats?.measurements?.any { it.day == draft.day } == true) {
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
                        DiaryFilterChip(selected = state.days == days, onClick = { event(ProfileEvent.Period(days)) }, label = { Text(label) })
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
                val series = profile.series
                val caloriePeriod = when {
                    state.days != 0 -> "по дням"
                    series.calories.size > 1 && series.calories[0].day.plusMonths(1) == series.calories[1].day -> "по месяцам"
                    else -> "по неделям"
                }
                item { ProfileBlock("Калории $caloriePeriod", LocalDiaryAccents.current.kcal) {
                    CaloriesChart(series, stats, caloriePeriod)
                    if (profile.todayEntries.isNotEmpty()) {
                        ChartLabel("Сегодня: ${profile.todayEntries.sumOf { it.calories }.groupedNumber()} ккал")
                    }
                } }
                item { ProfileBlock("БЖУ", LocalDiaryAccents.current.protein) { MacrosChart(series) } }
                item { ProfileBlock("Мои замеры", LocalDiaryAccents.current.protein) {
                    val weight = profile.measurements.firstOrNull { it.weight.isNotEmpty() }
                    val fat = profile.measurements.firstOrNull { it.fat.isNotEmpty() }
                    if (weight == null && fat == null) Text("Добавь первый замер.")
                    if (weight != null || fat != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            weight?.let { ProfileMetric("ВЕС", "${it.weight.amount()?.display() ?: it.weight} кг", Modifier.weight(1f), it.day.caption()) }
                            fat?.let { ProfileMetric("ЖИР", "${it.fat.amount()?.display() ?: it.fat}%", Modifier.weight(1f), it.day.caption()) }
                        }
                    }
                    MeasurementTrends(profile.measurements, profile.end, state.days)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { event(ProfileEvent.Open(LocalDate.now())) }, Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { Text("Записать замер") }
                        OutlinedButton(onClick = { event(ProfileEvent.OpenFat(LocalDate.now())) }, Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { Text("Записать % жира") }
                    }
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
                item { ProfileBlock("Тренировки", LocalDiaryAccents.current.workout) {
                    if (stats.workouts == 0) Text("Добавь тренировку.")
                    else {
                        val color = LocalDiaryAccents.current.workout
                        val points = series.workouts
                        val period = when {
                            state.days == 7 -> "по дням"
                            points.size > 1 && points[0].day.plusMonths(1) == points[1].day -> "по месяцам"
                            else -> "по неделям"
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stats.workouts.toBigDecimal().groupedNumber(), style = MaterialTheme.typography.titleLarge, color = color)
                            ChartLabel("Занятия $period")
                        }
                        ChartBars(points.map { it.count.takeIf { count -> count > 0 }?.toFloat() }, color,
                            "Тренировки $period, занятий ${stats.workouts}")
                        ChartDates(points.first().day, points.last().day)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileMetric("ПОДХОДОВ", stats.sets.toBigDecimal().groupedNumber(), Modifier.weight(1f))
                            ProfileMetric("ПОВТОРЕНИЙ", stats.reps.toBigDecimal().groupedNumber(), Modifier.weight(1f))
                            ProfileMetric("ОБЪЁМ", "${stats.volume.groupedNumber()} кг", Modifier.weight(1f))
                        }
                        stats.averageMinutes?.let { average ->
                            ChartLabel("Время · ${stats.totalMinutes} мин · в среднем ${average.display()} мин")
                        }
                    }
                } }
                state.weeklyMuscles?.let { load -> item { WeeklyMusclesBlock(load) } }
                item { ProfileBlock("Жим штанги лёжа", LocalDiaryAccents.current.workout) {
                    if (series.bench.isEmpty()) Text("Добавь рабочий подход жима лёжа.")
                    else {
                        TrendChart("Лучший рабочий вес", "кг", LocalDiaryAccents.current.workout,
                            series.bench.map { it.day to it.weight })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            stats.benchRecords.forEach { (reps, weight) ->
                                WorkoutSetTile(weight.groupedNumber(), reps.toString())
                            }
                        }
                    }
                } }
                profile.previous?.takeIf { state.days == 7 || state.days == 30 }?.let { previous ->
                    item { ProfileBlock("Сравнение периодов", LocalDiaryAccents.current.kcal) {
                        PeriodComparison(stats, previous, state.days)
                    } }
                }
                item { ProfileBlock("Когда я обычно ем", LocalDiaryAccents.current.kcal) { MealHours(profile.hours) } }
            }
        }
    }
}

@Composable
private fun WeeklyMusclesBlock(load: MuscleLoad) {
    val accent = LocalDiaryAccents.current.workout
    ProfileBlock("Мышцы за неделю", accent) {
        if (load.scores.isNotEmpty()) {
            val ranked = load.scores.entries.sortedWith(
                compareByDescending<Map.Entry<Muscle, java.math.BigDecimal>> { it.value }.thenBy { it.key },
            )
            val maxScore = ranked.first().value.toFloat()
            val description = ranked.joinToString { (muscle, score) -> "${muscle.label} ${score.display()}" }
            MuscleDiagram("Нагрузка мышц за 7 дней: $description") { muscle ->
                val score = load.scores[muscle]
                if (score == null) Color.Transparent else accent.copy(alpha = 0.25f + 0.75f * (score.toFloat() / maxScore))
            }
            for ((muscle, score) in ranked.take(5)) {
                Text("${muscle.label.replaceFirstChar { it.uppercaseChar() }} — ${score.display()}")
            }
            ChartLabel("Подходы: основная мышца 1, дополнительная 0,5")
        } else if (load.unknownSets == 0) {
            Text("За 7 дней нет рабочих подходов")
        }
        if (load.unknownSets > 0) {
            Text("Подходов без данных о мышцах: ${load.unknownSets}")
        }
    }
}

@Composable
private fun MyDataBlock(profile: UserProfile?, measurement: Measurement?, savedBmi: Bmi?, recalculate: () -> Unit) {
    ProfileBlock("Мои данные", LocalDiaryAccents.current.kcal) {
        val color = LocalDiaryAccents.current.kcal
        val goal = when (profile?.kind) {
            GoalKind.LOSE -> "Похудеть"
            GoalKind.MAINTAIN -> "Поддерживать"
            GoalKind.GAIN -> "Набрать"
            null -> "—"
        }
        Surface(Modifier.fillMaxWidth(), color = color.copy(alpha = .14f), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (profile != null) {
                    Icon(painterResource(R.drawable.ic_goal), contentDescription = null,
                        modifier = Modifier.testTag("goal-icon"), tint = color)
                }
                ProfileValue("ЦЕЛЬ", goal, MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProfileMetric("ВОЗРАСТ", profile?.let { (LocalDate.now().year - it.birthYear).toString() } ?: "—", Modifier.weight(1f))
            ProfileMetric("РОСТ", profile?.let { "${it.heightCm} см" } ?: "—", Modifier.weight(1f))
            ProfileMetric("ВЕС", measurement?.weight?.amount()?.let { "${it.display()} кг" } ?: "—", Modifier.weight(1f))
        }
        val bmi = savedBmi ?: profileBmi(profile?.heightCm, measurement?.weight?.amount())
        if (bmi != null) {
            Text("ИМТ · ${bmi.value.display()}", style = MaterialTheme.typography.titleSmall)
            BmiChart(bmi)
        }
        if (profile != null) {
            ActivityChart(profile.activity, color)
            ChartLabel(profile.activity.label)
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
            if (peak == 0) Text("Добавь время еды.")
            else {
                Row(Modifier.fillMaxWidth().chartDescription(
                    "Приёмы пищи по часам, всего ${hours.sum()}, пик в ${hours.indexOf(peak)}:00, приёмов $peak"
                ), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
            }
        }
    }
}
