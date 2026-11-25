package app.calorietraining

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val workoutTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun Workout.dateTimeCaption() = day.caption() + (startedAt?.let { " · ${it.format(workoutTimeFormat)}" } ?: "")

@Composable
fun WorkoutContent(state: WorkoutState, event: (WorkoutEvent) -> Unit, modifier: Modifier = Modifier) {
    var recording by rememberSaveable { mutableStateOf(false) }
    val opened = state.list.firstOrNull { it.id == state.opened }
    val session = state.session
    val draft = state.draft
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            DelayedProgress(state.busy)
            when {
                session != null && draft != null -> SetEditor(state, session, draft, event)
                opened != null -> key(opened.id) { WorkoutDetails(state, opened, event) }
                else -> WorkoutFeed(state, event, onRecord = { recording = true })
            }
        }
    }
    if (recording) WorkoutDateTimeDialog("Записать тренировку", "Записать", LocalDateTime.now(state.clock),
        onClose = { recording = false }, onConfirm = { recording = false; event(WorkoutEvent.Record(it)) })
}

@Composable
private fun ColumnScope.WorkoutFeed(state: WorkoutState, event: (WorkoutEvent) -> Unit, onRecord: () -> Unit) {
    var jumping by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    var jumped by rememberSaveable { mutableStateOf(false) }
    val accent = LocalDiaryAccents.current.workout
    val active = state.activeWorkout
    val now = workoutNow(state, active, event)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Тренировки", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        TextButton(enabled = state.list.isNotEmpty(), onClick = { jumping = true }) { Text("К дате") }
    }
    if (jumping) WorkoutCalendar(LocalDate.now(state.clock), { jumping = false }) { day ->
        jumping = false
        val index = state.list.indexOfFirst { it.day <= day }.takeIf { it >= 0 } ?: state.list.lastIndex
        if (index >= 0) {
            jumped = true
            scroll.requestScrollToItem(index + 1)
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    BoxWithConstraints(Modifier.weight(1f)) {
        LazyColumn(Modifier.fillMaxSize(), state = scroll,
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "workout-controls") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (active == null) Button(onClick = { event(WorkoutEvent.StartLive) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Начать тренировку") }
                    else GlassCard(accent, Modifier.fillMaxWidth(), onClick = { event(WorkoutEvent.Open(active.id)) }) {
                        Text("Идёт тренировка · ${formatRestElapsed(Duration.between(active.startedAt ?: now, now).seconds.coerceAtLeast(0))}",
                            Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    GlassTonalButton(onClick = onRecord, modifier = Modifier.fillMaxWidth(), contentPadding = ButtonDefaults.ContentPadding, enabled = !state.busy) {
                        Text("Записать тренировку")
                    }
                    WorkoutTemplateMenu(state, event)
                }
            }
            items(state.list, key = { it.id }) { workout ->
                GlassCard(accent, Modifier.fillMaxWidth(), onClick = { event(WorkoutEvent.Open(workout.id)) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(workout.dateTimeCaption() + if (workout.active) " · Идёт" else "",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(workout.title.ifBlank { "Тренировка" }, style = MaterialTheme.typography.titleLarge)
                        WorkoutMetrics(workout, workout.elapsedMinutes(now))
                        val exercises = workout.sets.map { it.exercise }.distinct()
                        if (exercises.isNotEmpty()) Text(
                            exercises.take(3).joinToString() + if (exercises.size > 3) " и ещё ${exercises.size - 3}" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (jumped) item(key = "jump-space") { Spacer(Modifier.height(maxHeight)) }
        }
    }
}

private fun Workout.elapsedMinutes(now: LocalDateTime): Long? =
    if (active) startedAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) }
    else minutes?.toLong()

@Composable
private fun workoutNow(state: WorkoutState, workout: Workout?, event: (WorkoutEvent) -> Unit): LocalDateTime {
    var now by remember(state.clock, workout?.lastDoneAt) { mutableStateOf(LocalDateTime.now(state.clock)) }
    LaunchedEffect(state.clock, workout?.id, workout?.active, workout?.lastDoneAt) {
        while (workout?.active == true) {
            now = LocalDateTime.now(state.clock)
            val last = workout.lastDoneAt ?: workout.startedAt
            if (last != null && now >= last.plusHours(3)) {
                event(WorkoutEvent.Refresh)
                break
            }
            delay(1000)
        }
    }
    return now
}

@Composable
private fun WorkoutDetails(state: WorkoutState, workout: Workout, event: (WorkoutEvent) -> Unit) {
    var exerciseDetails by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val now = workoutNow(state, workout, event)
    exerciseDetails?.let { name ->
        val photo = photoForExercise(name)
        if (photo != null) ExerciseMusclesScreen(photo, onBack = { exerciseDetails = null })
        else CustomMusclesScreen(name, onBack = { exerciseDetails = null })
        return
    }
    var finishing by rememberSaveable { mutableStateOf(false) }
    WorkoutActions(state, workout, event)
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (finishing) AlertDialog(onDismissRequest = { finishing = false },
        title = { Text("Завершить тренировку?") },
        confirmButton = { TextButton(onClick = { finishing = false; event(WorkoutEvent.Finish(workout.id)) }) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = { finishing = false }) { Text("Отмена") } })
    val rests = remember(workout.sets) { workout.restBySet() }
    val exercises = remember(workout.sets) {
        workout.sets.groupBy { it.group }.values
            .sortedByDescending { sets -> sets.minOf { it.id ?: 0L } }
            .map { it.inExecutionOrder() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (workout.active) {
            workout.lastDoneAt?.let { done ->
                item(key = "rest-timer") { WorkoutRestTimer(Duration.between(done, now).seconds.coerceAtLeast(0)) }
            }
            item(key = "finish") {
                GlassTonalButton(onClick = { finishing = true }, modifier = Modifier.fillMaxWidth(),
                    contentPadding = ButtonDefaults.ContentPadding) {
                    Text("Завершить тренировку")
                }
            }
        }
        item(key = "metrics") {
            GlassCard(LocalDiaryAccents.current.workout) { WorkoutMetrics(workout, workout.elapsedMinutes(now)) }
        }
        workout.nextPlanned()?.let { next ->
            item(key = "next") {
                NextSetCard(next, next.id != null) { next.id?.let { event(WorkoutEvent.Complete(workout.id, it)) } }
            }
        }
        item(key = "add-exercise") {
            Button(onClick = { event(WorkoutEvent.Edit(workout.id, SetDraft())) }, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить упражнение")
            }
        }
        items(exercises, key = { it.first().group }) { sets ->
            ExerciseCard(sets, rests, onMuscles = { exerciseDetails = it }) { event(WorkoutEvent.Edit(workout.id, it)) }
        }
    }
}

@Composable
private fun WorkoutActions(state: WorkoutState, workout: Workout, event: (WorkoutEvent) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    var savingTemplate by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    val label = workout.title.ifBlank { "Тренировка" }
    var editingDuration by rememberSaveable { mutableStateOf(false) }
    if (editingDuration) {
        var input by rememberSaveable { mutableStateOf(workout.minutes?.toString().orEmpty()) }
        val minutes = input.toIntOrNull()
        val valid = input.isEmpty() || minutes in workoutMinutesRange
        AlertDialog(onDismissRequest = { editingDuration = false },
            title = { Text("Длительность тренировки") },
            text = {
                DiaryTextField(input, { input = it }, singleLine = true, isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Минуты") }, supportingText = {
                        Text(if (valid) "Можно оставить пустым" else "Введите от 1 до 1440 минут")
                    })
            },
            confirmButton = { TextButton(enabled = valid, onClick = {
                event(WorkoutEvent.Duration(workout.id, minutes))
                editingDuration = false
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editingDuration = false }) { Text("Отмена") } })
    }
    var editingStart by rememberSaveable { mutableStateOf(false) }
    if (editingStart) WorkoutDateTimeDialog(
        "Изменить время начала", "Сохранить", workout.startedAt ?: workout.day.atStartOfDay(),
        onClose = { editingStart = false },
        onConfirm = { event(WorkoutEvent.ChangeStart(workout.id, it)); editingStart = false },
    )
    var menu by remember { mutableStateOf(false) }
    var moveDate by rememberSaveable(workout.day.toEpochDay()) { mutableStateOf<Long?>(null) }
    var repeating by rememberSaveable { mutableStateOf(false) }
    moveDate?.let { epoch ->
        val destination = java.time.LocalDate.ofEpochDay(epoch)
        AlertDialog(onDismissRequest = { moveDate = null },
            title = { Text(if (repeating) "Повторить тренировку" else "Перенести тренировку") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$label · ${workout.day.caption()}")
                    if (repeating) Text("Подходов в плане: ${workout.sets.count { !it.planned }}. Отмечайте их по мере выполнения.")
                    Text("Новая дата", style = MaterialTheme.typography.labelLarge)
                    DiaryDaySelector(destination, true, { moveDate = it.toEpochDay() })
                }
            },
            confirmButton = {
                Button(enabled = (repeating || destination != workout.day), onClick = {
                    moveDate = null
                    event(if (repeating) WorkoutEvent.Repeat(workout.id, destination)
                        else WorkoutEvent.Move(workout.id, destination))
                }) { Text(if (repeating) "Создать план" else "Перенести") }
            },
            dismissButton = { TextButton(onClick = { moveDate = null }) { Text("Отмена") } })
    }
    if (replacing) TemplateChangeDialog(state.templates, label, onClose = { replacing = false },
        onChange = { template, _ ->
            event(WorkoutEvent.ReplaceTemplate(template.id, workout.id))
            replacing = false
        })
    if (renaming || savingTemplate) AlertDialog(
        onDismissRequest = { renaming = false; savingTemplate = false },
        title = { Text(if (savingTemplate) "Название шаблона" else "Название тренировки") },
        text = {
            DiaryTextField(title, { title = it }, singleLine = true,
                label = { Text("Название") }, placeholder = { Text("Спина и бицепс") })
        },
        confirmButton = { TextButton(enabled = (!savingTemplate || title.isNotBlank()), onClick = {
            event(if (savingTemplate) WorkoutEvent.SaveTemplate(workout.id, title) else WorkoutEvent.Rename(workout.id, title))
            renaming = false
            savingTemplate = false
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { renaming = false; savingTemplate = false }) { Text("Отмена") } }
    )
    ScreenHeader(label, onBack = { event(WorkoutEvent.BackToList) }, subtitle = workout.dateTimeCaption()) {
        Box {
            TextButton(onClick = { menu = true }) { Text("Действия") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (workout.title.isBlank()) "Задать название" else "Переименовать") },
                    onClick = { menu = false; confirm = false; title = workout.title; renaming = true })
                DropdownMenuItem(text = { Text("Изменить время начала") }, onClick = {
                    menu = false
                    editingStart = true
                })
                DropdownMenuItem(text = { Text("Длительность") }, onClick = {
                    menu = false
                    editingDuration = true
                })
                DropdownMenuItem(text = { Text("Сохранить как шаблон") }, enabled = workout.sets.isNotEmpty(),
                    onClick = { menu = false; title = workout.title; savingTemplate = true })
                if (state.templates.isNotEmpty()) DropdownMenuItem(text = { Text("Обновить шаблон") },
                    enabled = workout.sets.isNotEmpty(), onClick = { menu = false; replacing = true })
                DropdownMenuItem(text = { Text("Перенести на дату") },
                    onClick = { menu = false; confirm = false; repeating = false; moveDate = workout.day.toEpochDay() })
                DropdownMenuItem(text = { Text("Повторить") }, enabled = workout.sets.any { !it.planned },
                    onClick = { menu = false; confirm = false; repeating = true; moveDate = LocalDate.now(state.clock).toEpochDay() })
                DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; confirm = true })
            }
        }
    }
    if(confirm) {
        OutlinedButton(onClick={event(WorkoutEvent.Remove(workout.id))}) { Text("Удалить тренировку целиком") }
        TextButton(onClick = { confirm = false }) { Text("Отмена") }
    }
}

@Composable
private fun WorkoutTemplateMenu(state: WorkoutState, event: (WorkoutEvent) -> Unit) {
    if (state.templates.isNotEmpty()) {
        var changingName by rememberSaveable { mutableStateOf(false) }
        if (changingName) TemplateChangeDialog(state.templates, source = null, onClose = { changingName = false },
            onChange = { template, name ->
                event(WorkoutEvent.RenameTemplate(template.id, name))
                changingName = false
            })
        var choosingTemplate by rememberSaveable { mutableStateOf(false) }
        Box {
            GlassTonalButton(onClick = { choosingTemplate = true }, modifier = Modifier,
                contentPadding = ButtonDefaults.ContentPadding) { Text("Из шаблона") }
            DropdownMenu(expanded = choosingTemplate, onDismissRequest = { choosingTemplate = false }) {
                state.templates.forEach { template ->
                    DropdownMenuItem(text = { Text(template.title) }, onClick = {
                        choosingTemplate = false
                        event(WorkoutEvent.StartTemplate(template.id))
                    })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Переименовать шаблон") }, onClick = {
                    choosingTemplate = false
                    changingName = true
                })
            }
        }
    }
}

@Composable
private fun SetEditor(state: WorkoutState, session: Long, draft: SetDraft, event: (WorkoutEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var confirmDelete by rememberSaveable(draft.id) { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить подход?") },
            text = { Text("${draft.exercise} · ${draft.reps} повт.") },
            confirmButton = { TextButton(onClick = {
                confirmDelete = false
                event(WorkoutEvent.DeleteSet)
            }) { Text("Удалить", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Оставить") } }
        )
    }
    fun edit(d:SetDraft) = event(WorkoutEvent.Edit(session,d))
    var choosing by rememberSaveable(draft.group) { mutableStateOf(false) }
    if (choosing) {
        ExercisePicker(onSelect = { photo ->
            edit(draft.copy(exercise = photo.name))
            choosing = false
        }, onClose = { choosing = false })
        return
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val group = state.list.firstOrNull { it.id == session }?.sets.orEmpty()
            .filter { it.group == draft.group }.inExecutionOrder()
        val editing = draft.id != null
        val newExercise = !editing && group.isEmpty()
        ScreenHeader(
            title = when {
                editing -> "Изменить подход"
                newExercise -> "Новое упражнение"
                else -> "Новый подход · ${draft.exercise}"
            },
            onBack = { event(WorkoutEvent.Close) },
        )
        if (editing) {
            val status = when {
                draft.planned -> "в плане"
                draft.doneAt != null -> "выполнен ${draft.doneAt.format(workoutTimeFormat)}"
                else -> "выполнен"
            }
            Text("Подход ${group.indexOfFirst { it.id == draft.id } + 1} · $status")
        }
        val catalogExercise = photoForExercise(draft.exercise)
        if (!newExercise) {
            if (editing) Text(draft.exercise, style = MaterialTheme.typography.titleMedium)
        } else if (catalogExercise != null) {
            Text(draft.exercise, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { choosing = true }) { Text("Сменить") }
                TextButton(onClick = { edit(draft.copy(exercise = "")) }) { Text("Своё упражнение") }
            }
        } else {
            OutlinedButton(onClick = { choosing = true }) { Text("Выбрать из каталога") }
            DiaryTextField(draft.exercise, { edit(draft.copy(exercise = it)) },
                label = { Text("Название упражнения") }, singleLine = true)
        }
        if (newExercise) catalogExercise?.let { ExerciseFrames(it) }
        state.previous?.let { previous ->
            GlassCard(colors.onSurfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Прошлая тренировка · ${previous.day.caption()}", style = MaterialTheme.typography.titleMedium)
                    previous.sets.forEachIndexed { index, set ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            val weight = if (set.weight.isBlank()) "Свой вес" else "${set.weight} кг"
                            Text("${index + 1}.  $weight × ${set.reps}" + if (set.warmup) " · разминка" else "")
                            TextButton(onClick = {
                                edit(draft.copy(weight = set.weight, reps = set.reps, warmup = set.warmup))
                            }) { Text("Повторить") }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiaryFilterChip(selected = !draft.warmup, onClick = { edit(draft.copy(warmup = false)) }, label = { Text("Рабочий") })
            DiaryFilterChip(selected = draft.warmup, onClick = { edit(draft.copy(warmup = true)) }, label = { Text("Разминочный") })
        }
        DiaryTextField(draft.weight,{edit(draft.copy(weight=it))},label={Text(if (usesDumbbell(draft.exercise)) "Вес одной гантели, кг" else "Вес, кг")},singleLine=true,
            supportingText={Text("Оставьте пустым для своего веса")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        DiaryTextField(draft.reps,{edit(draft.copy(reps=it))},label={Text("Повторы")},singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        state.error?.let {Text(it,color=colors.error)}
        Button(enabled = draft.valid, onClick = {
            event(WorkoutEvent.Save(planned = editing && draft.planned))
        }) { Text(if (editing) "Сохранить изменения" else "Записать подход") }
        OutlinedButton(enabled = draft.valid, onClick = {
            event(WorkoutEvent.Save(planned = !draft.planned))
        }) {
            Text(when {
                !editing -> "Добавить в план"
                draft.planned -> "Отметить выполненным"
                else -> "Вернуть в план"
            })
        }
        if (draft.id != null) TextButton(onClick = { confirmDelete = true }) {
            Text("Удалить подход", color = colors.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutCalendar(date: LocalDate, onClose: () -> Unit, onChoose: (LocalDate) -> Unit) {
    val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val picker = rememberDatePickerState(initialSelectedDateMillis = millis, initialDisplayedMonthMillis = millis,
        yearRange = minOf(1900, date.year)..maxOf(2100, date.year))
    DatePickerDialog(onDismissRequest = onClose,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            picker.selectedDateMillis?.let { onChoose(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()) }
        }) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }) { DatePicker(picker, showModeToggle = false) }
}

@Composable
private fun WorkoutDateTimeDialog(title: String, action: String, initial: LocalDateTime,
    onClose: () -> Unit, onConfirm: (LocalDateTime) -> Unit) {
    var day by rememberSaveable { mutableLongStateOf(initial.toLocalDate().toEpochDay()) }
    var hours by rememberSaveable { mutableStateOf(initial.hour.toString().padStart(2, '0')) }
    var minutes by rememberSaveable { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(day)
    val hour = hours.toIntOrNull()
    val minute = minutes.toIntOrNull()
    if (calendar) {
        WorkoutCalendar(date, { calendar = false }) { day = it.toEpochDay(); calendar = false }
        return
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { calendar = true }) { Text("Дата"); Text(" · ${date.caption()}") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryTextField(hours, { hours = it }, Modifier.width(104.dp), label = { Text("Часы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    DiaryTextField(minutes, { minutes = it }, Modifier.width(104.dp), label = { Text("Минуты") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
        },
        confirmButton = { TextButton(enabled = hour in 0..23 && minute in 0..59, onClick = {
            if (hour != null && minute != null) onConfirm(date.atTime(hour, minute))
        }) { Text(action) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } })
}

@Composable
private fun TemplateChangeDialog(templates: List<WorkoutTemplate>, source: String?, onClose: () -> Unit,
    onChange: (WorkoutTemplate, String) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val selected = templates.find { it.id == selectedId }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(when {
            selected == null -> "Выберите шаблон"
            source == null -> "Новое название шаблона"
            else -> "Обновить «${selected.title}»?"
        }) },
        text = {
            if (selected == null) LazyColumn {
                items(templates, key = { it.id }) { template ->
                    TextButton(onClick = { selectedId = template.id; name = template.title }) { Text(template.title) }
                }
            } else if (source == null) {
                DiaryTextField(name, { name = it }, singleLine = true, label = { Text("Название") })
            } else Text("Подходы из тренировки «$source» заменят текущие подходы шаблона.")
        },
        confirmButton = {
            if (selected != null) TextButton(enabled = source != null || name.isNotBlank(),
                onClick = { onChange(selected, name) }) { Text(if (source == null) "Сохранить" else "Заменить подходы") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } })
}

@Preview(locale="ru")
@Composable
private fun WorkoutPreview() {
    MaterialTheme { WorkoutContent(WorkoutState(list=listOf(Workout(1,listOf(SetDraft(exercise="Жим лёжа",weight="40",reps="12"))))),{}) }
}
