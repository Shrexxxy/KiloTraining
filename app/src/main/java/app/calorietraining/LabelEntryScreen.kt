package app.calorietraining

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun LabelEntryScreen(
    draft: FoodDraft,
    back: () -> Unit,
    apply: (FoodDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable(stateSaver = LabelEntryState.Saver) {
        mutableStateOf(LabelEntryState(energy = draft.energy, protein = draft.macros.protein,
            fat = draft.macros.fat, carbs = draft.macros.carbs))
    }
    var pasted by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var emptyClipboard by rememberSaveable { mutableStateOf(false) }
    var pasteBlocked by rememberSaveable { mutableStateOf(false) }
    val analysis = remember(pasted) { pasted?.let(::analyzeLabel) }
    val warning = input.warning(analysis)
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    fun paste(text: String) {
        pasted = text
        val result = parseLabel(text)
        val updated = input.paste(result)
        pasteBlocked = updated == null
        if (updated != null) input = updated
    }
    BackHandler { back() }
    Scaffold(modifier, containerColor = Color.Transparent, contentColor = colors.onSurface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = back) { Text("Назад") }
                Text("С этикетки", style = typography.titleLarge)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val text = if (clip != null && clip.itemCount > 0)
                        clip.getItemAt(0).text?.take(4001)?.toString() else null
                    emptyClipboard = text.isNullOrBlank()
                    if (!text.isNullOrBlank()) {
                        if (input.hasValues) pending = text else paste(text)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Вставить текст этикетки") }
                if (emptyClipboard) Text("В буфере нет текста",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!input.perPortion, { input = input.copy(perPortion = false) }, { Text("На 100 г") })
                    FilterChip(input.perPortion, { input = input.copy(perPortion = true) }, { Text("На порцию") })
                }
                if (input.perPortion) {
                    val invalidMass = input.grams.isNotBlank() &&
                        (input.grams.length > 50 || input.grams.amount()?.signum() != 1)
                    DiaryTextField(input.grams, { input = input.copy(grams = it) }, Modifier.fillMaxWidth(),
                        label = { Text("Масса порции, г") }, singleLine = true, isError = invalidMass,
                        supportingText = if (invalidMass) ({ Text("Введите массу больше нуля") }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!input.inKj, { input = input.copy(inKj = false) }, { Text("ккал") })
                    FilterChip(input.inKj, { input = input.copy(inKj = true) }, { Text("кДж") })
                }
                val labels = listOf("Энергия, ${if (input.inKj) "кДж" else "ккал"}", "Белки, г", "Жиры, г", "Углеводы, г")
                labels.forEachIndexed { index, label ->
                    val value = input.entries[index]
                    val invalid = value.isNotBlank() && (value.length > 50 || value.amount() == null)
                    val support = if (invalid) "Введите число от нуля" else input.notes[index]?.description
                    DiaryTextField(value, { input = input.change(index, it) }, Modifier.fillMaxWidth(),
                        label = { Text(label) }, singleLine = true, isError = invalid,
                        supportingText = support?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                HorizontalDivider(Modifier.padding(top = 4.dp))
                Text("Итог на 100 г", style = typography.titleMedium)
                val (kcal, protein, fat, carbs) = input.values.map { it?.formAmount().orEmpty() }
                Text("${if (kcal.isBlank()) "— ккал" else "$kcal ккал"} · ${Macros(protein, fat, carbs).label()}")
                input.estimate(analysis)?.let {
                    Text("По БЖУ примерно ${it.formAmount()} ккал", style = typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
                if (input.impossibleMass) Text(LabelProblem.IMPOSSIBLE_MASS.description, color = colors.error)
                if (warning) Text(LabelProblem.ENERGY_MISMATCH.description,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = colors.error)
                analysis?.result?.let { result ->
                    (result.basis as? LabelBasis.Portion)?.let {
                        Text("На этикетке порция ${it.grams.display()} г", style = typography.bodySmall)
                    }
                    if (result.problems.isNotEmpty()) {
                        Text("Разбор текста", style = typography.titleMedium)
                        result.problems.forEach { Text("• ${it.description}", style = typography.bodySmall) }
                    }
                    result.fields().forEachIndexed { index, value ->
                        if (value?.note == LabelNote.REVIEW) {
                            val label = listOf("Энергия, ккал", "Белки, г", "Жиры, г", "Углеводы, г")[index]
                            Text("$label: ${value.value.formAmount()} · ${value.note.description}", style = typography.bodySmall)
                        }
                    }
                    if (pasteBlocked) Text("Проверьте введённые числа и массу порции, затем повторите вставку.",
                        style = typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
            }
            Button(onClick = { apply(input.applyTo(draft)) }, enabled = input.valid,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 52.dp)) {
                Text(if (warning) "Всё равно применить" else "Применить")
            }
        }
    }
    pending?.let { text ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("Заменить введённые значения?") },
            confirmButton = { TextButton(onClick = { paste(text); pending = null }) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Нет") } })
    }
}
