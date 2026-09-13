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

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun decodeFlight(html: String): String {
        // self.__next_f.push([1,"..."]) parçalarını yakala
        val regex = Regex("""self\.__next_f\.push\(\[1,"((?:[^"\\]|\\.)*)"\]\)""")
        val combined = StringBuilder()
        regex.findAll(html).forEach { match ->
            val chunk = match.groupValues[1]
            try {
                // Unescape JSON string
                val unescaped = json.parseToJsonElement("\"$chunk\"").jsonPrimitive.content
                combined.append(unescaped)
            } catch (e: Exception) {
                combined.append(chunk.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n"))
            }
        }
        return combined.toString()
    }

    private fun parseFlightRows(flight: String): Map<String, String> {
        val rows = mutableMapOf<String, String>()
        val bytes = flight.encodeToByteArray()
        var p = 0
        while (p < bytes.size) {
            var q = p
            while (q < bytes.size && bytes[q] != ':'.code.toByte()) q++
            if (q >= bytes.size) break
            
            val id = bytes.decodeToString(p, q)
            if (!id.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                val nl = indexOfByte(bytes, '\n'.code.toByte(), p)
                if (nl < 0) break
                p = nl + 1
                continue
            }
            
            q++
            if (q < bytes.size && bytes[q] == 'T'.code.toByte()) {
                val comma = indexOfByte(bytes, ','.code.toByte(), q)
                if (comma < 0) break
                val lenHex = bytes.decodeToString(q + 1, comma)
                val byteLen = lenHex.toIntOrNull(16) ?: 0
                val start = comma + 1
                val end = (start + byteLen).coerceAtMost(bytes.size)
                rows[id] = bytes.decodeToString(start, end)
                p = end
                if (p < bytes.size && bytes[p] == '\n'.code.toByte()) p++
            } else {
                var nl = indexOfByte(bytes, '\n'.code.toByte(), q)
                if (nl < 0) nl = bytes.size
                rows[id] = bytes.decodeToString(q, nl)
                p = nl + 1
            }
        }
        return rows
    }

    private fun indexOfByte(array: ByteArray, target: Byte, start: Int): Int {
        for (i in start until array.size) {
            if (array[i] == target) return i
        }
        return -1
    }

    private fun extractObjectStringByKey(flight: String, key: String): String? {
        val searchKey = "\"$key\""
        val k = flight.indexOf(searchKey)
        if (k < 0) return null
        
        val start = flight.indexOf("{", k)
        if (start < 0) return null
        
        var depth = 0
        var inStr = false
        var esc = false
        
        for (i in start until flight.length) {
            val c = flight[i]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return flight.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun resolveRefs(element: JsonElement, rows: Map<String, String>): JsonElement {
        return when (element) {
            is JsonObject -> JsonObject(element.mapValues { resolveRefs(it.value, rows) })
            is JsonArray -> JsonArray(element.map { resolveRefs(it, rows) })
            is JsonPrimitive -> {
                val content = element.content
                if (content.startsWith("$")) {
                    val refId = if (content.startsWith("\$L")) content.substring(2) else content.substring(1)
                    if (refId.isNotEmpty() && refId.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                        val rowValue = rows[refId]
                        if (rowValue != null) {
                            try {
                                val parsed = json.parseToJsonElement(rowValue)
                                return resolveRefs(parsed, rows)
                            } catch (e: Exception) {
                                return JsonPrimitive(rowValue)
                            }
                        }
                    }
                }
                element
            }
        }
    }

    private fun extractPhoneFromText(text: String): String {
        // Turkish phone formats: 0532 123 45 67, 532 123 45 67, (532) 123 45 67, +90... etc.
        val regex = Regex("""(?:\+90|0|90)?\s*\(?([5][0-9]{2})\)?\s*[-.\s]*([0-9]{3})\s*[-.\s]*([0-9]{2})\s*[-.\s]*([0-9]{2})""")
        val match = regex.find(text)
        return if (match != null) {
            "0${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}${match.groupValues[4]}"
        } else ""
    }

    suspend fun fetchOfficePortfolios(url: String, page: Int = 1): List<Portfolio> {
        return try {
            val pagedUrl = if (url.contains("page=")) {
                url.replace(Regex("page=\\d+"), "page=$page")
            } else {
                url + (if (url.contains("?")) "&" else "?") + "page=$page"
            }
            
            val response = client.get(pagedUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            
            val html = response.bodyAsText()
            val flight = decodeFlight(html)
            if (flight.isEmpty()) return emptyList()

            val rows = parseFlightRows(flight)
            val rawJson = extractObjectStringByKey(flight, "officeDetailPropertyListingData")
            if (rawJson == null) return emptyList()
            
            val root = json.parseToJsonElement(rawJson)
            val resolved = resolveRefs(root, rows)
            
            val listingsArray = resolved.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray
            if (listingsArray == null) return emptyList()

            listingsArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val code = obj["code"]?.jsonPrimitive?.content ?: ""
                    if (code.isBlank()) return@mapNotNull null
                    
                    val title = obj["title"]?.jsonArray?.firstOrNull { it.jsonObject["languageId"]?.jsonPrimitive?.int == 1 }
                                ?.jsonObject?.get("text")?.jsonPrimitive?.content 
                                ?: obj["title"]?.jsonPrimitive?.content ?: ""

                    val priceInfo = obj["priceInfo"]?.jsonObject
                    val amount = priceInfo?.get("amount")?.jsonPrimitive?.content ?: ""
                    val symbol = priceInfo?.get("amountTypeSymbol")?.jsonPrimitive?.content ?: ""
                    
                    val city = obj["cityName"]?.jsonPrimitive?.content ?: ""
                    val district = obj["townName"]?.jsonPrimitive?.content ?: ""
                    val neighborhood = obj["neighborhoodName"]?.jsonPrimitive?.content ?: ""
                    val location = listOf(city, district, neighborhood).filter { it.isNotEmpty() }.joinToString(", ")

                    val consultant = obj["employeeName"]?.jsonPrimitive?.content ?: ""
                    var phone = obj["employeePhone"]?.jsonPrimitive?.content ?: ""
                    
                    // Diğer olası telefon anahtarlarını kontrol et
                    if (phone.isEmpty()) phone = obj["employeeMobilePhone"]?.jsonPrimitive?.content ?: ""
                    if (phone.isEmpty()) phone = obj["mobilePhone"]?.jsonPrimitive?.content ?: ""
                    if (phone.isEmpty()) phone = obj["officePhone"]?.jsonPrimitive?.content ?: ""
                    
                    if (phone.isEmpty()) {
                        val descArray = obj["description"]?.jsonArray
                        val desc = descArray?.firstOrNull { it.jsonObject["languageId"]?.jsonPrimitive?.int == 1 }
                                    ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                        phone = extractPhoneFromText(desc)
                    } else {
                        // Var olan telefonu temizleyip formatla (sadece rakam kalsın, başına 0 ekle eğer yoksa)
                        val digits = phone.filter { it.isDigit() }
                        phone = if (digits.startsWith("5") && digits.length == 10) "0$digits"
                               else if (digits.startsWith("905") && digits.length == 12) "0${digits.substring(2)}"
                               else if (digits.startsWith("05") && digits.length == 11) digits
                               else phone
                    }

                    Portfolio(
                        id = "remax_$code",
                        title = title,
                        price = if (amount.isNotEmpty()) "$amount $symbol" else "",
                        rooms = obj["roomOptions"]?.jsonPrimitive?.content ?: "",
                        area = obj["m2Area"]?.jsonPrimitive?.content ?: "",
                        location = location,
                        consultantName = consultant,
                        consultantPhone = phone,
                        type = if ((obj["operationName"]?.jsonPrimitive?.content ?: "").contains("kira", true)) "Kiralık" else "Satılık",
                        propertyType = obj["categoryName"]?.jsonPrimitive?.content ?: "Daire",
                        link = "https://www.remax.com.tr/tr/portfoy/$code",
                        createdAt = Clock.now()
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun syncWithFirebase(url: String, dbManager: DatabaseManager): Int {
        if (url.isBlank() || _isSyncing.value) return 0
        var totalAdded = 0
        var currentPage = 1
        _isSyncing.value = true
        try {
            val current = try { dbManager.getPortfolios().first() } catch (e: Exception) { emptyList<Portfolio>() }
            val existingIds = current.map { it.id }.toMutableSet()
            val existingLinks = current.map { l -> 
                l.link.replace("https://", "").replace("www.", "").removeSuffix("/") 
            }.toMutableSet()

            while (currentPage <= 10) {
                val list = fetchOfficePortfolios(url, currentPage)
                if (list.isEmpty()) break
                list.forEach { p ->
                    val norm = p.link.replace("https://", "").replace("www.", "").removeSuffix("/")
                    if (!existingIds.contains(p.id) && !existingLinks.contains(norm)) {
                        if (dbManager.addPortfolio(p) != null) {
                            existingIds.add(p.id)
                            existingLinks.add(norm)
                            totalAdded++
                        }
                    }
                }
                if (list.size < 10) break
                currentPage++
            }
        } catch (e: Exception) {
            println("RemaxService: Sync error ${e.message}")
        } finally {
            _isSyncing.value = false
        }
        return totalAdded
    }
}

val LocalRemaxServiceProvider = staticCompositionLocalOf<RemaxService> {
    error("RemaxService not provided")
}
