package app.calorietraining

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal interface ProductServer {
    suspend fun health(): Boolean
    suspend fun byBarcode(code: String): ServerProduct?
    suspend fun search(query: String, limit: Int = 20): List<ServerProduct>
    suspend fun put(card: ServerProduct)
    suspend fun post(card: ServerProduct)
}

internal enum class ServerFailure { OFFLINE, UNAUTHORIZED, INVALID_DATA, OTHER }
internal class ProductServerException(val failure: ServerFailure) : IOException()

internal data class ServerProduct(
    val name: String,
    val kcal: Double,
    val barcode: String? = null,
    val brand: String = "",
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val source: String = "phone"
) {
    fun toDraft(): FoodDraft {
        fun Double?.text() = this?.toBigDecimal()?.display().orEmpty()
        return MyProduct(name = name, brand = brand, barcode = barcode, energy = kcal.text(),
            macros = Macros(protein.text(), fat.text(), carbs.text()),
            source = if (source == "openfoodfacts") source else "manual").toDraft()
    }

    fun json() = JSONObject()
        .put("name", name)
        .put("brand", brand)
        .put("kcal", kcal)
        .put("protein", protein ?: JSONObject.NULL)
        .put("fat", fat ?: JSONObject.NULL)
        .put("carbs", carbs ?: JSONObject.NULL)
}

internal class HttpProductServer(address: String, private val key: String) : ProductServer {
    private val base = address.trim().trimEnd('/')

    override suspend fun health() = request("/health", authenticated = false) {
        JSONObject(it).run { getString("status") == "ok" && getBoolean("databaseAvailable") }
    } == true

    override suspend fun byBarcode(code: String): ServerProduct? {
        require(validBarcode(code))
        return request("/products/$code", allowMissing = true) { JSONObject(it).card() }
    }

    override suspend fun search(query: String, limit: Int): List<ServerProduct> {
        val term = URLEncoder.encode(query.trim(), "UTF-8")
        return requireNotNull(request("/products/search?q=$term&limit=$limit") {
            val rows = JSONArray(it)
            List(rows.length()) { index -> rows.getJSONObject(index).card() }
        })
    }

    override suspend fun put(card: ServerProduct) {
        val code = card.barcode?.takeIf(::validBarcode) ?: throw ProductServerException(ServerFailure.INVALID_DATA)
        request("/products/$code", method = "PUT", body = card.json()) {}
    }

    override suspend fun post(card: ServerProduct) {
        request("/products", method = "POST", body = card.json()) {}
    }

    private suspend fun <T> request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authenticated: Boolean = true,
        allowMissing: Boolean = false,
        parse: (String) -> T
    ): T? = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL(base)
            if ((endpoint.protocol != "http" && endpoint.protocol != "https") || endpoint.host.isBlank() ||
                endpoint.userInfo != null || endpoint.query != null || endpoint.ref != null) {
                throw ProductServerException(ServerFailure.OFFLINE)
            }
            val connection = URL(base + path).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = method
                if (authenticated) connection.setRequestProperty("X-Api-Key", key)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                when (connection.responseCode) {
                    in 200..299 -> parse(if (body == null) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else "")
                    404 -> if (allowMissing) null else throw ProductServerException(ServerFailure.OTHER)
                    401 -> throw ProductServerException(ServerFailure.UNAUTHORIZED)
                    400 -> throw ProductServerException(ServerFailure.INVALID_DATA)
                    else -> throw ProductServerException(ServerFailure.OTHER)
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: ProductServerException) {
            throw error
        } catch (_: IOException) {
            throw ProductServerException(ServerFailure.OFFLINE)
        } catch (_: IllegalArgumentException) {
            throw ProductServerException(ServerFailure.OFFLINE)
        } catch (_: JSONException) {
            throw ProductServerException(ServerFailure.OTHER)
        }
    }
}

private fun JSONObject.card(): ServerProduct {
    fun nutrient(key: String) = if (isNull(key)) null else getDouble(key).takeIf { it in 0.0..100.0 }
    val kcal = getDouble("kcal")
    if (kcal !in 0.0..900.0) throw ProductServerException(ServerFailure.OTHER)
    return ServerProduct(name = getString("name"), kcal = kcal,
        barcode = if (isNull("barcode")) null else getString("barcode"),
        brand = if (isNull("brand")) "" else getString("brand"), protein = nutrient("protein"),
        fat = nutrient("fat"), carbs = nutrient("carbs"), source = getString("source"))
}
