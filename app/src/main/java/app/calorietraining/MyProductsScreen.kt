package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun MyProductsScreen(
    products: List<MyProduct>,
    onSave: (MyProduct) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    busy: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MyProduct?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val settings = remember(context) { ProductServerSettings(context) }
    val address = settings.address.trim()
    val key = settings.key.trim()
    val submission = if (address.isNotEmpty() && key.isNotEmpty()) {
        viewModel { OffSubmitModel(context.applicationContext) }
    } else null
    LaunchedEffect(address, key) { submission?.load(address, key) }
    val current = editing ?: products.firstOrNull { it.id == selectedId }
    fun closeCard() {
        editing = null
        selectedId = null
        sharing = false
        submission?.clearResult()
    }
    var confirming by remember { mutableStateOf(false) }
    val matches = remember(products, query) { products.filter { it.matches(query) } }
    val close = {
        if (!busy) {
            if (current == null) onClose() else closeCard()
        }
    }
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 20.dp)) {
            ScreenHeader(if (current == null) "Мои продукты" else "Продукт", onBack = close, backEnabled = !busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            submission?.result?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            val card = current
            if (card != null) {
                val status = submission?.status
                val canShare = status?.enabled == true && card.canShareWithOff() && products.any { it == card }
                ProductEditor(card, { editing = it }, onSave = {
                    onSave(card.copy(source = if (products.any {
                        it.id == card.id && it.name == card.name && it.energy == card.energy && it.macros == card.macros
                    }) card.source else "manual", updatedAt = ""))
                    closeCard()
                }, onDelete = { confirming = true }, share = if (canShare) {
                    {
                        submission.clearResult()
                        sharing = true
                    }
                } else null, sending = submission?.sending == true)
                if (sharing && canShare) {
                    LaunchedEffect(submission.result) {
                        if (submission.result != null) sharing = false
                    }
                    OffSubmitDialog(card, status.test, submission.sending,
                        onDismiss = { sharing = false },
                        onSubmit = { submission.submit(card, address, key) })
                }
                if (confirming) AlertDialog(
                    onDismissRequest = { confirming = false },
                    title = { Text("Удалить продукт?") },
                    text = { Text(card.name) },
                    confirmButton = {
                        TextButton(onClick = {
                            onDelete(requireNotNull(card.id))
                            confirming = false
                            closeCard()
                        }) { Text("Удалить") }
                    },
                    dismissButton = { TextButton(onClick = { confirming = false }) { Text("Отмена") } }
                )
            } else {
                DiaryTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Поиск по продуктам") }, singleLine = true)
                TextButton(onClick = { editing = MyProduct() }, enabled = !busy) { Text("Новый продукт") }
                if (products.isEmpty()) {
                    Text("Здесь появятся продукты, которые вы записываете",
                        style = MaterialTheme.typography.bodyMedium)
                } else if (matches.isEmpty()) {
                    Text("Совпадений нет", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches, key = { requireNotNull(it.id) }) { product ->
                        GlassCard(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth(), onClick = { selectedId = product.id }, enabled = !busy) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                                    if (product.brand.isNotBlank()) Text(product.brand, style = MaterialTheme.typography.bodyMedium)
                                    val unit = if (product.weighted) " / 100 г" else " на порцию"
                                    Text("${product.energy.ifBlank { "—" }} ккал$unit",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(if (product.source == "openfoodfacts") "Open Food Facts" else "Мой продукт",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditor(
    card: MyProduct,
    onChange: (MyProduct) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    share: (() -> Unit)?,
    sending: Boolean
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (card.id == null) "Новый продукт" else "Правка продукта", style = MaterialTheme.typography.headlineMedium)
        DiaryTextField(card.name, { onChange(card.copy(name = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Название") }, singleLine = true)
        DiaryTextField(card.brand, { onChange(card.copy(brand = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Бренд") }, singleLine = true)
        card.barcode?.let {
            DiaryTextField(it, {}, Modifier.fillMaxWidth(), label = { Text("Штрихкод") },
                readOnly = true, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiaryFilterChip(card.weighted, { onChange(card.copy(weighted = true)) }, { Text("На 100 г") })
            DiaryFilterChip(!card.weighted, { onChange(card.copy(weighted = false)) }, { Text("На порцию") })
        }
        ProductNumber("Ккал", card.energy) { onChange(card.copy(energy = it)) }
        ProductNumber("Белки", card.macros.protein) { onChange(card.copy(macros = card.macros.copy(protein = it))) }
        ProductNumber("Жиры", card.macros.fat) { onChange(card.copy(macros = card.macros.copy(fat = it))) }
        ProductNumber("Углеводы", card.macros.carbs) { onChange(card.copy(macros = card.macros.copy(carbs = it))) }
        Row {
            Button(onClick = onSave, enabled = card.name.isNotBlank() && card.macros.valid &&
                (card.energy.isBlank() || card.energy.amount() != null)) { Text("Сохранить") }
            if (card.id != null) TextButton(onClick = onDelete) { Text("Удалить") }
        }
        if (share != null) {
            TextButton(onClick = share, enabled = !sending) { Text("Поделиться в Open Food Facts") }
        }
    }
}

@Composable
private fun ProductNumber(label: String, value: String, onChange: (String) -> Unit) {
    DiaryTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Preview
@Composable
private fun MyProductsPreview() {
    DiaryTheme(ThemeMode.DARK) {
        MyProductsScreen(listOf(MyProduct(id = 1, name = "Сырок глазированный", energy = "410")), {}, {}, {})
    }
}
