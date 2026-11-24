package app.calorietraining

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun FoodLibrary(foods: List<FoodDraft>, onChoose: (FoodDraft) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, search: (String) -> Unit, online: CatalogSearch, onManage: (() -> Unit)? = null,
    meal: Meal? = null, onScan: () -> Unit = {}, onManual: () -> Unit = {},
    whenEmpty: @Composable (Modifier) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(foods, query) {
        val words = query.searchableName().split(Regex("""\s+"""))
        foods.filter { food -> words.all("${food.name} ${food.brand}".searchableName()::contains) }
    }
    val term = query.trim()
    val results = (online as? CatalogSearch.Results)?.takeIf { it.term == term }
    val failed = (online as? CatalogSearch.Failed)?.takeIf { it.term == term }
    val pending = (online as? CatalogSearch.Loading)?.takeIf { it.term == term }
    val serverFoods = results?.serverFoods ?: failed?.serverFoods ?: pending?.serverFoods
    val (serverMatches, catalogMatches) = remember(matches, serverFoods, results) {
        val barcodes = matches.mapNotNull { it.barcode }.toMutableSet()
        fun distinct(foods: List<FoodDraft>) = foods.filter { it.barcode?.let(barcodes::add) ?: true }
        serverFoods?.let(::distinct) to results?.foods?.let(::distinct).orEmpty()
    }
    val loading = pending != null
    Scaffold(modifier = modifier, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 20.dp)) {
            ScreenHeader(if (meal == null) "Поиск продуктов" else "Добавить · ${meal.label}", onBack = onClose) {
                if (meal != null) {
                    IconButton(onClick = onScan) { Icon(painterResource(R.drawable.ic_barcode), "Сканировать штрихкод") }
                }
            }
            if (meal == null) onManage?.let { TextButton(onClick = it) { Text("Мои продукты") } }
            DiaryTextField(query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text("Название продукта или блюда") }, singleLine = true)
            if (meal != null && term.isEmpty()) {
                whenEmpty(Modifier.weight(1f))
            } else {
                TextButton(onClick = { if (!loading) search(term) }, enabled = term.length >= 2) {
                    Text("Найти в интернете")
                }
                DelayedProgress(loading)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (matches.isNotEmpty()) item { Text("Мои продукты", style = MaterialTheme.typography.labelLarge) }
                    items(matches) { food -> ProductRow(food, onChoose) }
                    if (serverMatches != null) {
                        item { Text("Ваша база", style = MaterialTheme.typography.labelLarge) }
                        items(serverMatches) { food -> ProductRow(food, onChoose) }
                    }
                    if (failed != null) item {
                        Text("Каталог недоступен. Попробуйте ещё раз или введите продукт вручную.")
                    }
                    if (results != null) {
                        item { Text("Open Food Facts · ODbL", style = MaterialTheme.typography.labelLarge) }
                        if (results.foods.isEmpty()) item { Text("В каталоге нет совпадений с калорийностью") }
                        items(catalogMatches) { food -> ProductRow(food, onChoose) }
                    }
                }
            }
            if (meal != null) OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                Text("Новый продукт")
            }
        }
    }
}


@Composable
internal fun AddMealFood(meal: Meal, state: FoodUiState, event: (FoodEvent) -> Unit, modifier: Modifier) {
    var products by rememberSaveable { mutableStateOf(false) }
    val active = when {
        state.venueTab -> 3
        state.recipes != null -> 2
        products -> 1
        else -> 0
    }
    state.openVenue?.let {
        VenueScreen(it, meal, state, event, modifier)
        return
    }
    val cards = remember(state.myProducts) { state.myProducts.map(MyProduct::toDraft) }
    val diary = remember(state.frequent, state.recent) {
        (state.frequent + state.recent).distinctBy { foodSuggestionKey(it.name, it.weighted) }
    }
    fun choose(food: FoodDraft) = event(FoodEvent.Edit(food.copy(meal = meal)))
    FoodLibrary(cards, ::choose, { event(FoodEvent.Close) }, modifier,
        search = { event(FoodEvent.FindFood(it)) }, online = state.online, meal = meal,
        onScan = { event(FoodEvent.Scan) }, onManual = { choose(FoodDraft()) }) { content ->
        Column(content.fillMaxWidth()) {
            SecondaryScrollableTabRow(
                selectedTabIndex = active, containerColor = Color.Transparent, edgePadding = 0.dp, minTabWidth = 48.dp,
            ) {
                listOf("Из дневника", "Мои продукты", "Рецепты", "Заведения").forEachIndexed { index, label ->
                    Tab(selected = index == active, onClick = {
                        products = index == 1
                        if (index == 3) event(FoodEvent.Venues)
                        else if (state.venueTab) event(FoodEvent.LeaveVenues)
                        if (index == 2 && state.recipes == null) event(FoodEvent.Recipes)
                        if (index != 2 && state.recipes != null) event(FoodEvent.LeaveRecipes)
                    }) {
                        Text(label, Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (active) {
                3 -> VenuesList(state.venues, glassAccent(meal), { event(FoodEvent.OpenVenue(it)) }, Modifier.weight(1f))
                2 -> RecipeList(state.recipes.orEmpty(), event, Modifier.weight(1f))
                else -> {
                    if (active == 1) TextButton(onClick = { event(FoodEvent.ManageProducts) }) {
                        Text("Управлять продуктами")
                    }
                    val foods = if (active == 1) cards else diary
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                        if (foods.isEmpty() && !state.busy) item {
                            Text(if (active == 1) "Пока нет сохранённых продуктов" else "Пока нет продуктов из дневника",
                                Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(foods) { ProductRow(it, ::choose) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(food: FoodDraft, onChoose: (FoodDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable { onChoose(food) }) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium)
            if (food.brand.isNotBlank()) Text(food.brand, style = MaterialTheme.typography.bodyMedium)
            Text("${food.energy} ккал " + if (food.weighted) "на 100 г" else "за порцию",
                style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
    }
}
