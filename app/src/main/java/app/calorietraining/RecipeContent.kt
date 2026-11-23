package app.calorietraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.RoundingMode

@Composable
internal fun RecipeContent(state: FoodUiState, event: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    val recipe = state.recipe
    fun back() = event(if (recipe == null) FoodEvent.LeaveRecipes else FoodEvent.EditRecipe(null))
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        ScreenHeader(if (recipe == null) "Мои рецепты" else "Рецепт", onBack = ::back)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (recipe == null) {
            RecipeList(state.recipes.orEmpty(), event, Modifier.weight(1f))
        } else {
            fun edit(value: Recipe) = event(FoodEvent.EditRecipe(value))
            val food = recipe.food()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DiaryTextField(recipe.name, { edit(recipe.copy(name = it)) }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Название блюда") }) }
                item { AmountField("Вес готового блюда без посуды, г", recipe.cooked, true) { edit(recipe.copy(cooked = it)) } }
                itemsIndexed(recipe.ingredients) { index, ingredient ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${ingredient.name} · ${ingredient.grams} г")
                            Row {
                                TextButton(onClick = { event(FoodEvent.Ingredient(index)) }) { Text("Изменить") }
                                TextButton(onClick = { edit(recipe.copy(ingredients = recipe.ingredients.filterIndexed { i, _ -> i != index })) }) { Text("Убрать") }
                            }
                        }
                    }
                }
                item { OutlinedButton(onClick = { event(FoodEvent.Ingredient(null)) }) { Text("Добавить ингредиент") } }
                if (food != null) item {
                    val kcal = food.energy.amount()?.setScale(2, RoundingMode.HALF_UP)?.display()
                    Text("На 100 г · $kcal ккал")
                    Text(food.copy(grams="100").portionMacros.label())
                }
            }
            Button(enabled = food != null, onClick = { event(FoodEvent.SaveRecipe) }) { Text("Сохранить рецепт") }
        }
    }
}

@Composable
internal fun RecipeList(recipes: List<Recipe>, event: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recipes.isEmpty()) item { Text("Добавьте первое домашнее блюдо") }
            items(recipes) { item ->
                GlassCard(LocalDiaryAccents.current.protein, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("Выход · ${item.cooked} г")
                        Row {
                            TextButton(onClick = { event(FoodEvent.LogRecipe(item)) }) { Text("Записать порцию") }
                            TextButton(onClick = { event(FoodEvent.EditRecipe(item)) }) { Text("Изменить") }
                        }
                    }
                }
            }
        }
        Button(onClick = { event(FoodEvent.EditRecipe(Recipe())) }) { Text("Создать рецепт") }
    }
}

@Preview(locale="ru")
@Composable
private fun RecipePreview() {
    MaterialTheme { RecipeContent(FoodUiState(recipes = emptyList(), recipe = Recipe(name = "Плов")), {}) }
}
