package com.example.sesliportfoyara

object PortfolioSearchEngine {
    private val STOPWORDS = setOf(
        "bana", "olan", "var", "mi", "mı", "mu", "mü", "bul", "goster", "göster",
        "gösterir", "ne", "nedir", "portfoyde", "portföyde", "portfoydeki", "hangi",
        "icin", "için", "ver", "soyle", "söyle", "bir", "tane", "misin", "musun",
        "lütfen", "lutfen", "ara", "arasana", "listele", "de", "da", "ve"
    )

    fun normalize(str: String): String {
        return str.lowercase()
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .trim()
    }

    fun tokenize(str: String): List<String> {
        // Normalizasyon sonrası sadece harf ve rakamları al, boşlukları temizle
        val normalized = normalize(str)
        return normalized.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { it.length > 1 && it !in STOPWORDS }
    }

    fun search(query: String, listings: List<Portfolio>): List<Portfolio> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        
        val tokens = tokenize(query)

        return listings.map { l ->
            val content = normalize(
                listOf(
                    l.title,
                    l.location,
                    l.consultantName,
                    l.consultantPhone,
                    l.type,
                    l.rooms,
                    l.area,
                    l.price,
                    l.features.joinToString(" ")
                ).joinToString(" ")
            )
            
            var score = 0
            // Tam eşleşme varsa yüksek puan
            if (content.contains(normalizedQuery)) {
                score += 20
            }
            
            // Kelime bazlı eşleşme
            tokens.forEach { tok ->
                if (content.contains(tok)) score += 5
            }
            
            l to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
    }
}
