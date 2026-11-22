package app.calorietraining

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

private val cardTimestamp = DateTimeFormatterBuilder().appendInstant(9).toFormatter()
private val spaces = Regex("""[\s\p{Z}]+""")
internal fun String.searchableName() = trim().lowercase().replace('ё', 'е').replace(spaces, " ")

data class MyProduct(
    val id: Long? = null,
    val name: String = "",
    val brand: String = "",
    val barcode: String? = null,
    val weighted: Boolean = true,
    val energy: String = "",
    val macros: Macros = Macros(),
    val source: String = "manual",
    val updatedAt: String = ""
) {
    internal fun matches(query: String): Boolean {
        val text = "$name $brand".searchableName()
        return query.searchableName().split(' ').all(text::contains)
    }

    internal fun toDraft() = FoodDraft(name = name, weighted = weighted, energy = energy,
        barcode = barcode, macros = macros, brand = brand,
        origin = ProductOrigin(source, name, energy, macros))
}

data class ProductOrigin(val source: String, val name: String, val energy: String, val macros: Macros)

internal fun SQLiteDatabase.createMyProducts() {
    execSQL("""CREATE TABLE my_product (
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, brand TEXT NOT NULL DEFAULT '',
        barcode TEXT UNIQUE, weighted INTEGER NOT NULL CHECK(weighted IN (0,1)),
        energy TEXT NOT NULL, protein TEXT NOT NULL, fat TEXT NOT NULL, carbs TEXT NOT NULL,
        source TEXT NOT NULL CHECK(source IN ('manual','openfoodfacts')),
        search_key TEXT NOT NULL, updated_at TEXT NOT NULL
    )""")
    execSQL("CREATE UNIQUE INDEX my_product_name ON my_product(search_key,weighted) WHERE barcode IS NULL")
}

private fun Cursor.card() = MyProduct(getLong(0), getString(1), getString(2), getString(3),
    getInt(4) == 1, getString(5), Macros(getString(6), getString(7), getString(8)), getString(9), getString(10))

private const val selectCard = "SELECT id,name,brand,barcode,weighted,energy,protein,fat,carbs,source,updated_at FROM my_product"

fun FoodStore.myProducts(query: String = ""): List<MyProduct> =
    readableDatabase.rawQuery("$selectCard ORDER BY updated_at DESC,id DESC", null).use { rows ->
        buildList {
            while (rows.moveToNext()) {
                val card = rows.card()
                if (card.matches(query)) add(card)
            }
        }
    }

fun FoodStore.myProductByBarcode(code: String): MyProduct? =
    readableDatabase.rawQuery("$selectCard WHERE barcode=?", arrayOf(code)).use {
        if (it.moveToFirst()) it.card() else null
    }

fun FoodStore.saveMyProduct(product: MyProduct) {
    val db = writableDatabase
    db.atomic { db.upsertCard(product.copy(updatedAt = "")) }
}

fun FoodStore.deleteMyProduct(id: Long) {
    writableDatabase.delete("my_product", "id=?", arrayOf(id.toString()))
}

internal fun SQLiteDatabase.upsertCard(card: MyProduct) {
    val name = card.name.trim()
    val key = name.searchableName()
    val barcode = card.barcode?.takeIf(String::isNotBlank)
    val weighted = if (card.weighted) 1 else 0
    val (where, args) = if (barcode != null) "barcode=?" to arrayOf(barcode)
        else "barcode IS NULL AND search_key=? AND weighted=?" to arrayOf(key, weighted.toString())
    val duplicate = rawQuery("SELECT id FROM my_product WHERE $where", args).use {
        if (it.moveToFirst()) it.getLong(0) else null
    }
    val values = ContentValues().apply {
        put("name", name)
        put("brand", card.brand.trim())
        put("barcode", barcode)
        put("weighted", weighted)
        put("energy", card.energy.trim())
        put("protein", card.macros.protein.trim())
        put("fat", card.macros.fat.trim())
        put("carbs", card.macros.carbs.trim())
        put("source", card.source)
        put("search_key", key)
        put("updated_at", card.updatedAt.ifEmpty { cardTimestamp.format(Instant.now()) })
    }
    val id = duplicate ?: card.id
    if (duplicate != null && card.id != null && duplicate != card.id) {
        delete("my_product", "id=?", arrayOf(card.id.toString()))
    }
    if (id == null || update("my_product", values, "id=?", arrayOf(id.toString())) == 0) {
        insertOrThrow("my_product", null, values)
    }
}

internal fun SQLiteDatabase.transferLegacyProducts(table: String) {
    val cachedNames = mutableSetOf<String>()
    if (hasProductColumns(table, "code", "name", "energy", "protein", "fat", "carbs")) {
        rawQuery("SELECT code,name,energy,protein,fat,carbs FROM $table ORDER BY code", null).use { rows ->
            while (rows.moveToNext()) {
                val name = rows.getString(1)
                cachedNames.add(name.searchableName())
                upsertCard(MyProduct(name = name, barcode = rows.getString(0), energy = rows.getString(2),
                    macros = Macros(rows.getString(3), rows.getString(4), rows.getString(5)), source = "openfoodfacts"))
            }
        }
    }
    execSQL("DROP TABLE IF EXISTS $table")
    if (!hasProductColumns("food", "id", "day", "name", "grams", "energy", "protein", "fat", "carbs")) return
    val seen = mutableSetOf<Pair<String, Boolean>>()
    rawQuery("SELECT name,grams,energy,protein,fat,carbs,day FROM food ORDER BY day DESC,id DESC", null).use { rows ->
        while (rows.moveToNext()) {
            val name = rows.getString(0)
            val key = name.searchableName()
            val weighted = rows.getString(1).isNotEmpty()
            if (key in cachedNames || !seen.add(key to weighted)) continue
            upsertCard(MyProduct(name = name, weighted = weighted, energy = rows.getString(2),
                macros = Macros(rows.getString(3), rows.getString(4), rows.getString(5)),
                updatedAt = rows.getString(6) + "T00:00:00.000000000Z"))
        }
    }
}

private fun SQLiteDatabase.hasProductColumns(table: String, vararg required: String): Boolean =
    rawQuery("PRAGMA table_info($table)", null).use { rows ->
        val name = rows.getColumnIndexOrThrow("name")
        val columns = buildSet { while (rows.moveToNext()) add(rows.getString(name)) }
        required.all(columns::contains)
    }
