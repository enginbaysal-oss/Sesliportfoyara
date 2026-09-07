package com.example.sesliportfoyara

object PortfolioSearchEngine {
    private val STOPWORDS = setOf(
        "bana", "olan", "var", "mi", "mı", "mu", "mü", "bul", "goster", "göster",
        "gösterir", "ne", "nedir", "portfoyde", "portföyde", "portfoydeki", "hangi",
        "icin", "için", "ver", "soyle", "söyle", "bir", "tane", "misin", "musun",
        "lütfen", "lutfen", "ara", "arasana", "listele", "de", "da", "ve"
    )

    private val NUMBER_MAP = mapOf(
        "sifir" to "0", "bir" to "1", "iki" to "2", "uc" to "3", "dort" to "4",
        "bes" to "5", "alti" to "6", "yedi" to "7", "sekiz" to "8", "dokuz" to "9",
        "on" to "10"
    )

    fun normalize(str: String): String {
        var n = str.lowercase()
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace("arti", "+")
            .trim()
            
        NUMBER_MAP.forEach { (word, digit) ->
            n = n.replace(Regex("\\b$word\\b"), digit)
        }
        
        return n
    }

    // Boşlukları ve özel karakterleri atarak karşılaştırma yapmak için
    private fun collapse(str: String): String {
        return normalize(str).filter { it.isLetterOrDigit() || it == '+' }
    }

    fun tokenize(str: String): List<String> {
        val normalized = normalize(str)
        // Boşlukları ve virgülleri temizle ama '+' karakterini koru (oda sayısı için)
        return normalized.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in STOPWORDS }
    }

    fun search(query: String, listings: List<Portfolio>): List<Portfolio> {
        val normalizedQuery = normalize(query)
        val collapsedQuery = collapse(query)
        
        println("🔍 Arama Başlatıldı: '$query'")
        
        if (normalizedQuery.isEmpty()) return emptyList()
        
        val tokens = tokenize(query)

        val scoredResults = listings.map { l ->
            val fields = listOf(
                l.title to 15,
                l.location to 10,
                l.rooms to 25, // Oda sayısı en kritik alan
                l.propertyType to 10,
                l.type to 8,
                l.features.joinToString(" ") to 8,
                l.consultantName to 5,
                l.ownerName to 5,
                l.price to 4,
                l.area to 4
            )
            
            var score = 0
            
            fields.forEach { (fieldValue, weight) ->
                val normField = normalize(fieldValue)
                val collField = collapse(fieldValue)
                
                // 1. TAM EŞLEŞME (Daraltılmış)
                if (collField.isNotBlank() && (collField.contains(collapsedQuery) || collapsedQuery.contains(collField))) {
                    score += weight * 4
                }
                
                // 2. KELİME BAZLI EŞLEŞME
                tokens.forEach { tok ->
                    if (tok.isEmpty()) return@forEach
                    
                    // Çok kısa kelimeler (örn: "1", "6") sadece oda sayısı veya başlıkta tam eşleşirse puan alsın
                    // Telefon numarası veya fiyatın içindeki "1"i yakalayıp gürültü yapmasın
                    val isShortToken = tok.length == 1
                    
                    if (isShortToken) {
                        // Kısa tokenlar sadece tam eşleşme veya kritik alanlarda puan alır
                        if (normField == tok || (weight >= 15 && normField.contains(tok))) {
                            score += weight
                        }
                    } else {
                        if (normField.contains(tok)) {
                            score += weight
                        } else if (collField.contains(collapse(tok))) {
                            score += weight
                        }
                    }
                }
            }
            
            l to score
        }
        
        val maxScore = scoredResults.maxOfOrNull { it.second } ?: 0
        
        // EŞİK DEĞERİ (THRESHOLD): 
        // 1. En yüksek puanın %30'undan az olanları ele.
        // 2. Mutlak olarak 20 puanın altında kalan zayıf eşleşmeleri ele.
        val threshold = (maxScore * 0.3).coerceAtLeast(20.0)
        
        val finalResults = scoredResults
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .map { it.first }
            
        println("📊 Max Skor: $maxScore, Eşik: $threshold, Sonuç: ${finalResults.size}")
        
        // Debug için en iyi sonuçları konsola yazdır
        finalResults.take(3).forEach { l ->
            println("✅ Eşleşme: '${l.title}' - ${l.rooms} - ${l.price}")
        }
        
        return finalResults
    }
}
