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
        return normalized.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { it.length > 0 && it !in STOPWORDS }
    }

    fun search(query: String, listings: List<Portfolio>): List<Portfolio> {
        val normalizedQuery = normalize(query)
        val collapsedQuery = collapse(query)
        
        println("🔍 Arama Başlatıldı: '$query'")
        println("🔍 Normalizasyon: '$normalizedQuery'")
        println("🔍 Daraltılmış Sorgu: '$collapsedQuery'")
        
        if (normalizedQuery.isEmpty()) return emptyList()
        
        val tokens = tokenize(query)

        val scoredResults = listings.map { l ->
            val fields = listOf(
                l.title to 15, // Puan artırıldı
                l.location to 10,
                l.rooms to 20, // Oda sayısı aramada en yüksek puanı alır
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
                
                // 1. TAM EŞLEŞME (Daraltılmış) - Boşluk farklarını tolere eder (Örn: "3 + 1" vs "3+1")
                if (collField.contains(collapsedQuery) || collapsedQuery.contains(collField)) {
                    score += weight * 3
                }
                
                // 2. KELİME BAZLI EŞLEŞME
                tokens.forEach { tok ->
                    if (normField.contains(tok)) {
                        score += weight
                    } else if (collField.contains(collapse(tok))) {
                        score += weight
                    }
                }
            }
            
            if (score > 0) {
                println("✅ Eşleşme: '${l.title}'")
                println("   └─ ID: ${l.id}")
                println("   └─ Tip: ${l.propertyType}, Fiyat: ${l.price}")
                println("   └─ Puan: $score")
            }
            
            l to score
        }
        
        val finalResults = scoredResults
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            
        println("📊 Toplam Sonuç: ${finalResults.size}")
        return finalResults
    }
}
