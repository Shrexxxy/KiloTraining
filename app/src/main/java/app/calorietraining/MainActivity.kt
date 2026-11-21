package app.calorietraining

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val russian = Locale.forLanguageTag("ru")
internal fun LocalDate.caption(): String {
    val pattern = if (year == LocalDate.now().year) "EEE, d MMMM" else "d MMMM yyyy"
    return format(DateTimeFormatter.ofPattern(pattern, russian)).replaceFirstChar { it.titlecase(russian) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = ThemeSettings(this)
        val initialMode = settings.mode
        applyDiaryBars(when (initialMode) {
            ThemeMode.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        })
        setContent {
            var mode by remember { mutableStateOf(initialMode) }
            DiaryTheme(mode) {
                DiaryApp(mode) { selected ->
                    settings.mode = selected
                    mode = selected
                }
            }
        }
    }
}

@Composable
fun FoodScreen(modifier: Modifier = Modifier, model: FoodViewModel = viewModel()) {
    val state by model.uiState.collectAsStateWithLifecycle()
    FoodContent(state, model::onEvent, modifier)
}

@Composable
fun FoodContent(state: FoodUiState, onEvent: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (state.scanning) {
        ScannerScreen(state, onEvent)
        return
    }
    state.draft?.let { draft ->
        state.library?.let { foods ->
            FoodLibrary(if (state.recipe == null) foods else foods.filter { it.weighted }, onChoose = { onEvent(FoodEvent.Edit(it.copy(meal = draft.meal, mealTime = draft.mealTime))) },
                onClose = { onEvent(FoodEvent.BackToEditor) }, modifier = modifier,
                search = { onEvent(FoodEvent.FindFood(it)) }, online = state.online)
            return
        }
        val back = if (state.confirmingDelete) FoodEvent.CancelDelete else FoodEvent.Close
        BackHandler { onEvent(back) }
        FoodEditor(draft, state, onEvent, modifier)
        return
    }
    if (state.recipes != null) {
        RecipeContent(state, onEvent, modifier)
        return
    }
    Scaffold(modifier = modifier, containerColor = colors.surface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
            Text("Питание", Modifier.padding(top = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            DiaryDaySelector(state.day, !state.busy, { onEvent(FoodEvent.Day(it)) })
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.loaded) item {
                    val total = state.rows.sumOf { it.calories }.display()
                    Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_meal), null, Modifier.size(30.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("$total ккал за день", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (state.rows.isNotEmpty()) item { DailyNutrition(state.rows.dailyMacros()) }
                if (state.loaded && state.day == LocalDate.now()) item {
                    DailyGoal(state.goal, state.rows.sumOf { it.calories }, state.busy) {
                        onEvent(FoodEvent.Goal(it))
                    }
                }
                if (state.loaded && state.rows.isEmpty()) item {
                    Text("За этот день пока нет записей", Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                state.rows.groupBy { it.meal }.toSortedMap().forEach { (meal, entries) ->
                    item(key = meal.name) {
                        MealGroupHeader(state.day, meal, entries, !state.busy) { destination ->
                            onEvent(FoodEvent.CopyMeal(state.day, destination, meal))
                        }
                    }
                    items(entries, key = { it.id }) { row ->
                    Surface(onClick = { onEvent(FoodEvent.Edit(row.edit())) }, enabled = !state.busy,
                        shape = MaterialTheme.shapes.large, color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (row.mealTime.isNotBlank()) Text(row.mealTime, style = MaterialTheme.typography.labelSmall)
                                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (row.grams.isEmpty()) "1 порция" else "${row.grams} г",
                                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.medium) {
                                Text("${row.calories.display()} ккал", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                }
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.error != null && state.draft == null) item {
                    Text(state.error, color = colors.error)
                    TextButton(onClick = { onEvent(FoodEvent.Day(state.day)) }) { Text("Повторить") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { onEvent(FoodEvent.BrowseFoods) }, enabled = state.loaded && !state.busy) { Text("Найти продукт") }
                TextButton(onClick = { onEvent(FoodEvent.Recipes) }, enabled = state.loaded && !state.busy) { Text("Мои рецепты") }
            }
            Button(onClick = { onEvent(FoodEvent.Scan) }, enabled = state.loaded && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Сканировать штрихкод") }
            OutlinedButton(onClick = { onEvent(FoodEvent.Edit(FoodDraft())) }, enabled = state.loaded && !state.busy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 52.dp)) {
                Text("Ввести вручную", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

}

@Composable
private fun DailyGoal(goal: Int?, consumed: BigDecimal, busy: Boolean, save: (Int?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    if (editing) {
        val value = input.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Дневная цель") },
            text = {
                Column {
                    OutlinedTextField(input, { input = it }, label = { Text("Ккал в день") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    if (goal != null) TextButton(onClick = { editing = false; save(null) }) { Text("Отключить цель") }
                }
            },
            confirmButton = { Button(enabled = value != null, onClick = {
                value?.let { editing = false; save(it) }
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } }
        )
    }
    Column {
        if (goal != null) {
            val remaining = goal.toBigDecimal() - consumed
            val status = when (remaining.signum()) {
                1 -> "До цели · ${remaining.display()} ккал"
                -1 -> "Сверх цели · ${remaining.abs().display()} ккал"
                else -> "Дневная цель достигнута"
            }
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(enabled = !busy, onClick = { input = goal?.toString().orEmpty(); editing = true }) {
            Text(if (goal == null) "Задать дневную цель" else "Изменить цель · $goal ккал")
        }
    }
}

@Composable
internal fun DateArrow(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(glyph, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun FoodEditor(draft: FoodDraft, state: FoodUiState, onEvent: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    fun edit(value: FoodDraft) = onEvent(FoodEvent.Edit(value))
    val busy = state.busy
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.recipe != null) "Ингредиент" else if (draft.id == null) "Новая запись" else "Изменить запись",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(enabled = !busy, onClick = { onEvent(FoodEvent.Close) }) { Text("Отмена") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (draft.id != null && !state.confirmingDelete) {
                    TextButton(enabled = !busy, onClick = { onEvent(FoodEvent.AskDelete) }) {
                        Text("Удалить запись", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (state.confirmingDelete) {
                    Text("Удалить запись «${draft.name}»?", style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = { onEvent(FoodEvent.CancelDelete) }) { Text("Оставить") }
                        TextButton(onClick = { onEvent(FoodEvent.Delete) }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (draft.id == null && draft.barcode == null) {
                    TextButton(enabled = !busy, onClick = { onEvent(FoodEvent.BrowseFoods) }) {
                        Text("Найти продукт")
                    }
                }
                if (state.recipe != null) TextButton(enabled = !busy, onClick = { onEvent(FoodEvent.Scan) }) { Text("Сканировать штрихкод") }
                if (state.recipe == null && draft.id == null && draft.barcode == null && state.recent.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text("Недавние продукты") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.recent.forEach { food ->
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(food.name)
                                        Text("${food.energy} ккал " + if (food.weighted) "на 100 г" else "за порцию",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }, onClick = { expanded = false; edit(food.copy(meal = draft.meal, mealTime = draft.mealTime)) })
                            }
                        }
                    }
                }
                OutlinedTextField(draft.name, { edit(draft.copy(name = it)) }, Modifier.fillMaxWidth(),
                    enabled = !busy, label = { Text("Название") }, singleLine = true)
                if (draft.barcode != null) Text(state.hint, style = MaterialTheme.typography.bodySmall)
                if (draft.barcode == null && state.recipe == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !draft.weighted, enabled = !busy, label = { Text("За порцию") },
                        onClick = { if (draft.weighted) edit(draft.copy(weighted = false, energy = "", macros = Macros())) })
                    FilterChip(selected = draft.weighted, enabled = !busy, label = { Text("На 100 г") },
                        onClick = { if (!draft.weighted) edit(draft.copy(weighted = true, energy = "", macros = Macros())) })
                }
                if (state.recipe == null) {
                    OutlinedTextField(draft.mealTime, { edit(draft.copy(mealTime = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("Время еды") }, placeholder = { Text("ЧЧ:ММ") }, singleLine = true,
                        isError = !validMealTime(draft.mealTime), supportingText = { Text("Можно оставить пустым") })
                    var choosingMeal by rememberSaveable { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { choosingMeal = true }) {
                            Text("Приём пищи · ${draft.meal.label}")
                        }
                        DropdownMenu(expanded = choosingMeal, onDismissRequest = { choosingMeal = false }) {
                            Meal.entries.forEach { meal ->
                                DropdownMenuItem(text = { Text(meal.label) }, onClick = {
                                    choosingMeal = false
                                    edit(draft.copy(meal = meal))
                                })
                            }
                        }
                    }
                }
                if (draft.weighted) AmountField(if (state.recipe == null) "Вес порции, г" else "Вес ингредиента, г", draft.grams, !busy) { edit(draft.copy(grams = it)) }
                AmountField(if (draft.weighted) "Ккал на 100 г" else "Ккал на порцию", draft.energy, !busy) {
                    edit(draft.copy(energy = it))
                }
                Text(if (draft.weighted) "БЖУ на 100 г" else "БЖУ за порцию", style = MaterialTheme.typography.titleMedium)
                Text("Если значение неизвестно, оставьте поле пустым", style = MaterialTheme.typography.bodySmall)
                AmountField("Белки, г", draft.macros.protein, !busy) { edit(draft.copy(macros = draft.macros.copy(protein = it))) }
                AmountField("Жиры, г", draft.macros.fat, !busy) { edit(draft.copy(macros = draft.macros.copy(fat = it))) }
                AmountField("Углеводы, г", draft.macros.carbs, !busy) { edit(draft.copy(macros = draft.macros.copy(carbs = it))) }
                if (draft.calories != null) Text("БЖУ порции · ${draft.portionMacros.label()}")
                if (draft.weighted) draft.calories?.let { Text("В порции: ${it.display()} ккал") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            }
            Button(onClick = { onEvent(FoodEvent.Save) },
                enabled = draft.calories != null && !busy && !state.confirmingDelete,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 52.dp)) { Text("Сохранить") }
        }
    }
}

@Composable
internal fun AmountField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Preview(locale = "ru", showBackground = true)
@Composable
private fun DiaryPreview() {
    DiaryTheme(ThemeMode.SYSTEM) {
        FoodContent(FoodUiState(day = LocalDate.of(2026, 11, 20), loaded = true, rows = listOf(
            FoodEntry(1, "Овсяная каша", "140", "200", BigDecimal("280")),
            FoodEntry(2, "Йогурт", "90", "150", BigDecimal("135")),
            FoodEntry(3, "Творог с ягодами", "300", "", BigDecimal("300"))
        )), {})
    }
}


@Composable
internal fun DailyNutrition(macros: Macros, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(macros.label(), style = MaterialTheme.typography.titleMedium)
        if (listOf(macros.protein, macros.fat, macros.carbs).any(String::isEmpty)) {
            Text("Прочерк означает, что для итога не хватает данных", style = MaterialTheme.typography.bodySmall)
        }
    }
}
