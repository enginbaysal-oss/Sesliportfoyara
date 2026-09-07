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
        val uniqueTokens = tokens.toSet()

        val scoredResults = listings.map { l ->
            val fields = listOf(
                l.title to 15,
                l.location to 12, // Konum ağırlığı artırıldı
                l.rooms to 25,
                l.propertyType to 12, // Emlak tipi ağırlığı artırıldı
                l.type to 8,
                l.features.joinToString(" ") to 8,
                l.consultantName to 5,
                l.ownerName to 5,
                l.price to 4,
                l.area to 4
            )
            
            var baseScore = 0
            val matchedTokens = mutableSetOf<String>()
            
            fields.forEach { (fieldValue, weight) ->
                val normField = normalize(fieldValue)
                val collField = collapse(fieldValue)
                
                // 1. TAM EŞLEŞME (Tüm sorgu bir alanda geçiyorsa büyük bonus)
                if (collField.isNotBlank() && (collField.contains(collapsedQuery) || collapsedQuery.contains(collField))) {
                    baseScore += weight * 5
                    matchedTokens.addAll(uniqueTokens) // Tüm kelimeler eşleşmiş sayılır
                }
                
                // 2. KELİME (TOKEN) BAZLI EŞLEŞME
                uniqueTokens.forEach { tok ->
                    val isShortToken = tok.length <= 2
                    
                    val matches = if (isShortToken) {
                        normField == tok || (weight >= 20 && normField.contains(tok))
                    } else {
                        normField.contains(tok) || collField.contains(collapse(tok))
                    }

                    if (matches) {
                        baseScore += weight
                        matchedTokens.add(tok)
                    }
                }
            }
            
            // TOKEN BOOSTING: 
            // Kaç farklı kelimenin eşleştiği çok kritiktir. 
            // "Güzelyurt daire" aramasında her iki kelimeyi de içeren ilanları katlayarak öne çıkar.
            val matchRatio = if (uniqueTokens.isNotEmpty()) matchedTokens.size.toFloat() / uniqueTokens.size else 0f
            
            // Eğer sorguda birden fazla kelime varsa ve sadece biri eşleşiyorsa puanı düşür (Gürültü engelleme)
            val finalScore = if (uniqueTokens.size > 1 && matchedTokens.size < 2) {
                (baseScore * 0.5f).toInt() // Cezalandırma
            } else {
                // Eşleşen token sayısı arttıkça puanı katla (Üssel artış)
                (baseScore * (1f + matchRatio * matchRatio * 2f)).toInt()
            }
            
            l to finalScore
        }
        
        val maxScore = scoredResults.maxOfOrNull { it.second } ?: 0
        
        // EŞİK DEĞERİ (Daha katı):
        // En yüksek puanın %40'ından az olanları ele (Eskiden %30 idi)
        // Mutlak olarak 25 puanın altını ele (Eskiden 20 idi)
        val threshold = (maxScore * 0.4).coerceAtLeast(25.0)
        
        val finalResults = scoredResults
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .map { it.first }
            
        println("📊 Max Skor: $maxScore, Eşik: $threshold, Sonuç: ${finalResults.size}")
        
        finalResults.take(5).forEach { l ->
            println("✅ Sonuç: '${l.title}' - ${l.location} - ${l.propertyType}")
        }
        
        return finalResults
    }
}
