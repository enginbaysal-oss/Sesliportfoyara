package com.example.sesliportfoyara

object PortfolioSearchEngine {
    private val STOPWORDS = setOf(
        "bana", "olan", "var", "mi", "mı", "mu", "mü", "bul", "goster", "göster",
        "gösterir", "ne", "nedir", "portfoyde", "portföyde", "portfoydeki", "hangi",
        "icin", "için", "ver", "soyle", "söyle", "bir", "tane", "misin", "musun",
        "lütfen", "lutfen", "ara", "arasana", "listele", "de", "da", "ve"
    )

    // Emlak Tipleri (Kategori Grubu)
    private val CATEGORIES = mapOf(
        "daire" to setOf("daire", "konut", "rezidans", "apartman"),
        "arsa" to setOf("arsa"),
        "tarla" to setOf("tarla", "bag", "bahce", "zeytinlik"),
        "villa" to setOf("villa", "kosk", "malikan"),
        "isyeri" to setOf("isyeri", "ofis", "dukk"),
        "zeytinlik" to setOf("zeytinlik", "tarla")
    )
    
    private val ALL_CAT_WORDS = CATEGORIES.values.flatten().toSet()

    private val COMMON_WORDS = setOf(
        "satilik", "kiralik", "bahceli", "mustakil", "asansorli", "asansorlu", "site", 
        "icerisinde", "ici", "kat", "katta", "oda", "odali", "m2", "metrekare", "fiyat"
    )

    fun normalize(str: String): String {
        return str.lowercase()
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace("arti", "+").trim()
    }

    private fun collapse(str: String): String {
        return normalize(str).filter { it.isLetterOrDigit() || it == '+' }
    }

    fun tokenize(str: String): List<String> {
        val normalized = normalize(str)
        return normalized.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in STOPWORDS }
    }

    fun search(query: String, listings: List<Portfolio>): List<Portfolio> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        
        val uniqueTokens = tokens.toSet()
        val collapsedQuery = collapse(query)

        // 1. Kelimeleri Gruplandır
        val requestedCategories = uniqueTokens.filter { it in ALL_CAT_WORDS }
        val rareTokens = uniqueTokens.filter { it !in COMMON_WORDS && it !in ALL_CAT_WORDS && it.length > 1 }

        val scoredResults = listings.map { l ->
            val titleNorm = normalize(l.title)
            val locNorm = normalize(l.location)
            val typeNorm = normalize(l.propertyType)
            val opNorm = normalize(l.type)
            val fullText = "$titleNorm $locNorm $typeNorm $opNorm ${l.features.joinToString(" ")}"
            val collapsedText = collapse(fullText)
            
            val matchedTokens = uniqueTokens.filter { tok -> 
                if (tok.length <= 2) fullText.split(" ").contains(tok)
                else fullText.contains(tok) || collapsedText.contains(collapse(tok))
            }.toSet()

            // MANTIKSAL KONTROLLER
            
            // A) Kategori Kontrolü (Zorunlu)
            // Eğer kullanıcı "daire" dediyse, ilan daire grubunda olmalı
            val categoryMatch = if (requestedCategories.isEmpty()) true else {
                requestedCategories.any { cat ->
                    // Kategori anahtar kelimesi type veya title içinde geçmeli
                    typeNorm.contains(cat) || titleNorm.contains(cat)
                }
            }

            // B) Lokasyon Kontrolü (Zorunlu)
            // Eğer kullanıcı "Karaali" dediyse, ilan lokasyonunda mutlaka geçmeli
            val rareMatch = if (rareTokens.isEmpty()) true else {
                rareTokens.any { rare -> locNorm.contains(rare) || titleNorm.contains(rare) }
            }

            // PUANLAMA
            var score = matchedTokens.size * 20
            if (collapsedText.contains(collapsedQuery)) score += 200 // Tam eşleşme (en büyük bonus)
            if (rareTokens.any { it in locNorm }) score += 100 // Lokasyon eşleşmesi bonusu
            
            // Eğer kategori tam tutuyorsa ekstra puan
            if (requestedCategories.any { typeNorm.contains(it) }) score += 50

            object {
                val p = l
                val isValid = categoryMatch && rareMatch
                val matchCount = matchedTokens.size
                val finalScore = score
            }
        }.filter { it.isValid && it.matchCount > 0 }

        if (scoredResults.isEmpty()) return emptyList()

        // En iyi eşleşmeyi bul
        val maxMatches = scoredResults.maxOf { it.matchCount }
        
        // Sadece en iyi eşleşenleri veya bir altını getir (Gürültüyü tamamen silmek için)
        val filtered = scoredResults.filter { 
            if (uniqueTokens.size >= 3) it.matchCount >= (maxMatches).coerceAtLeast(2)
            else it.matchCount >= maxMatches
        }

        val maxScore = filtered.maxOfOrNull { it.finalScore } ?: 0
        val threshold = maxScore * 0.8

        return filtered
            .filter { it.finalScore >= threshold }
            .sortedByDescending { it.finalScore }
            .map { it.p }
    }
}
