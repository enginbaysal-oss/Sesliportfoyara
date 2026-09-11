package com.example.sesliportfoyara

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class RemaxService {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            })
        }
    }

    suspend fun fetchOfficePortfolios(url: String): List<Portfolio> {
        return try {
            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            val html = response.bodyAsText()
            
            // Extract __NEXT_DATA__
            val regex = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>")
            val matchResult = regex.find(html) ?: return emptyList()
            val jsonString = matchResult.groupValues[1]
            
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonString).jsonObject
            
            // Navigate to listings: props -> pageProps -> office -> listings (or similar)
            // Note: Structure might vary slightly between office and search pages
            val pageProps = root["props"]?.jsonObject?.get("pageProps")?.jsonObject ?: return emptyList()
            
            // If it's an office page
            val officeListings = pageProps["office"]?.jsonObject?.get("listings")?.jsonArray
            
            // If it's a general search page
            val generalListings = pageProps["listings"]?.jsonArray
            
            val listings = officeListings ?: generalListings ?: return emptyList()

            listings.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: ""
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val price = obj["price"]?.jsonPrimitive?.content ?: ""
                    val rooms = obj["roomCount"]?.jsonPrimitive?.content ?: ""
                    val area = obj["squareMeter"]?.jsonPrimitive?.content ?: ""
                    val city = obj["city"]?.jsonPrimitive?.content ?: ""
                    val district = obj["district"]?.jsonPrimitive?.content ?: ""
                    val type = obj["listingType"]?.jsonPrimitive?.content ?: "Satılık"
                    val category = obj["category"]?.jsonPrimitive?.content ?: "Daire"
                    val slug = obj["slug"]?.jsonPrimitive?.content ?: ""

                    Portfolio(
                        id = "remax_$id",
                        title = title,
                        price = price,
                        rooms = rooms,
                        area = area,
                        location = "$city, $district",
                        type = if (type.contains("kira", true)) "Kiralık" else "Satılık",
                        propertyType = category,
                        link = "https://remax.com.tr/tr/portfoy/$slug",
                        createdAt = Clock.now()
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun syncWithFirebase(url: String, dbManager: DatabaseManager) {
        if (url.isBlank() || _isSyncing.value) return
        
        _isSyncing.value = true
        try {
            val fetchedList = fetchOfficePortfolios(url)
            if (fetchedList.isEmpty()) return

            val currentOfficeList = dbManager.getPortfolios().first()
            
            fetchedList.forEach { newP ->
                // Link üzerinden kontrol (Mükerrer önleme)
                val exists = currentOfficeList.any { 
                    it.link == newP.link || (it.id == newP.id && it.id.isNotEmpty())
                }
                
                if (!exists) {
                    dbManager.addPortfolio(newP)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }
}

val LocalRemaxServiceProvider = staticCompositionLocalOf<RemaxService> {
    error("RemaxService not provided")
}
