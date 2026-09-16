package com.example.sesliportfoyara

object MatchingEngine {
    
    fun findMatches(client: Client, portfolios: List<Portfolio>): List<Portfolio> {
        if (client.type == ClientType.SELLER) return emptyList()
        
        return portfolios.filter { portfolio ->
            // 1. İŞLEM TİPİ KONTROLÜ (Satılık/Kiralık)
            if (!portfolio.type.equals(client.dealType, ignoreCase = true)) return@filter false

            // 2. EMLAK TİPİ NORMALİZASYONU VE KONTROLÜ
            val pType = normalizePropertyType(portfolio.propertyType)
            val cType = normalizePropertyType(client.propertyType)
            if (pType != cType) return@filter false

            // 3. KONUM KONTROLÜ (Gelişmiş ve Esnek)
            val pLoc = normalizeForSearch(portfolio.location)
            val cIlce = normalizeForSearch(client.ilce)
            val cMahalle = normalizeForSearch(client.mahalle)
            
            // Eğer ilçe girilmişse, ilçe mutlaka ilanda geçmeli.
            if (cIlce.isNotEmpty() && !pLoc.contains(cIlce)) return@filter false
            
            // Eğer mahalle de girilmişse, ilçe uysa bile mahalle uymuyorsa puan düşer ama elenmez (esneklik için)
            
            // 4. PUANLAMA VE FİLTRELEME
            scoreMatch(client, portfolio) >= 0.15f // Eşiği biraz daha düşürdük
        }.sortedByDescending { scoreMatch(client, it) }
    }
    
    private fun normalizePropertyType(type: String): String {
        return when(val t = type.trim().lowercase()) {
            "konut", "rezidans", "daire", "apartman dairesi" -> "daire"
            "ticari", "işyeri", "isyeri", "dükkan", "ofis", "bina" -> "işyeri"
            "arsa", "tarla", "bağ", "bahçe", "bag", "bahce" -> "arsa"
            "villa", "müstakil", "köşk", "yalı", "mustakil", "kosk", "yali" -> "villa"
            else -> t
        }
    }

    // Türkçe karakterleri ve yazım farklarını normalize eden fonksiyon
    private fun normalizeForSearch(text: String): String {
        return text.trim().lowercase()
            .replace("ç", "c").replace("ğ", "g")
            .replace("ı", "i").replace("i̇", "i").replace("i", "i")
            .replace("ö", "o").replace("ş", "s")
            .replace("ü", "u")
            .replace("center", "merkez")
    }
    
    private fun scoreMatch(client: Client, portfolio: Portfolio): Float {
        var score = 0f
        
        val pLoc = normalizeForSearch(portfolio.location)
        val cIlce = normalizeForSearch(client.ilce)
        val cMahalle = normalizeForSearch(client.mahalle)
        
        // --- KONUM PUANLAMASI ---
        if (cIlce.isNotEmpty() && pLoc.contains(cIlce)) score += 0.5f
        if (cMahalle.isNotEmpty() && pLoc.contains(cMahalle)) score += 0.4f // Mahalle uyumu yüksek puan
        
        // --- ODA SAYISI PUANLAMASI ---
        val cRooms = client.preferredRooms.replace(" ", "").lowercase()
        val pRooms = portfolio.rooms.replace(" ", "").lowercase()
        if (cRooms.isNotEmpty() && pRooms.isNotEmpty()) {
            if (pRooms.contains(cRooms) || cRooms.contains(pRooms)) {
                score += 0.3f
            }
        } else {
            score += 0.1f
        }
        
        // --- FİYAT PUANLAMASI (%20 tolerans payı ile) ---
        if (client.preferredPriceMax.isNotEmpty() && portfolio.price.isNotEmpty()) {
            val maxPrice = client.preferredPriceMax.filter { it.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE
            val portPrice = portfolio.price.filter { it.isDigit() }.toLongOrNull() ?: 0L
            
            // %20 tolerans (pazarlık payı)
            val tolerancePrice = maxPrice * 1.20
            
            if (portPrice <= tolerancePrice && portPrice > 0) {
                score += 0.3f
            }
        } else {
            score += 0.1f
        }
        
        return score
    }
}
