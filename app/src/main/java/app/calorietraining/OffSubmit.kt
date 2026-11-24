package app.calorietraining

import android.content.Context
import android.content.ContentValues
import android.database.SQLException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal data class OffStatus(val enabled: Boolean, val test: Boolean)

internal fun MyProduct.canShareWithOff(): Boolean {
    val code = barcode ?: return false
    return id != null && source == "manual" && weighted && name.isNotBlank() && validBarcode(code) &&
        code.reversed().mapIndexed { index, digit -> (digit - '0') * if (index % 2 == 0) 1 else 3 }.sum() % 10 == 0 &&
        energy.amount()?.let { it <= 900.toBigDecimal() } == true &&
        listOf(macros.protein, macros.fat, macros.carbs).all { value ->
            value.amount()?.let { it <= 100.toBigDecimal() } == true
        }
}

internal fun MyProduct.hasOffCalorieWarning(): Boolean {
    val kcal = requireNotNull(energy.amount())
    val macroKcal = requireNotNull(macros.protein.amount()) * 4.toBigDecimal() +
        requireNotNull(macros.fat.amount()) * 9.toBigDecimal() +
        requireNotNull(macros.carbs.amount()) * 4.toBigDecimal()
    return (macroKcal - kcal).abs() > maxOf(10.toBigDecimal(), kcal.movePointLeft(1))
}

internal class OffSubmitModel(private val app: Context) : ViewModel() {
    var status by mutableStateOf<OffStatus?>(null)
        private set
    var sending by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set

    suspend fun load(address: String, key: String) {
        if (address.isBlank() || key.isBlank()) {
            status = null
            return
        }
        status = OffSubmitClient(address, key).status()
    }

    fun clearResult() {
        if (!sending) result = null
    }

    fun submit(card: MyProduct, address: String, key: String) {
        if (sending || status?.enabled != true || !card.canShareWithOff()) return
        sending = true
        result = null
        viewModelScope.launch {
            try {
                result = send(card, address, key)
            } finally {
                sending = false
            }
        }
    }

    private suspend fun send(card: MyProduct, address: String, key: String): String = withContext(Dispatchers.IO) {
        val code = requireNotNull(card.barcode)
        try {
            FoodStore(app).use { store ->
                productSyncLock.withLock {
                    if (store.myProductByBarcode(code) != card) return@withLock SaveFirst
                    val pending = store.readableDatabase.rawQuery(
                        "SELECT pending_sync FROM my_product WHERE id=? AND updated_at=?",
                        arrayOf(card.id.toString(), card.updatedAt)
                    ).use { rows -> if (rows.moveToFirst()) rows.getInt(0) == 1 else null }
                    if (pending == null) return@withLock SaveFirst
                    if (pending) {
                        try {
                            HttpProductServer(address, key).put(requireNotNull(card.serverCard()))
                        } catch (_: ProductServerException) {
                            return@withLock SaveFirst
                        }
                        val synced = ContentValues().apply { put("pending_sync", 0) }
                        val updated = store.writableDatabase.update("my_product", synced,
                            "id=? AND updated_at=? AND pending_sync=1", arrayOf(card.id.toString(), card.updatedAt))
                        if (updated != 1) return@withLock SaveFirst
                        ProductServerSettings.queueChanged()
                    }
                    if (store.myProductByBarcode(code) != card) return@withLock SaveFirst
                    OffSubmitClient(address, key).submit(code)
                }
            }
        } catch (_: SQLException) {
            SaveFirst
        }
    }

    private companion object {
        const val SaveFirst = "Сначала сохраните товар на своём сервере"
    }
}

private class OffSubmitClient(address: String, private val key: String) {
    private val base = address.trim().trimEnd('/')

    suspend fun status(): OffStatus? = withContext(Dispatchers.IO) {
        try {
            endpoint("/off-submissions/status").withCancellableHttpConnection { connection ->
                connection.connectTimeout = 3_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("X-Api-Key", key)
                if (connection.responseCode != 200) return@withCancellableHttpConnection null
                val body = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                OffStatus(body.getBoolean("enabled"), body.getBoolean("test"))
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: JSONException) {
            null
        }
    }

    fun submit(code: String): String = try {
        endpoint("/products/$code/off-submission").withHttpConnection { connection ->
            connection.connectTimeout = 3_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-Api-Key", key)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(0)
            connection.outputStream.close()
            when (connection.responseCode) {
                in 200..299 -> "Отправлено"
                409 -> {
                    val title = try {
                        connection.errorStream?.bufferedReader()?.use { JSONObject(it.readText()).optString("title") }
                    } catch (_: JSONException) {
                        null
                    }
                    if (title == AlreadyExists) AlreadyExists else Rejected
                }
                503 -> if (connection.getHeaderField("Retry-After") == null) "Отправка выключена на сервере"
                    else Rejected
                else -> Rejected
            }
        }
    } catch (_: IOException) {
        "Нет связи с сервером"
    } catch (_: IllegalArgumentException) {
        "Нет связи с сервером"
    }

    private companion object {
        const val AlreadyExists = "Товар уже есть в Open Food Facts"
        const val Rejected = "Open Food Facts не принял данные"
    }

    private fun endpoint(path: String): URL {
        val url = URL(base)
        require(url.protocol in listOf("http", "https") && url.host.isNotBlank() &&
            url.userInfo == null && url.query == null && url.ref == null)
        return URL(base + path)
    }
}
