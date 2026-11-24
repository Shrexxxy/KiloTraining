package app.calorietraining

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.MathContext
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
        if (savedInstanceState == null) launchAutoBackup(applicationContext)
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
    Box(modifier) {
        FoodBody(state, onEvent)
        DelayedProgress(state.busy)
    }
}

@Composable
private fun FoodBody(state: FoodUiState, onEvent: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (state.scanning) {
        ScannerScreen(state, onEvent)
        return
    }
    if (state.managingProducts) {
        MyProductsScreen(state.myProducts, { onEvent(FoodEvent.SaveProduct(it)) },
            { onEvent(FoodEvent.DeleteProduct(it)) }, { onEvent(FoodEvent.LeaveProducts) },
            modifier = modifier, error = state.error, busy = state.busy)
        return
    }
    state.draft?.let { draft ->
        state.library?.let { foods ->
            val barcode = draft.barcode.takeIf { state.scanOutcome != null }
            FoodLibrary(if (state.recipe == null && barcode == null) foods else foods.filter { it.weighted },
                onChoose = { onEvent(FoodEvent.Edit(it.copy(meal = draft.meal, mealTime = draft.mealTime,
                    barcode = barcode ?: it.barcode, venueName = draft.venueName, photoPath = draft.photoPath))) },
                onClose = { onEvent(FoodEvent.BackToEditor) }, modifier = modifier,
                search = { onEvent(FoodEvent.FindFood(it)) }, online = state.online,
                onManage = { onEvent(FoodEvent.ManageProducts) })
            return
        }
        FoodEditor(draft, state, onEvent, modifier)
        return
    }
    val meal = state.addingMeal
    if (meal != null && state.recipe == null) {
        AddMealFood(meal, state, onEvent, modifier)
        return
    }
    if (state.recipes != null) {
        RecipeContent(state, onEvent, modifier)
        return
    }
    val total = remember(state.rows) { state.rows.sumOf { it.calories } }
    val macros = remember(state.rows) { state.rows.dailyMacros() }
    val today = state.day == LocalDate.now()
    var editingGoal by rememberSaveable(state.day.toEpochDay()) { mutableStateOf(false) }
    if (editingGoal) GoalDialog(state.day, state.goals, { editingGoal = false }) { kcal, goals ->
        editingGoal = false
        onEvent(FoodEvent.Goal(kcal, goals))
    }
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
            Text("Питание", Modifier.padding(top = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            DiaryDaySelector(state.day, true, { onEvent(FoodEvent.Day(it)) })
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.loaded) item {
                    GlassCard(LocalDiaryAccents.current.kcal) {
                        Box(Modifier.padding(16.dp)) {
                            NutritionRings(total, macros, state.goals, state.rows.isNotEmpty(), today) { editingGoal = true }
                        }
                    }
                }
                val byMeal = state.rows.groupBy { it.meal }
                Meal.entries.forEach { meal ->
                    val entries = byMeal[meal].orEmpty()
                    if (meal != Meal.UNSPECIFIED || entries.isNotEmpty()) {
                        item(key = meal.name) { MealCard(state.day, meal, entries, total, onEvent) }
                    }
                }
                if (state.error != null && state.draft == null) item {
                    Text(state.error, color = colors.error)
                    TextButton(onClick = { onEvent(FoodEvent.Day(state.day)) }) { Text("Повторить") }
                }
            }

        }
    }

}

@Composable
private fun MealCard(day: LocalDate, meal: Meal, entries: List<FoodEntry>, total: BigDecimal, onEvent: (FoodEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val barShape = RoundedCornerShape(3.dp)
    val accent = glassAccent(meal)
    GlassCard(accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            MealGroupHeader(day, meal, entries, true) { destination ->
                onEvent(FoodEvent.CopyMeal(day, destination, meal))
            }
            val fraction = if (total.signum() == 0) 0f else
                entries.sumOf { it.calories }.divide(total, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(5.dp).background(colors.outlineVariant, barShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(accent, barShape))
            }
            entries.forEach { row ->
                Row(Modifier.fillMaxWidth().clickable { onEvent(FoodEvent.Edit(row.edit())) }
                    .heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.photoPath?.let {
                        VenueThumbnail(it, Modifier.size(44.dp), "Фото блюда")
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val portion = if (row.grams.isEmpty()) "1 порция" else "${row.grams} г"
                        Text(listOf(row.venueName, row.mealTime, portion).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text("${row.calories.display()} ккал", Modifier.padding(start = 8.dp),
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (meal != Meal.UNSPECIFIED) {
                TextButton(onClick = { onEvent(FoodEvent.AddInMeal(meal)) }) { Text("+ Добавить") }
            }
        }
    }
}

@Composable
internal fun GoalDialog(
    day: LocalDate, goals: DailyGoals, dismiss: () -> Unit,
    onboarding: Boolean = false, save: (Int?, MacroGoals) -> Unit,
) {
    val (goal, macros) = goals
    val hasGoal = goals != DailyGoals()
    var kcal by rememberSaveable { mutableStateOf(goal?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(macros.protein?.toString().orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(macros.fat?.toString().orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(macros.carbs?.toString().orEmpty()) }
    val valid = listOf(kcal, protein, fat).all { it.isEmpty() || (it.toIntOrNull() ?: 0) > 0 } &&
        (carbs.isEmpty() || (carbs.toIntOrNull() ?: -1) >= 0) && (!onboarding || kcal.isNotEmpty())
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Дневная цель") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Действует с ${day.caption()} до следующего изменения",
                    Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium)
                GoalField("Ккал в день", kcal) { kcal = it }
                GoalField("Белки, г", protein) { protein = it }
                GoalField("Жиры, г", fat) { fat = it }
                GoalField("Углеводы, г", carbs) { carbs = it }
                if (hasGoal && !onboarding) TextButton(onClick = { save(null, MacroGoals()) }) { Text("Отключить цель") }
            }
        },
        confirmButton = { Button(enabled = valid, onClick = {
            save(kcal.toIntOrNull(), MacroGoals(protein.toIntOrNull(), fat.toIntOrNull(), carbs.toIntOrNull()))
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } }
    )
}

@Composable
private fun GoalField(label: String, value: String, change: (String) -> Unit) {
    DiaryTextField(value, change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
internal fun DateArrow(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Icon(painterResource(icon), null, Modifier.size(24.dp))
    }
}

@Composable
private fun FoodEditor(draft: FoodDraft, state: FoodUiState, onEvent: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    fun edit(value: FoodDraft) = onEvent(FoodEvent.Edit(value))
    val scanCode = draft.barcode.takeIf { state.scanOutcome != null }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(scanCode) {
        if (scanCode != null && draft.name.isBlank()) {
            withFrameNanos { }
            nameFocus.requestFocus()
        }
    }
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 20.dp)) {
            ScreenHeader(
                title = if (state.recipe != null) "Ингредиент" else if (draft.id == null) "Новая запись" else "Изменить запись",
                onBack = { onEvent(if (state.confirmingDelete) FoodEvent.CancelDelete else FoodEvent.Close) },
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.recipe == null) {
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Meal.entries.filter { it != Meal.UNSPECIFIED }.forEach { meal ->
                            val accent = glassAccent(meal)
                            val selected = draft.meal == meal
                            val colors = MaterialTheme.colorScheme
                            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                                color = if (selected) accent.copy(alpha = .24f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (selected) accent else colors.outlineVariant)) {
                                Box(Modifier.selectable(selected = selected, role = Role.RadioButton,
                                    onClick = { edit(draft.copy(meal = meal)) })
                                    .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(meal.label, style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (scanCode != null && state.scanOutcome != null) {
                    ScanResultCard(scanCode, state.scanOutcome, state.busy, onEvent)
                }
                if (draft.id != null && !state.confirmingDelete) {
                    TextButton(onClick = { onEvent(FoodEvent.AskDelete) }) {
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
                if (draft.id == null && draft.barcode == null && (state.recipe != null || state.addingMeal == null)) {
                    TextButton(onClick = { onEvent(FoodEvent.BrowseFoods) }) {
                        Text("Найти продукт")
                    }
                }
                if (state.recipe != null) TextButton(onClick = { onEvent(FoodEvent.Scan) }) { Text("Сканировать штрихкод") }
                DiaryTextField(draft.name, { edit(draft.copy(name = it)) }, Modifier.fillMaxWidth().focusRequester(nameFocus),
                    label = { Text("Название") }, singleLine = true)
                if (state.recipe == null) VenueEditorFields(draft, state.venues, state.busy, onEvent)
                if (scanCode == null && draft.barcode != null && state.hint.isNotBlank()) Text(state.hint, style = MaterialTheme.typography.bodySmall)
                if (draft.barcode == null && state.recipe == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !draft.weighted, label = { Text("За порцию") },
                        onClick = { if (draft.weighted) edit(draft.copy(weighted = false, energy = "", macros = Macros())) })
                    FilterChip(selected = draft.weighted, label = { Text("На 100 г") },
                        onClick = { if (!draft.weighted) edit(draft.copy(weighted = true, energy = "", macros = Macros())) })
                }
                if (state.recipe == null) {
                    DiaryTextField(draft.mealTime, { edit(draft.copy(mealTime = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("Время еды") }, placeholder = { Text("ЧЧ:ММ") }, singleLine = true,
                        isError = !validMealTime(draft.mealTime), supportingText = { Text("Можно оставить пустым") })
                }
                if (draft.weighted) AmountField(if (state.recipe == null) "Вес порции, г" else "Вес ингредиента, г", draft.grams, true) { edit(draft.copy(grams = it)) }
                AmountField(if (draft.weighted) "Ккал на 100 г" else "Ккал на порцию", draft.energy, true) {
                    edit(draft.copy(energy = it))
                }
                Text(if (draft.weighted) "БЖУ на 100 г" else "БЖУ за порцию", style = MaterialTheme.typography.titleMedium)
                Text("Если значение неизвестно, оставьте поле пустым", style = MaterialTheme.typography.bodySmall)
                AmountField("Белки, г", draft.macros.protein, true) { edit(draft.copy(macros = draft.macros.copy(protein = it))) }
                AmountField("Жиры, г", draft.macros.fat, true) { edit(draft.copy(macros = draft.macros.copy(fat = it))) }
                AmountField("Углеводы, г", draft.macros.carbs, true) { edit(draft.copy(macros = draft.macros.copy(carbs = it))) }
                if (draft.calories != null) Text("БЖУ порции · ${draft.portionMacros.label()}")
                if (draft.weighted) draft.calories?.let { Text("В порции: ${it.display()} ккал") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            }
            Button(onClick = { onEvent(FoodEvent.Save) },
                enabled = draft.calories != null && !state.confirmingDelete && !state.busy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 52.dp)) { Text("Сохранить") }
        }
    }
}

@Composable
internal fun AmountField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    DiaryTextField(value, change, modifier = Modifier.fillMaxWidth(), enabled = enabled,
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
