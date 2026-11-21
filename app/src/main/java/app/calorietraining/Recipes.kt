package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.math.RoundingMode

data class Recipe(val id: Long? = null, val name: String = "", val cooked: String = "",
    val ingredients: List<FoodDraft> = emptyList()) {
    fun food(): FoodDraft? {
        val yield = cooked.amount()?.takeIf { it.signum() > 0 } ?: return null
        if (name.isBlank() || ingredients.isEmpty() || ingredients.any { !it.weighted || it.calories == null }) return null
        fun perHundred(value: (FoodDraft) -> String): String {
            var sum = BigDecimal.ZERO
            for (item in ingredients) {
                val nutrient = value(item).amount() ?: return ""
                sum += nutrient * requireNotNull(item.grams.amount())
            }
            return sum.divide(yield, 10, RoundingMode.HALF_UP).display()
        }
        return FoodDraft(name = name.trim(), weighted = true, energy = perHundred { it.energy },
            macros = Macros(perHundred { it.macros.protein }, perHundred { it.macros.fat }, perHundred { it.macros.carbs }))
    }
}

internal fun createRecipeTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE recipe(id INTEGER PRIMARY KEY, name TEXT NOT NULL, cooked TEXT NOT NULL)")
    db.execSQL("""CREATE TABLE ingredient(id INTEGER PRIMARY KEY, recipe_id INTEGER NOT NULL,
        name TEXT NOT NULL, grams TEXT NOT NULL, energy TEXT NOT NULL,
        protein TEXT NOT NULL, fat TEXT NOT NULL, carbs TEXT NOT NULL)""")
    db.execSQL("CREATE INDEX ingredient_owner ON ingredient(recipe_id)")
}

fun FoodStore.recipes(): List<Recipe> {
    val items = mutableMapOf<Long, MutableList<FoodDraft>>()
    readableDatabase.rawQuery("SELECT recipe_id,name,grams,energy,protein,fat,carbs FROM ingredient ORDER BY id", null).use { rows ->
        while (rows.moveToNext()) {
            items.getOrPut(rows.getLong(0)) { mutableListOf() }.add(FoodDraft(name = rows.getString(1),
                weighted = true, grams = rows.getString(2), energy = rows.getString(3),
                macros = Macros(rows.getString(4), rows.getString(5), rows.getString(6))))
        }
    }
    return readableDatabase.rawQuery("SELECT id,name,cooked FROM recipe ORDER BY id DESC", null).use { rows ->
        buildList { while (rows.moveToNext()) add(Recipe(rows.getLong(0), rows.getString(1), rows.getString(2),
            items[rows.getLong(0)].orEmpty())) }
    }
}

fun FoodStore.saveRecipe(recipe: Recipe) {
    requireNotNull(recipe.food())
    val db = writableDatabase
    db.atomic {
        val header = ContentValues().apply { put("name", recipe.name.trim()); put("cooked", recipe.cooked.trim()) }
        val id = recipe.id ?: db.insertOrThrow("recipe", null, header)
        if (recipe.id != null) {
            val args = arrayOf(id.toString())
            if (db.update("recipe", header, "id=?", args) != 1) throw android.database.SQLException("Рецепт отсутствует")
            db.delete("ingredient", "recipe_id=?", args)
        }
        for (food in recipe.ingredients) {
            db.insertOrThrow("ingredient", null, ContentValues().apply {
                put("recipe_id", id)
                put("name", food.name.trim())
                put("grams", food.grams.trim())
                put("energy", food.energy.trim())
                put("protein", food.macros.protein.trim())
                put("fat", food.macros.fat.trim())
                put("carbs", food.macros.carbs.trim())
            })
        }
    }
}
