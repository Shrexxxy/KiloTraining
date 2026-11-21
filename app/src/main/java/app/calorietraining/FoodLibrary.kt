package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun String.searchableName() = trim().lowercase().replace('ё', 'е')

@Composable
internal fun FoodLibrary(foods: List<FoodDraft>, onChoose: (FoodDraft) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, search: (String) -> Unit, online: CatalogSearch) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(foods, query) {
        val words = query.searchableName().split(Regex("""\s+"""))
        foods.filter { food -> words.all(food.name.searchableName()::contains) }
    }
    val term = query.trim()
    val results = (online as? CatalogSearch.Results)?.takeIf { it.term == term }
    val loading = (online as? CatalogSearch.Loading)?.term == term
    BackHandler(onBack = onClose)
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 20.dp)) {
            Text("Поиск продуктов", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text("Название продукта или блюда") }, singleLine = true)
            TextButton(onClick = { search(term) }, enabled = term.length >= 2 && !loading) {
                Text("Найти в интернете")
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if ((online as? CatalogSearch.Failed)?.term == term) item {
                    Text("Каталог недоступен. Попробуйте ещё раз или введите продукт вручную.")
                }
                if (results != null) {
                    item { Text("Open Food Facts · ODbL", style = MaterialTheme.typography.labelLarge) }
                    if (results.foods.isEmpty()) item { Text("В каталоге нет совпадений с калорийностью") }
                    items(results.foods) { food -> ProductRow(food, onChoose) }
                }
                if (matches.isNotEmpty()) item { Text("Из дневника", style = MaterialTheme.typography.labelLarge) }
                items(matches) { food ->
                    ProductRow(food, onChoose)
                }
            }
            TextButton(onClick = onClose) { Text("Вернуться к вводу") }
        }
    }
}


@Composable
private fun ProductRow(food: FoodDraft, onChoose: (FoodDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable { onChoose(food) }) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium)
            Text("${food.energy} ккал " + if (food.weighted) "на 100 г" else "за порцию",
                style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
    }
}
