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
            
        // Sayı kelimelerini rakama çevir (Örn: "uc + bir" -> "3 + 1")
        NUMBER_MAP.forEach { (word, digit) ->
            n = n.replace(Regex("\\b$word\\b"), digit)
        }
        
        return n
    }

    fun tokenize(str: String): List<String> {
        val normalized = normalize(str)
        return normalized.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { it.length > 0 && it !in STOPWORDS }
    }

    fun search(query: String, listings: List<Portfolio>): List<Portfolio> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        
        val tokens = tokenize(query)

        return listings.map { l ->
            // Tüm alanları içeren genişletilmiş içerik indeksi
            val fields = listOf(
                l.title to 10,
                l.location to 8,
                l.rooms to 12, // Oda sayısı aramada kritiktir
                l.propertyType to 8,
                l.type to 5, // Satılık/Kiralık
                l.features.joinToString(" ") to 5,
                l.consultantName to 4,
                l.ownerName to 4,
                l.price to 3,
                l.area to 3,
                l.consultantPhone to 2,
                l.ownerPhone to 2
            )
            
            var score = 0
            
            fields.forEach { (fieldValue, weight) ->
                val normField = normalize(fieldValue)
                
                // Tam eşleşme (Alan bazlı)
                if (normField.contains(normalizedQuery) || normalizedQuery.contains(normField)) {
                    score += weight * 2
                }
                
                // Kelime bazlı eşleşme
                tokens.forEach { tok ->
                    if (normField.contains(tok)) {
                        score += weight
                    }
                }
            }
            
            l to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
    }
}
