package com.example.sesliportfoyara

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

class RemaxService {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing
    var lastSyncMessage: String = ""
    // Son başarılı ağ okuması; bulut reddederse arayüz yerel olarak gösterebilir.
    var lastFetchedPortfolios: List<Portfolio> = emptyList()
        private set

    // CORS PROXY: Web (wasmJs) tarafında Remax'ın sunucusu doğrudan istek
    // atmamıza CORS politikası nedeniyle izin vermiyor. Bu yüzden istekleri
    // kendi Cloudflare Worker'ımız üzerinden yönlendiriyoruz. Mobil (Android)
    // tarafında CORS kısıtlaması olmadığı için bu proxy orada da sorunsuz
    // çalışır, herhangi bir olumsuz etkisi yoktur.
    private val proxyBaseUrls = listOf(
        "https://jcjerwvibjetomqeelsy.supabase.co/functions/v1/remax-page-proxy?url=",
        "https://sesliportfoy-proxy.engin-baysal.workers.dev/?url="
    )

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

    // Read-only public RE/MAX İlyada 3 feed; no Firebase write/clear required.
    // This is a published listing snapshot, not client/CRM data.
    suspend fun fetchPublishedOfficePortfolios(): List<Portfolio> {
        return try {
            val feed = "https://enginbaysal-oss.github.io/Sesliportfoyara/data/ilyada3-portfoyler.json"
            val response = withTimeoutOrNull(15000L) {
                client.get(feed + "?t=" + Clock.now())
            }
            if (response == null || !response.status.isSuccess()) {
                lastSyncMessage = "RE/MAX ofis ilan servisine erişilemiyor. Eski kayıtlar korunuyor."
                return emptyList()
            }
            val parsed = json.decodeFromString<List<Portfolio>>(response.bodyAsText())
                .filter { p ->
                    p.id.startsWith("remax_P") &&
                    p.link.startsWith("https://remax.com.tr/tr/portfoy/") &&
                    normalizeOfficeName(p.officeName) == normalizeOfficeName("RE/MAX İlyada 3")
                }
                .distinctBy { it.id }
            if (parsed.isEmpty()) {
                lastSyncMessage = "RE/MAX ilan dosyası boş veya geçersiz. Eski kayıtlar korunuyor."
            } else {
                lastSyncMessage = "${parsed.size} güncel RE/MAX İlyada 3 portföyü başarıyla okundu."
            }
            parsed
        } catch (e: Exception) {
            lastSyncMessage = "İlan servisi geçici olarak okunamadı; mevcut kayıtlar korunuyor."
            emptyList()
        }
    }

    private fun decodeFlight(html: String): String {
        val combined = StringBuilder()
        var index = 0
        val target = "self.__next_f.push([1,\""
        
        while (true) {
            val startIdx = html.indexOf(target, index)
            if (startIdx == -1) break
            
            val strStart = startIdx + target.length
            var i = strStart
            var esc = false
            var endIdx = -1
            
            while (i < html.length) {
                val c = html[i]
                if (esc) {
                    esc = false
                } else if (c == '\\') {
                    esc = true
                } else if (c == '"') {
                    if (i + 2 < html.length && html[i + 1] == ']' && html[i + 2] == ')') {
                        endIdx = i
                        break
                    }
                }
                i++
            }
            
            if (endIdx != -1) {
                val chunk = html.substring(strStart, endIdx)
                try {
                    val unescaped = json.parseToJsonElement("\"$chunk\"").jsonPrimitive.content
                    combined.append(unescaped)
                } catch (e: Exception) {
                    combined.append(chunk.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n"))
                }
                index = endIdx + 3
            } else {
                index = strStart + 1
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

    // Next.js veri formatı değişse bile HTML'deki ilan kartlarını okuyabilen yedek.
    private fun extractPortfoliosFromHtml(html: String): List<Portfolio> {
        fun plain(value: String): String = value
            .replace(Regex("""(?is)<script\b[^>]*>.*?</script>"""), " ")
            .replace(Regex("""(?is)<style\b[^>]*>.*?</style>"""), " ")
            .replace(Regex("""(?is)<[^>]+>"""), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ").trim()

        val anchors = Regex("""(?is)<a\b[^>]*\bhref\s*=\s*["']([^"']*?/portfoy/[Pp]\d+[^"']*)["'][^>]*>""")
        val codePattern = Regex("""(?i)/portfoy/(P\d+)""")
        val titlePattern = Regex("""(?is)<h[1-6]\b[^>]*>(.*?)</h[1-6]>""")
        val altPattern = Regex("""(?is)<img\b[^>]*\balt\s*=\s*["']([^"']+)["']""")
        val imagePattern = Regex("""(?is)<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""")
        val pricePattern = Regex("""(\d[\d.,]*\s*₺)""")
        val areaPattern = Regex("""(\d[\d.,]*\s*m²)""", RegexOption.IGNORE_CASE)
        val roomsPattern = Regex("""(\d+\s*\+\s*\d+)""")
        val result = linkedMapOf<String, Portfolio>()

        for (match in anchors.findAll(html)) {
            val code = codePattern.find(match.groupValues[1])?.groupValues?.get(1)?.uppercase() ?: continue
            val start = match.range.last + 1
            val closing = html.indexOf("</a>", start, ignoreCase = true)
            if (closing == -1 || closing - start > 15000) continue
            val card = html.substring(start, closing)
            val cardText = plain(card)
            if (cardText.isBlank()) continue
            val title = titlePattern.find(card)?.groupValues?.get(1)?.let(::plain)?.takeIf { it.isNotBlank() }
                ?: altPattern.find(card)?.groupValues?.get(1)?.let(::plain)?.takeIf { it.isNotBlank() }
                ?: cardText.replaceFirst(pricePattern, "").take(180).trim()
            val img = imagePattern.find(card)?.groupValues?.get(1)?.replace("&amp;", "&").orEmpty()
            val imageUrl = when {
                img.startsWith("https://") -> img
                img.startsWith("//") -> "https:$img"
                img.startsWith("/") -> "https://remax.com.tr$img"
                else -> ""
            }
            val propertyType = listOf("Zeytinlik", "Bağ & Bahçe", "Tarla", "Arsa", "Villa", "Daire",
                "Müstakil Ev", "Fabrika", "Ofis", "Dükkan", "Çiftlik")
                .firstOrNull { cardText.contains(it, ignoreCase = true) }.orEmpty()
            result[code] = Portfolio(
                id = "remax_$code",
                title = title.ifBlank { "RE/MAX Portföy $code" },
                price = pricePattern.find(cardText)?.groupValues?.get(1)?.trim().orEmpty(),
                rooms = roomsPattern.find(cardText)?.groupValues?.get(1)?.replace(" ", "").orEmpty(),
                area = areaPattern.find(cardText)?.groupValues?.get(1)?.trim().orEmpty(),
                location = "",
                type = when {
                    cardText.contains("Kiralık", ignoreCase = true) -> "Kiralık"
                    cardText.contains("Satılık", ignoreCase = true) -> "Satılık"
                    else -> ""
                },
                propertyType = propertyType,
                imageUrl = imageUrl,
                link = "https://remax.com.tr/tr/portfoy/$code",
                createdAt = Clock.now()
            )
        }
        return result.values.toList()
    }

    suspend fun fetchOfficePortfolios(url: String, page: Int = 1): List<Portfolio> {
        return try {
            val pagedUrl = if (url.contains("page=")) {
                url.replace(Regex("page=\\d+"), "page=$page")
            } else {
                url + (if (url.contains("?")) "&" else "?") + "page=$page"
            }

            val encodedTarget = pagedUrl.encodeURLParameter()
            var html = ""
            var requestError = ""
            for (proxy in proxyBaseUrls) {
                try {
                    val response = withTimeoutOrNull(15000L) {
                        client.get(proxy + encodedTarget) {
                            header("Accept", "text/html,application/xhtml+xml")
                        }
                    }
                    if (response == null) {
                        requestError = "RE/MAX bağlantısı zaman aşımına uğradı."
                        continue
                    }
                    if (!response.status.isSuccess()) {
                        requestError = "RE/MAX bağlantı hatası (HTTP " + response.status.value + ")."
                        continue
                    }
                    val body = response.bodyAsText()
                    if (!body.contains("/portfoy/", ignoreCase = true) &&
                        !body.contains("PropertyListingData", ignoreCase = true) &&
                        !body.contains("self.__next_f.push")) {
                        requestError = "RE/MAX sayfası ilan verisi yerine boş/korumalı yanıt döndürdü."
                        continue
                    }
                    html = body
                    break
                } catch (e: Exception) {
                    requestError = "RE/MAX bağlantı hatası: " + e.message.orEmpty().take(120)
                }
            }
            if (html.isEmpty()) {
                lastSyncMessage = requestError.ifBlank { "RE/MAX ilan listesine erişilemedi." }
                return emptyList()
            }
            // Genel arama sayfasının kartları doğrudan HTML içinde bulunur.
            // Uyuşmayan Next.js Flight JSON şeması bu kartları kaybettirmesin.
            if (url.contains("/tr/emlak/", ignoreCase = true)) {
                val visibleCards = extractPortfoliosFromHtml(html)
                if (visibleCards.isNotEmpty()) return visibleCards
            }
            val flight = decodeFlight(html)
            if (flight.isEmpty()) {
                val fallback = extractPortfoliosFromHtml(html)
                if (fallback.isEmpty()) lastSyncMessage = "RE/MAX sayfasında ilan bağlantıları bulunamadı."
                return fallback
            }

            val rows = parseFlightRows(flight)
            var rawJson: String? = null
            for (key in listOf("officeDetailPropertyListingData", "searchPropertyListingData", "propertyListingData", "listingData")) {
                rawJson = extractObjectStringByKey(flight, key)
                if (rawJson != null) break
            }
            if (rawJson == null) {
                val fallback = extractPortfoliosFromHtml(html)
                if (fallback.isEmpty()) lastSyncMessage = "Sayfa okundu ancak ilan verileri ayrıştırılamadı."
                return fallback
            }

            val root = json.parseToJsonElement(rawJson)
            val resolved = resolveRefs(root, rows)

            val listingsArray = when {
                resolved is JsonArray -> resolved
                resolved is JsonObject -> {
                    resolved.jsonObject["data"]?.jsonObject?.get("data")?.jsonArray
                        ?: resolved.jsonObject["data"]?.jsonArray
                        ?: resolved.jsonObject["listings"]?.jsonArray
                        ?: resolved.values.firstOrNull { it is JsonArray }?.jsonArray
                }
                else -> null
            }
            if (listingsArray == null) return extractPortfoliosFromHtml(html)

            val parsed = listingsArray.mapNotNull { element ->
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

                    // KONUM BİLGİSİ ÇEKME (Daha esnek hale getirildi)
                    val city = obj["cityName"]?.jsonPrimitive?.content 
                        ?: obj["city"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                    val district = obj["townName"]?.jsonPrimitive?.content 
                        ?: obj["town"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                        ?: obj["districtName"]?.jsonPrimitive?.content ?: ""
                    val neighborhood = obj["neighborhoodName"]?.jsonPrimitive?.content 
                        ?: obj["neighborhood"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                    
                    var location = listOf(city, district, neighborhood).filter { it.isNotEmpty() }.joinToString(", ")
                    
                    // Eğer yukarıdakiler boşsa alternatif alanları kontrol et
                    if (location.isEmpty()) {
                        location = obj["address"]?.jsonPrimitive?.content 
                            ?: obj["locationText"]?.jsonPrimitive?.content 
                            ?: obj["fullLocation"]?.jsonPrimitive?.content ?: ""
                    }

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

                    var imageUrl = ""
                    for (key in listOf("coverPhoto", "thumbnail", "photoUrl", "photoPath", "squarePhotoUrl", "imageUrl", "image", "mainImage", "primaryPhoto", "src", "url", "path", "fullPath")) {
                        val valStr = try {
                            obj[key]?.jsonPrimitive?.content 
                                ?: obj[key]?.jsonObject?.get("url")?.jsonPrimitive?.content 
                                ?: obj[key]?.jsonObject?.get("path")?.jsonPrimitive?.content 
                                ?: ""
                        } catch (_: Exception) { "" }
                        if (valStr.isNotEmpty()) {
                            imageUrl = valStr
                            break
                        }
                    }

                    if (imageUrl.isEmpty()) {
                        for (arrayKey in listOf("photos", "gallery", "images", "pictures", "media", "photo")) {
                            try {
                                val arr = obj[arrayKey]?.jsonArray
                                val first = arr?.firstOrNull()
                                if (first != null) {
                                    val found = when (first) {
                                        is JsonPrimitive -> first.content
                                        is JsonObject -> first["url"]?.jsonPrimitive?.content ?: first["path"]?.jsonPrimitive?.content ?: first["fullPath"]?.jsonPrimitive?.content ?: first["src"]?.jsonPrimitive?.content ?: ""
                                        else -> ""
                                    }
                                    if (found.isNotEmpty()) {
                                        imageUrl = found
                                        break
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    if (imageUrl.isNotEmpty()) {
                        if (imageUrl.startsWith("//")) {
                            imageUrl = "https:$imageUrl"
                        } else if (imageUrl.startsWith("/")) {
                            imageUrl = "https://www.remax.com.tr$imageUrl"
                        } else if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                            if (imageUrl.contains("remax")) {
                                imageUrl = "https://$imageUrl"
                            } else {
                                imageUrl = "https://img.remax.com.tr/$imageUrl"
                            }
                        }
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
                        propertyType = when(val cat = obj["categoryName"]?.jsonPrimitive?.content ?: "Daire") {
                            "Konut", "Rezidans" -> "Daire"
                            "Ticari", "Bina" -> "İşyeri"
                            else -> cat
                        },
                        imageUrl = imageUrl,
                        link = "https://www.remax.com.tr/tr/portfoy/$code",
                        createdAt = Clock.now()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            if (parsed.isNotEmpty()) parsed else extractPortfoliosFromHtml(html)
        } catch (e: Exception) {
            println("RemaxService: Liste ayrıştırma hatası: " + e.message)
            emptyList()
        }
    }

    suspend fun syncWithFirebase(url: String, dbManager: DatabaseManager, officeName: String): Int {
        if (url.isBlank() || _isSyncing.value) return 0
        val urls = url.split(",", ";", "|", "\n").map { it.trim() }.filter { it.isNotBlank() }
        if (urls.isEmpty()) return 0
        // Şehir genelini içeren /tr/emlak/... araması tek ofise ait değildir.
        // Bu ilanları ortak Firebase /listings havuzuna yazmak diğer danışmanların
        // portföylerini karıştırır. Yalnızca cihazda geçici/kayıtlı liste olarak göster.
        val isGeneralSearch = urls.any { it.contains("/tr/emlak/", ignoreCase = true) ||
            it.substringBefore("?").endsWith("/tr/emlak", ignoreCase = true) }


        var totalAdded = 0
        lastSyncMessage = ""
        lastFetchedPortfolios = emptyList()
        _isSyncing.value = true
        try {
            // Zaman aşımına kadar toplanan ilanlar da korunmalı; bir sayfa
            // yavaşladığı için tüm okunan sonuçlar kaybolmasın.
            val fetchedPortfolios = mutableListOf<Portfolio>()
            val fetchedNormLinks = mutableSetOf<String>()
            withTimeoutOrNull(150000L) {
                for (url in urls) {
                    var currentPage = 1

                    while (currentPage <= 20) {
                        val list = fetchOfficePortfolios(url, currentPage)
                        if (list.isEmpty()) {
                            break
                        }

                        val newItemsInPage = list.filter { p ->
                            val norm = p.link.replace("https://", "").replace("www.", "").removeSuffix("/").lowercase()
                            norm.isNotBlank() && !fetchedNormLinks.contains(norm)
                        }

                        if (newItemsInPage.isEmpty() && currentPage > 1) {
                            break
                        }

                        list.forEach { p ->
                            val norm = p.link.replace("https://", "").replace("www.", "").removeSuffix("/").lowercase()
                            if (norm.isNotBlank() && !fetchedNormLinks.contains(norm)) {
                                fetchedNormLinks.add(norm)
                                fetchedPortfolios.add(p)
                            }
                        }
                        if (list.size < 10) break
                        currentPage++
                    }
                }
            }

            if (fetchedPortfolios.isNotEmpty()) {
                    // Ağda bulunanlar bulut yazma yetkisi olmasa da görüntülenebilsin.
                    // Mevcut veya manuel eklenmiş portföyleri ASLA silme.
                    val sourceOffice = if (isGeneralSearch) {
                        if (urls.any { it.contains("/manisa", ignoreCase = true) }) "RE/MAX Manisa (genel)"
                        else "RE/MAX şehir ilanları"
                    } else officeName
                    lastFetchedPortfolios = fetchedPortfolios.map { it.copy(officeName = sourceOffice) }
                    if (!isGeneralSearch) {
                        var failedInRow = 0
                        for (p in lastFetchedPortfolios) {
                            if (dbManager.addPortfolio(p) != null) {
                                totalAdded++
                                failedInRow = 0
                            } else {
                                failedInRow++
                                // Yetki hatasında yüzlerce başarısız istek gönderme.
                                if (failedInRow >= 3) break
                            }
                        }
                    }
                    lastSyncMessage = when {
                        isGeneralSearch -> lastFetchedPortfolios.size.toString() + " genel ilan sadece EmlakCep'te gösterilecek; hiçbir ofisin bulut verisi değiştirilmedi."
                        totalAdded == lastFetchedPortfolios.size -> "$totalAdded RE/MAX portföyü buluta eklendi/güncellendi."
                        else -> lastFetchedPortfolios.size.toString() + " ilan okundu; $totalAdded kayıt buluta yazıldı. Kalanlar yerel önbellekte korunacak."
                    }
                } else {
                    lastSyncMessage = "RE/MAX ilanları okunamadı; mevcut portföylerin hiçbiri silinmedi."
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
