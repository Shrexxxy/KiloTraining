package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun WorkoutContent(state:WorkoutState,event:(WorkoutEvent)->Unit,modifier:Modifier=Modifier,
    focusSession: Long? = null, onFocused: () -> Unit = {},
    restTimer: @Composable () -> Unit = {}) {
    val colors=MaterialTheme.colorScheme
    val draft=state.draft
    var onlyPending by rememberSaveable(state.day.toEpochDay()) { mutableStateOf(false) }
    fun visibleGroups(workout: Workout) = workout.sets.groupBy { it.group }.values.filter { sets ->
        !onlyPending || sets.any { it.planned }
    }
    Scaffold(modifier=modifier,containerColor=colors.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp)) {
            if(draft != null && state.session!=null) {
                BackHandler { event(WorkoutEvent.Close) }
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
                fun edit(d:SetDraft) = event(WorkoutEvent.Edit(state.session,d))
                var choosing by rememberSaveable(draft.group) { mutableStateOf(false) }
                if (choosing) {
                    ExercisePicker(onSelect = { photo ->
                        edit(draft.copy(exercise = photo.name))
                        choosing = false
                    }, onClose = { choosing = false })
                    return@Column
                }
                Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Подход",Modifier.padding(top=16.dp),style=MaterialTheme.typography.headlineLarge)
                    OutlinedButton(enabled = !state.busy, onClick = { choosing = true }) { Text("Выбрать из каталога") }
                    val original = state.list.firstOrNull { it.id == state.session }?.sets?.firstOrNull { it.group == draft.group }
                    OutlinedTextField(draft.exercise,{edit(draft.copy(exercise=it))},label={Text("Название упражнения")},
                        singleLine=true,enabled=!state.busy)
                    photoForExercise(draft.exercise)?.let { ExerciseFrames(it) }
                    state.previous?.let { previous ->
                        Text("Прошлая тренировка · ${previous.day.caption()}", style = MaterialTheme.typography.titleMedium)
                        previous.sets.forEachIndexed { index, set ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                val weight = if (set.weight.isBlank()) "Свой вес" else "${set.weight} кг"
                                Text("${index + 1}.  $weight × ${set.reps}" + if (set.warmup) " · разминка" else "")
                                TextButton(enabled = !state.busy, onClick = {
                                    edit(draft.copy(weight = set.weight, reps = set.reps, warmup = set.warmup))
                                }) { Text("Повторить") }
                            }
                        }
                    }
                    if (original != null && draft.exercise.trim() != original.exercise) {
                        Text("Название изменится у всех подходов этого упражнения.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !draft.warmup, enabled = !state.busy,
                            onClick = { edit(draft.copy(warmup = false)) }, label = { Text("Рабочий") })
                        FilterChip(selected = draft.warmup, enabled = !state.busy,
                            onClick = { edit(draft.copy(warmup = true)) }, label = { Text("Разминочный") })
                    }
                    OutlinedTextField(draft.weight,{edit(draft.copy(weight=it))},label={Text("Вес, кг")},singleLine=true,
                        supportingText={Text("Оставьте пустым для своего веса")},enabled=!state.busy,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    OutlinedTextField(draft.reps,{edit(draft.copy(reps=it))},label={Text("Повторы")},singleLine=true,
                        enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    state.error?.let {Text(it,color=colors.error)}
                    Button(enabled=draft.valid && !state.busy,onClick={event(WorkoutEvent.Save())}) {
                        Text(if (draft.planned) "Отметить выполненным" else "Сохранить")
                    }
                    OutlinedButton(enabled = draft.valid && !state.busy,
                        onClick = { event(WorkoutEvent.Save(planned = true)) }) {
                        Text(when {
                            draft.id == null -> "Добавить в план"
                            draft.planned -> "Сохранить в плане"
                            else -> "Вернуть в план"
                        })
                    }
                    TextButton(enabled=!state.busy,onClick={event(WorkoutEvent.Close)}) { Text("Отмена") }
                    if (draft.id != null) TextButton(enabled = !state.busy, onClick = { confirmDelete = true }) {
                        Text("Удалить подход", color = colors.error)
                    }
                }
            } else {
                Text("Тренировка",Modifier.padding(top=16.dp,bottom=12.dp),style=MaterialTheme.typography.headlineLarge)
                DiaryDaySelector(state.day, !state.busy, { event(WorkoutEvent.Day(it)) })
                if (state.list.isNotEmpty()) {
                    FilterChip(selected = onlyPending, onClick = { onlyPending = !onlyPending },
                        label = { Text("Только оставшиеся") })
                }
                state.error?.let {Text(it,color=colors.error)}
                val listState = rememberLazyListState()
                LaunchedEffect(focusSession, state.list, state.busy) {
                    if (focusSession == null || state.busy) return@LaunchedEffect
                    val position = state.list.indexOfFirst { it.id == focusSession }
                    if (position >= 0) {
                        val timerRows = if (state.day == java.time.LocalDate.now()) 1 else 0
                        val earlierRows = state.list.take(position).sumOf { 2 + visibleGroups(it).size }
                        listState.scrollToItem(timerRows + earlierRows)
                        onFocused()
                    }
                }
                LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if (state.day == java.time.LocalDate.now() && state.list.isNotEmpty()) {
                        item(key = "rest-timer") { restTimer() }
                    }
                    if(state.list.isEmpty() && !state.busy) item {Text("За этот день пока нет тренировок")}
                    state.list.forEachIndexed { index, workout ->
                        item(key="session${workout.id}") {
                            var confirm by rememberSaveable { mutableStateOf(false) }
                            var savingTemplate by rememberSaveable { mutableStateOf(false) }
                            var renaming by rememberSaveable { mutableStateOf(false) }
                            var title by rememberSaveable { mutableStateOf("") }
                            val label = workout.title.ifBlank { "Тренировка ${index + 1}" }
                            var menu by remember { mutableStateOf(false) }
                            var moveDate by rememberSaveable(state.day.toEpochDay()) { mutableStateOf<Long?>(null) }
                            var repeating by rememberSaveable { mutableStateOf(false) }
                            moveDate?.let { epoch ->
                                val destination = java.time.LocalDate.ofEpochDay(epoch)
                                AlertDialog(onDismissRequest = { moveDate = null },
                                    title = { Text(if (repeating) "Повторить тренировку" else "Перенести тренировку") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("$label · ${state.day.caption()}")
                                            if (repeating) Text("Подходов в плане: ${workout.sets.count { !it.planned }}. Отмечайте их по мере выполнения.")
                                            Text("Новая дата", style = MaterialTheme.typography.labelLarge)
                                            DiaryDaySelector(destination, !state.busy, { moveDate = it.toEpochDay() })
                                        }
                                    },
                                    confirmButton = {
                                        Button(enabled = !state.busy && (repeating || destination != state.day), onClick = {
                                            moveDate = null
                                            event(if (repeating) WorkoutEvent.Repeat(workout.id, destination)
                                                else WorkoutEvent.Move(workout.id, destination))
                                        }) { Text(if (repeating) "Создать план" else "Перенести") }
                                    },
                                    dismissButton = { TextButton(onClick = { moveDate = null }) { Text("Отмена") } })
                            }
                            if (renaming || savingTemplate) AlertDialog(
                                onDismissRequest = { renaming = false; savingTemplate = false },
                                title = { Text(if (savingTemplate) "Название шаблона" else "Название тренировки") },
                                text = {
                                    OutlinedTextField(title, { title = it }, singleLine = true,
                                        label = { Text("Название") }, placeholder = { Text("Спина и бицепс") })
                                },
                                confirmButton = { TextButton(enabled = !state.busy && (!savingTemplate || title.isNotBlank()), onClick = {
                                    event(if (savingTemplate) WorkoutEvent.SaveTemplate(workout.id, title) else WorkoutEvent.Rename(workout.id, title))
                                    renaming = false
                                    savingTemplate = false
                                }) { Text("Сохранить") } },
                                dismissButton = { TextButton(onClick = { renaming = false; savingTemplate = false }) { Text("Отмена") } }
                            )
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    val completed = workout.sets.count { !it.planned }
                                    Text("Подходы: $completed из ${workout.sets.size} выполнено",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Box {
                                    TextButton(enabled = !state.busy, onClick = { menu = true }) { Text("Действия") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(if (workout.title.isBlank()) "Задать название" else "Переименовать") },
                                            onClick = { menu = false; confirm = false; title = workout.title; renaming = true })
                                        DropdownMenuItem(text = { Text("Сохранить как шаблон") }, enabled = workout.sets.isNotEmpty(),
                                            onClick = { menu = false; title = workout.title; savingTemplate = true })
                                        DropdownMenuItem(text = { Text("Перенести на дату") },
                                            onClick = { menu = false; confirm = false; repeating = false; moveDate = state.day.toEpochDay() })
                                        DropdownMenuItem(text = { Text("Повторить") }, enabled = workout.sets.any { !it.planned },
                                            onClick = { menu = false; confirm = false; repeating = true; moveDate = java.time.LocalDate.now().toEpochDay() })
                                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; confirm = true })
                                    }
                                }
                            }
                            if(confirm) {
                                OutlinedButton(enabled=!state.busy,onClick={event(WorkoutEvent.Remove(workout.id))}) { Text("Удалить тренировку целиком") }
                                TextButton(onClick = { confirm = false }) { Text("Отмена") }
                            }
                        }
                        items(visibleGroups(workout),key={ it.first().group }) { sets ->
                            OutlinedCard(border=BorderStroke(1.dp,colors.outlineVariant),colors=CardDefaults.outlinedCardColors(containerColor=colors.surface)) {
                                Surface(color=colors.primaryContainer) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        photoForExercise(sets.first().exercise)?.let { photo ->
                                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(photo.second),
                                                null, Modifier.size(64.dp, 52.dp))
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Text(sets.first().exercise, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                sets.forEachIndexed { i,set ->
                                    if (onlyPending && !set.planned) return@forEachIndexed
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(enabled=!state.busy,modifier=Modifier.weight(1f),
                                            onClick={event(WorkoutEvent.Edit(workout.id,set))}) {
                                            val label = if (set.planned) "В плане · ${i+1}" else "Подход ${i+1}"
                                            Text(if (set.warmup) "$label · Разминка" else label, Modifier.weight(1f))
                                            Text("${if(set.weight.isBlank()) "Свой вес" else "${set.weight} кг"} × ${set.reps}")
                                        }
                                        if (set.planned && set.id != null) {
                                            TextButton(enabled = !state.busy,
                                                onClick = { event(WorkoutEvent.Complete(workout.id, set.id)) }) {
                                                Text("Готово")
                                            }
                                        }
                                    }
                                }
                                TextButton(enabled=!state.busy,onClick={event(WorkoutEvent.Edit(workout.id,sets.last().copy(id=null, planned=false)))}) { Text("Добавить подход") }
                            }
                        }
                        item { OutlinedButton(enabled=!state.busy,onClick={event(WorkoutEvent.Edit(workout.id,SetDraft()))}) { Text("Добавить упражнение") } }
                    }
                }
                if (state.templates.isNotEmpty()) {
                    var choosingTemplate by rememberSaveable { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !state.busy, onClick = { choosingTemplate = true }) { Text("Из шаблона") }
                        DropdownMenu(expanded = choosingTemplate, onDismissRequest = { choosingTemplate = false }) {
                            state.templates.forEach { template ->
                                DropdownMenuItem(text = { Text(template.title) }, onClick = {
                                    choosingTemplate = false
                                    event(WorkoutEvent.StartTemplate(template.id))
                                })
                            }
                        }
                    }
                }
                Button(enabled=!state.busy,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp),onClick={event(WorkoutEvent.Create)}) { Text("Новая тренировка") }
            }
        }
    }
}
@Preview(locale="ru")
@Composable
private fun WorkoutPreview() {
    MaterialTheme { WorkoutContent(WorkoutState(list=listOf(Workout(1,listOf(SetDraft(exercise="Жим лёжа",weight="40",reps="12"))))),{}) }
}
