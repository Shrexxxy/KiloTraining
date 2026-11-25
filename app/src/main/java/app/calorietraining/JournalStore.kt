package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

internal fun createJournalTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE product (code TEXT PRIMARY KEY, name TEXT NOT NULL, energy TEXT NOT NULL)")
    db.execSQL("CREATE TABLE workout (id INTEGER PRIMARY KEY, day TEXT NOT NULL, title TEXT NOT NULL DEFAULT '')")
    db.execSQL("""CREATE TABLE lifting (id INTEGER PRIMARY KEY, session INTEGER NOT NULL,
        exercise_group TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT NOT NULL, reps INTEGER NOT NULL,
        planned INTEGER NOT NULL DEFAULT 0)""")
    db.execSQL("CREATE INDEX lifting_session ON lifting(session)")
}

data class Product(val name: String, val energy: String, val macros: Macros = Macros(), val brand: String = "")
enum class ScanOutcome { PHONE, SERVER, CATALOG, MISSING, NO_ENERGY, UNAVAILABLE }

data class ProductResult(val draft: FoodDraft, val hint: String, val outcome: ScanOutcome)
fun validBarcode(code: String) = code.length in listOf(8,12,13,14) && code.all { it in '0'..'9' }

private const val catalogFields = "product_name_ru,product_name,brands,nutriments"

private fun readCatalog(connection: HttpURLConnection): JSONObject? {
    try {
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent","CalorieTraining/0.2 (Android food diary)")
        if (connection.responseCode == 404) return null
        if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (e: JSONException) { throw IOException("Некорректный ответ каталога", e) }
}

internal fun JSONObject.asProduct(): Product {
    val nutrients = optJSONObject("nutriments")
    fun nutrient(key: String) = nutrients?.optString(key)?.takeIf { it.amount() != null }.orEmpty()
    return Product(optString("product_name_ru").ifBlank { optString("product_name") },
        nutrient("energy-kcal_100g"), Macros(nutrient("proteins_100g"), nutrient("fat_100g"), nutrient("carbohydrates_100g")), catalogBrand())
}

private fun productUrl(code: String): URL {
    require(validBarcode(code))
    return URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$catalogFields")
}

fun fetchProduct(code: String): Product? =
    productUrl(code).withHttpConnection(::readCatalog)?.optJSONObject("product")?.asProduct()

private suspend fun fetchProductAsync(code: String): Product? =
    productUrl(code).withCancellableHttpConnection(::readCatalog)?.optJSONObject("product")?.asProduct()

private fun catalogSearchUrl(query: String): URL {
    val term = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    return URL("https://search.openfoodfacts.org/search?q=$term&page_size=25&langs=ru&fields=code,$catalogFields")
}

internal suspend fun searchCatalog(query: String): List<FoodDraft> {
    val rows = catalogSearchUrl(query).withCancellableHttpConnection(::readCatalog)?.optJSONArray("hits")
        ?: throw IOException("Нет списка продуктов")
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val product = row.asProduct()
            if (product.name.isBlank() || product.energy.isBlank()) continue
            add(MyProduct(name = product.name, brand = product.brand, energy = product.energy,
                barcode = row.optString("code").takeIf(::validBarcode),
                macros = product.macros, source = "openfoodfacts").toDraft())
        }
    }
}

private fun JSONObject.catalogBrand(): String =
    optJSONArray("brands")?.let { brands ->
        (0 until brands.length()).joinToString(", ") { brands.optString(it) }
    } ?: optString("brands")

fun FoodStore.cacheProduct(code: String, product: Product) {
    saveMyProduct(MyProduct(name = product.name, brand = product.brand, barcode = code,
        energy = product.energy, macros = product.macros, source = "openfoodfacts"))
}

internal suspend fun FoodStore.lookup(
    code: String,
    server: ProductServer?,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    fetch: suspend (String) -> Product? = ::fetchProductAsync
): ProductResult {
    require(validBarcode(code))
    localLookup(code)?.let { return it }
    suspend fun fromCatalog() = try {
        catalogResult(code, fetch(code))
    } catch (_: IOException) {
        catalogUnavailable(code)
    }

    return coroutineScope {
        val catalog = async(dispatcher) { fromCatalog() }
        if (server == null) return@coroutineScope catalog.await()

        val own = async(dispatcher) {
            try {
                server.byBarcode(code)?.let {
                    ProductResult(it.toDraft(), "Найдено в вашей базе", ScanOutcome.SERVER)
                }
            } catch (_: IOException) {
                null
            }
        }
        select {
            own.onAwait { result ->
                if (result == null) catalog.await() else {
                    catalog.cancel()
                    result
                }
            }
            catalog.onAwait { result ->
                val preferred = withTimeoutOrNull(1_000) { own.await() }
                own.cancel()
                preferred ?: result
            }
        }
    }
}

private fun FoodStore.localLookup(code: String): ProductResult? =
    myProductByBarcode(code)?.let { ProductResult(it.toDraft(), "Сохранено на телефоне", ScanOutcome.PHONE) }

fun FoodStore.lookup(code: String, fetch: (String) -> Product? = ::fetchProduct): ProductResult {
    require(validBarcode(code))
    return localLookup(code) ?: lookupCatalog(code, fetch)
}

private fun lookupCatalog(code: String, fetch: (String) -> Product?): ProductResult {
    val product = try {
        fetch(code)
    } catch (e: IOException) {
        return catalogUnavailable(code)
    }
    return catalogResult(code, product)
}

private fun catalogUnavailable(code: String) = ProductResult(FoodDraft(weighted = true, barcode = code),
    "Каталог недоступен. Введите данные с упаковки.", ScanOutcome.UNAVAILABLE)

private fun catalogResult(code: String, product: Product?): ProductResult {
    if (product == null || product.energy.isBlank()) {
        return ProductResult(FoodDraft(name = product?.name.orEmpty(), weighted = true, barcode = code,
            macros = product?.macros ?: Macros(), brand = product?.brand.orEmpty()),
            "Введите данные с упаковки", if (product == null) ScanOutcome.MISSING else ScanOutcome.NO_ENERGY)
    }
    val draft = MyProduct(name = product.name, brand = product.brand, barcode = code,
        energy = product.energy, macros = product.macros, source = "openfoodfacts").toDraft()
    return ProductResult(draft, "Данные: Open Food Facts · ODbL", ScanOutcome.CATALOG)
}

data class SetDraft(val id: Long? = null, val group: String = UUID.randomUUID().toString(),
    val exercise: String = "", val weight: String = "", val reps: String = "", val planned: Boolean = false,
    val warmup: Boolean = false, val doneAt: LocalDateTime? = null) {
    val valid get() = exercise.isNotBlank() && (weight.isBlank() || weight.amount() != null) && (reps.toIntOrNull() ?: 0) > 0
}
data class Workout(val id: Long,val sets: List<SetDraft>, val title: String = "", val minutes: Int? = null,
    val day: LocalDate = LocalDate.now(), val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null, val live: Boolean = false) {
    val active get() = live && finishedAt == null
    /** Записанная задним числом или старая тренировка считается завершённой, когда все подходы выполнены. */
    val completed get() = finishedAt != null || (!live && sets.isNotEmpty() && sets.none { it.planned })
    val status get() = when {
        active -> WorkoutStatus.Active
        completed -> WorkoutStatus.Completed
        !live && sets.any { it.planned } -> WorkoutStatus.Plan
        else -> null
    }
    val lastDoneAt get() = sets.filterNot { it.planned }.mapNotNull { it.doneAt }.maxOrNull()

    internal fun restBySet(): Map<Long?, Duration> {
        val completed = sets.filter { !it.planned && it.doneAt != null }
            .sortedWith(compareBy({ it.doneAt }, { it.id }))
        return completed.zipWithNext().associate { (previous, next) ->
            next.id to Duration.between(previous.doneAt, next.doneAt)
        }
    }
}
enum class WorkoutStatus(val label: String) { Active("Идёт"), Completed("Завершена"), Plan("План") }

internal val workoutMinutesRange = 1..1440
private val workoutMinuteFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
private val workoutSecondFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
private const val workoutSortTime = "COALESCE(w.started_at, w.day || 'T00:00')"

internal fun addWorkoutTimes(db: SQLiteDatabase) {
    db.execSQL("ALTER TABLE workout ADD COLUMN started_at TEXT")
    db.execSQL("ALTER TABLE workout ADD COLUMN finished_at TEXT")
    db.execSQL("ALTER TABLE workout ADD COLUMN live INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE lifting ADD COLUMN done_at TEXT")
}

fun FoodStore.setDuration(session: Long, minutes: Int?) {
    require(minutes == null || minutes in workoutMinutesRange)
    writableDatabase.execSQL("UPDATE workout SET minutes=? WHERE id=?", arrayOf<Any>(minutes ?: 0, session))
}

fun FoodStore.newWorkout(day: LocalDate): Long = writableDatabase.insertOrThrow("workout",null,ContentValues().apply { put("day",day.toString()) })

internal fun FoodStore.newWorkout(start: LocalDateTime, live: Boolean): Long {
    var session = 0L
    writableDatabase.atomic {
        if (live) {
            finishInactiveWorkouts()
            writableDatabase.rawQuery("SELECT id FROM workout WHERE live=1 AND finished_at IS NULL LIMIT 1", null).use {
                if (it.moveToFirst()) session = it.getLong(0)
            }
        }
        if (session == 0L) session = writableDatabase.insertOrThrow("workout", null, ContentValues().apply {
            put("day", start.toLocalDate().toString())
            put("started_at", start.format(workoutMinuteFormat))
            put("live", if (live) 1 else 0)
        })
    }
    return session
}

internal fun FoodStore.changeWorkoutStart(session: Long, start: LocalDateTime) {
    writableDatabase.execSQL("UPDATE workout SET day=?, started_at=? WHERE id=?",
        arrayOf<Any>(start.toLocalDate().toString(), start.format(workoutMinuteFormat), session))
}

internal fun FoodStore.moveWorkout(session: Long, date: LocalDate) {
    writableDatabase.execSQL("""UPDATE workout SET day=?, started_at=CASE WHEN started_at IS NULL THEN NULL
        ELSE ? || substr(started_at,11) END WHERE id=?""", arrayOf<Any>(date.toString(), date.toString(), session))
}

internal fun FoodStore.finishWorkout(session: Long, at: LocalDateTime = LocalDateTime.now(clock)) {
    val workout = workouts(session = session).singleOrNull() ?: return
    val start = workout.startedAt ?: return
    if (!workout.active) return
    val elapsed = Duration.between(start, at).coerceAtLeast(Duration.ZERO)
    val minutes = (elapsed.toMinutes() + if (elapsed.minusMinutes(elapsed.toMinutes()).isZero) 0 else 1)
        .coerceAtMost(1440)
    writableDatabase.execSQL("UPDATE workout SET finished_at=?,minutes=? WHERE id=? AND finished_at IS NULL",
        arrayOf<Any>(at.format(workoutMinuteFormat), minutes, session))
}

internal fun FoodStore.finishInactiveWorkouts() {
    val cutoff = LocalDateTime.now(clock).minusHours(3)
    workouts(activeOnly = true).forEach { workout ->
        val last = workout.lastDoneAt ?: workout.startedAt ?: return@forEach
        if (last <= cutoff) finishWorkout(workout.id, last)
    }
}

// Время выполнения пишем только в идущей тренировке: в записанной задним числом это было бы время ввода, а не подхода.
private fun FoodStore.liveSetTime(session: Long): String? =
    readableDatabase.rawQuery("SELECT 1 FROM workout WHERE id=? AND live=1 AND finished_at IS NULL",
        arrayOf(session.toString())).use { if (it.moveToFirst()) LocalDateTime.now(clock).format(workoutSecondFormat) else null }

internal fun FoodStore.completeSet(session: Long, setId: Long) {
    writableDatabase.execSQL("UPDATE lifting SET planned=0,done_at=? WHERE session=? AND id=? AND planned=1",
        arrayOf<Any?>(liveSetTime(session), session, setId))
}

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
    val db = writableDatabase
    db.atomic {
        if (set.id == null) {
            if (!planned) liveSetTime(session)?.let { values.put("done_at", it) }
            db.insertOrThrow("lifting", null, values)
        } else {
            if (planned) values.putNull("done_at")
            else completeSet(session, set.id)
            db.update("lifting", values, "session=? AND id=?", arrayOf(session.toString(), set.id.toString()))
        }
        db.execSQL("UPDATE lifting SET exercise=? WHERE session=? AND exercise_group=? AND exercise<>?",
            arrayOf<Any>(set.exercise.trim(), session, set.group, set.exercise.trim()))
    }
}

data class PreviousSet(val weight: String, val reps: String, val warmup: Boolean = false)

fun FoodStore.repeatWorkout(source: Workout, date: LocalDate): Long {
    var target = 0L
    val groups = mutableMapOf<String, String>()
    val db = writableDatabase
    db.atomic {
        target = newWorkout(LocalDateTime.now(clock).with(date), live = false)
        db.execSQL("UPDATE workout SET title=? WHERE id=?", arrayOf<Any>(source.title, target))
        for (set in source.sets.filterNot { it.planned }) {
            db.insertOrThrow("lifting", null, ContentValues().apply {
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
    return target
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

fun FoodStore.workouts(day: LocalDate): List<Workout> = readWorkouts("w.day=?", arrayOf(day.toString()), "w.id")

internal fun FoodStore.workouts(from: LocalDate, through: LocalDate): List<Workout> =
    readWorkouts("w.day BETWEEN ? AND ?", arrayOf(from.toString(), through.toString()), "w.id")

internal fun FoodStore.workouts(session: Long? = null, activeOnly: Boolean = false): List<Workout> = when {
    session != null -> readWorkouts("w.id=?", arrayOf(session.toString()), "w.id")
    activeOnly -> readWorkouts("w.live=1 AND w.finished_at IS NULL", null, "w.id DESC")
    else -> readWorkouts("1", null, "$workoutSortTime DESC, w.id DESC")
}

private fun FoodStore.readWorkouts(where: String, args: Array<String>?, order: String): List<Workout> {
    val result = mutableListOf<Workout>()
    var sets = mutableListOf<SetDraft>()
    readableDatabase.rawQuery("""SELECT w.id,l.id,l.exercise_group,l.exercise,l.weight,l.reps,w.title,l.planned,l.warmup,w.minutes,
        w.day,w.started_at,w.finished_at,w.live,l.done_at
        FROM workout w LEFT JOIN lifting l ON w.id=l.session WHERE $where ORDER BY $order,l.id""", args).use {
        while(it.moveToNext()) {
            val id = it.getLong(0)
            if (result.lastOrNull()?.id != id) {
                sets = mutableListOf()
                result.add(Workout(id, sets, it.getString(6), it.getInt(9).takeIf { value -> value > 0 },
                    LocalDate.parse(it.getString(10)), it.getString(11)?.let(LocalDateTime::parse),
                    it.getString(12)?.let(LocalDateTime::parse), it.getInt(13) == 1))
            }
            if(!it.isNull(1)) sets.add(SetDraft(it.getLong(1),it.getString(2),it.getString(3),it.getString(4),it.getString(5),
                it.getInt(7) == 1,it.getInt(8) == 1,it.getString(14)?.let(LocalDateTime::parse)))
        }
    }
    return result
}
