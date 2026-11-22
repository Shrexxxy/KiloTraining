package app.calorietraining

import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ProductSyncReport(
    val sent: Int = 0,
    val unsuitable: Int = 0,
    val failure: ServerFailure? = null
)

internal fun FoodStore.pendingSyncCount(): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM my_product WHERE pending_sync=1", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

private val syncLock = Mutex()

internal suspend fun FoodStore.syncProducts(server: ProductServer): ProductSyncReport = withContext(Dispatchers.IO) {
    syncLock.withLock {
        val pending = readableDatabase.rawQuery("$selectCard WHERE pending_sync=1 ORDER BY id", null).use { rows ->
            buildList { while (rows.moveToNext()) add(rows.card()) }
        }
        val processed = mutableListOf<MyProduct>()
        var sent = 0
        var unsuitable = 0
        var failure: ServerFailure? = null

        for (product in pending) {
            if (!product.weighted) {
                processed.add(product)
                continue
            }
            val card = product.serverCard()
            if (card == null) {
                unsuitable++
                continue
            }
            try {
                if (card.barcode == null) server.post(card) else server.put(card)
                sent++
                processed.add(product)
            } catch (error: ProductServerException) {
                if (error.failure == ServerFailure.INVALID_DATA) {
                    unsuitable++
                } else {
                    failure = error.failure
                    break
                }
            }
        }
        val db = writableDatabase
        val synced = ContentValues().apply { put("pending_sync", 0) }
        db.atomic {
            for (card in processed) {
                db.update("my_product", synced, "id=? AND updated_at=? AND pending_sync=1",
                    arrayOf(card.id.toString(), card.updatedAt))
            }
        }
        ProductSyncReport(sent, unsuitable, failure)
    }
}

private fun MyProduct.serverCard(): ServerProduct? {
    fun String.inRange(maximum: Int) = amount()?.takeIf { it <= maximum.toBigDecimal() }?.toDouble()
    val kcal = energy.inRange(900) ?: return null
    return ServerProduct(name = name, brand = brand, barcode = barcode, kcal = kcal,
        protein = macros.protein.inRange(100), fat = macros.fat.inRange(100), carbs = macros.carbs.inRange(100))
}

internal class ProductServerSettings(
    private val context: Context,
    private val factory: (String, String) -> ProductServer = ::HttpProductServer
) {
    private val preferences = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    val address get() = preferences.getString("address", "").orEmpty()
    val key get() = preferences.getString("key", "").orEmpty()
    val lastSent get() = preferences.getLong("lastSent", 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    fun setAddress(value: String) = preferences.edit().putString("address", value).apply()
    fun setKey(value: String) = preferences.edit().putString("key", value).apply()

    fun client() = address.trim().takeIf(String::isNotBlank)?.let { factory(it, key.trim()) }

    suspend fun send(): ProductSyncReport? {
        val server = client() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                FoodStore(context).use { it.syncProducts(server) }.also { report ->
                    if (report.sent > 0) preferences.edit().putLong("lastSent", System.currentTimeMillis()).apply()
                }
            } catch (_: SQLException) {
                ProductSyncReport(failure = ServerFailure.OTHER)
            } finally {
                queueChanged()
            }
        }
    }

    companion object {
        private val revision = MutableStateFlow(0L)
        val changes = revision.asStateFlow()

        fun queueChanged() = revision.update { it + 1 }
    }
}
