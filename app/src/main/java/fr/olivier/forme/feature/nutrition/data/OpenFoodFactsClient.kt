package fr.olivier.forme.feature.nutrition.data

import fr.olivier.forme.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class OffProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val proteinPer100g: Double?,
    val kcalPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val servingG: Double?,
)

sealed interface OffResult {
    data class Found(val product: OffProduct) : OffResult
    data object NotFound : OffResult
    data object Offline : OffResult
    data class Failed(val message: String) : OffResult
}

/**
 * Minimal Open Food Facts client (one GET, no API key). HttpURLConnection is enough here:
 * no HTTP library is added for a single read-only call.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor() {

    suspend fun product(barcode: String): OffResult = withContext(Dispatchers.IO) {
        val url = URL("$BASE/api/v2/product/$barcode.json?fields=$FIELDS&lc=fr")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "GymFlemme/${BuildConfig.VERSION_NAME} (personal Android app)")
            when (connection.responseCode) {
                200 -> parse(barcode, connection.inputStream.use { it.readBytes().decodeToString() })
                404 -> OffResult.NotFound
                else -> OffResult.Failed("HTTP ${connection.responseCode}")
            }
        } catch (e: IOException) {
            OffResult.Offline
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://world.openfoodfacts.org"
        private const val FIELDS = "code,product_name,product_name_fr,brands,nutriments,serving_quantity"
        private const val TIMEOUT_MS = 8_000
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(barcode: String, body: String): OffResult {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { return OffResult.Failed("Réponse illisible") }
            if (root["status"]?.jsonPrimitive?.intOrNull != 1) return OffResult.NotFound
            val product = root["product"]?.jsonObject ?: return OffResult.NotFound
            val nutriments = product["nutriments"] as? JsonObject
            fun text(key: String) = product[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            fun number(key: String) = nutriments?.get(key)?.jsonPrimitive?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
            val name = text("product_name_fr") ?: text("product_name") ?: return OffResult.NotFound
            return OffResult.Found(
                OffProduct(
                    barcode = barcode,
                    name = name,
                    brand = text("brands")?.substringBefore(',')?.trim(),
                    proteinPer100g = number("proteins_100g"),
                    kcalPer100g = number("energy-kcal_100g"),
                    carbsPer100g = number("carbohydrates_100g"),
                    fatPer100g = number("fat_100g"),
                    servingG = product["serving_quantity"]?.jsonPrimitive?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() },
                ),
            )
        }
    }
}
