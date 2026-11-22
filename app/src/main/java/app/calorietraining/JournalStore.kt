package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID

internal fun createJournalTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE product (code TEXT PRIMARY KEY, name TEXT NOT NULL, energy TEXT NOT NULL)")
    db.execSQL("CREATE TABLE workout (id INTEGER PRIMARY KEY, day TEXT NOT NULL, title TEXT NOT NULL DEFAULT '')")
    db.execSQL("""CREATE TABLE lifting (id INTEGER PRIMARY KEY, session INTEGER NOT NULL,
        exercise_group TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT NOT NULL, reps INTEGER NOT NULL,
        planned INTEGER NOT NULL DEFAULT 0)""")
    db.execSQL("CREATE INDEX lifting_session ON lifting(session)")
}

data class Product(val name: String, val energy: String, val macros: Macros = Macros())
enum class ScanOutcome { PHONE, CATALOG, MISSING, NO_ENERGY, UNAVAILABLE }

data class ProductResult(val draft: FoodDraft, val hint: String, val outcome: ScanOutcome)
fun validBarcode(code: String) = code.length in listOf(8,12,13,14) && code.all { it in '0'..'9' }

private const val catalogFields = "product_name_ru,product_name,brands,nutriments"

private fun readCatalog(url: String): JSONObject? {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent","CalorieTraining/0.2 (Android food diary)")
        if (connection.responseCode == 404) return null
        if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (e: JSONException) { throw IOException("Некорректный ответ каталога", e) }
    finally { connection.disconnect() }
}

internal fun JSONObject.asProduct(): Product {
    val nutrients = optJSONObject("nutriments")
    fun nutrient(key: String) = nutrients?.optString(key)?.takeIf { it.amount() != null }.orEmpty()
    return Product(optString("product_name_ru").ifBlank { optString("product_name") },
        nutrient("energy-kcal_100g"), Macros(nutrient("proteins_100g"), nutrient("fat_100g"), nutrient("carbohydrates_100g")))
}

fun fetchProduct(code: String): Product? {
    require(validBarcode(code))
    return readCatalog("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$catalogFields")
        ?.optJSONObject("product")?.asProduct()
}

internal fun searchCatalog(query: String): List<FoodDraft> {
    val term = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    val url = "https://search.openfoodfacts.org/search?q=$term&page_size=25&langs=ru&fields=$catalogFields"
    val rows = readCatalog(url)?.optJSONArray("hits") ?: throw IOException("Нет списка продуктов")
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val product = row.asProduct()
            if (product.name.isBlank() || product.energy.isBlank()) continue
            val brands = row.optJSONArray("brands")
            val brand = brands?.let { (0 until it.length()).joinToString(", ") { i -> brands.optString(i) } } ?: row.optString("brands")
            val name = listOf(product.name, brand).filter(String::isNotBlank).joinToString(" · ")
            add(FoodDraft(name = name, weighted = true, energy = product.energy, macros = product.macros))
        }
    }
}

fun FoodStore.cacheProduct(code: String, product: Product) {
    writableDatabase.execSQL("INSERT OR REPLACE INTO product(code,name,energy,protein,fat,carbs) VALUES (?,?,?,?,?,?)", arrayOf(code,product.name,product.energy,product.macros.protein,product.macros.fat,product.macros.carbs))
}

fun FoodStore.lookup(code: String, fetch: (String) -> Product? = ::fetchProduct): ProductResult {
    require(validBarcode(code))
    val cached = readableDatabase.rawQuery("SELECT name,energy,protein,fat,carbs FROM product WHERE code=?",arrayOf(code)).use {
        if(it.moveToFirst()) Product(it.getString(0),it.getString(1),Macros(it.getString(2),it.getString(3),it.getString(4))) else null
    }
    var outcome = ScanOutcome.PHONE
    val product = cached ?: try {
        fetch(code).also {
            outcome = when {
                it == null -> ScanOutcome.MISSING
                it.energy.isBlank() -> ScanOutcome.NO_ENERGY
                else -> {
                    cacheProduct(code, it)
                    ScanOutcome.CATALOG
                }
            }
        }
    } catch (e: IOException) {
        outcome = ScanOutcome.UNAVAILABLE
        null
    }
    val hint = when (outcome) {
        ScanOutcome.PHONE -> "Сохранено на телефоне"
        ScanOutcome.CATALOG -> "Данные: Open Food Facts · ODbL"
        ScanOutcome.MISSING, ScanOutcome.NO_ENERGY -> "Введите данные с упаковки"
        ScanOutcome.UNAVAILABLE -> "Каталог недоступен. Введите данные с упаковки."
    }
    return ProductResult(FoodDraft(name=product?.name.orEmpty(),energy=product?.energy.orEmpty(),
        weighted=true,barcode=code,macros=product?.macros ?: Macros()), hint, outcome)
}

data class SetDraft(val id: Long? = null, val group: String = UUID.randomUUID().toString(),
    val exercise: String = "", val weight: String = "", val reps: String = "", val planned: Boolean = false, val warmup: Boolean = false) {
    val valid get() = exercise.isNotBlank() && (weight.isBlank() || weight.amount() != null) && (reps.toIntOrNull() ?: 0) > 0
}
data class Workout(val id: Long,val sets: List<SetDraft>, val title: String = "", val minutes: Int? = null)
internal val workoutMinutesRange = 1..1440

fun FoodStore.setDuration(session: Long, minutes: Int?) {
    require(minutes == null || minutes in workoutMinutesRange)
    writableDatabase.execSQL("UPDATE workout SET minutes=? WHERE id=?", arrayOf<Any>(minutes ?: 0, session))
}

fun FoodStore.newWorkout(day: LocalDate): Long = writableDatabase.insertOrThrow("workout",null,ContentValues().apply { put("day",day.toString()) })
fun FoodStore.saveSet(session: Long,set: SetDraft, planned: Boolean = false) {
    require(set.valid)
    val values = ContentValues().apply {
        put("session",session)
        put("exercise_group",set.group)
        put("exercise",set.exercise.trim())
        put("weight",set.weight.trim())
        put("reps",set.reps.toInt())
        put("warmup", if (set.warmup) 1 else 0)
        put("planned", if (planned) 1 else 0)
    }
    if(set.id == null) writableDatabase.insertOrThrow("lifting",null,values)
    else writableDatabase.update("lifting",values,"session=? AND id=?",arrayOf(session.toString(),set.id.toString()))
    writableDatabase.execSQL(
        "UPDATE lifting SET exercise=? WHERE session=? AND exercise_group=? AND exercise<>?",
        arrayOf(set.exercise.trim(), session, set.group, set.exercise.trim())
    )
}

data class PreviousSet(val weight: String, val reps: String, val warmup: Boolean = false)

fun FoodStore.repeatWorkout(source: Workout, date: LocalDate) {
    val target = writableDatabase.insertOrThrow("workout", null, ContentValues().apply {
        put("day", date.toString())
        put("title", source.title)
    })
    val groups = mutableMapOf<String, String>()
    for (set in source.sets.filterNot { it.planned }) {
        writableDatabase.insertOrThrow("lifting", null, ContentValues().apply {
            put("session", target)
            put("exercise_group", groups.getOrPut(set.group) { UUID.randomUUID().toString() })
            put("exercise", set.exercise)
            put("weight", set.weight)
            put("reps", set.reps.toInt())
            put("warmup", if (set.warmup) 1 else 0)
            put("planned", 1)
        })
    }
}
data class PreviousExercise(val day: LocalDate, val sets: List<PreviousSet>)

fun FoodStore.previousExercise(day: LocalDate, session: Long, exercise: String): PreviousExercise? {
    val query = """SELECT w.day, l.weight, l.reps,l.warmup FROM lifting l JOIN workout w ON w.id=l.session
        WHERE l.planned=0 AND l.exercise=? AND w.id=(
            SELECT p.id FROM workout p JOIN lifting s ON s.session=p.id
            WHERE s.planned=0 AND s.exercise=? AND (p.day < ? OR (p.day=? AND p.id < ?))
            ORDER BY p.day DESC, p.id DESC LIMIT 1)
        ORDER BY l.id"""
    val date = day.toString()
    return readableDatabase.rawQuery(query, arrayOf(exercise, exercise, date, date, session.toString())).use { rows ->
        if (!rows.moveToFirst()) return@use null
        val lastDay = LocalDate.parse(rows.getString(0))
        PreviousExercise(lastDay, buildList {
            do { add(PreviousSet(rows.getString(1), rows.getString(2), rows.getInt(3) == 1)) } while (rows.moveToNext())
        })
    }
}

fun FoodStore.workouts(day: LocalDate): List<Workout> {
    val result = mutableListOf<Workout>()
    var sets = mutableListOf<SetDraft>()
    readableDatabase.rawQuery("""SELECT w.id,l.id,l.exercise_group,l.exercise,l.weight,l.reps,w.title,l.planned,l.warmup,w.minutes
        FROM workout w LEFT JOIN lifting l ON w.id=l.session WHERE w.day=? ORDER BY w.id,l.id""",arrayOf(day.toString())).use {
        while(it.moveToNext()) {
            val id = it.getLong(0)
            if (result.lastOrNull()?.id != id) {
                sets = mutableListOf()
                result.add(Workout(id, sets, it.getString(6), it.getInt(9).takeIf { value -> value > 0 }))
            }
            if(!it.isNull(1)) sets.add(SetDraft(it.getLong(1),it.getString(2),it.getString(3),it.getString(4),it.getString(5),it.getInt(7) == 1,it.getInt(8) == 1))
        }
    }
    return result
}
