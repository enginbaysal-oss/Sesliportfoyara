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
            
            // 3. PUANLAMA
            scoreMatch(client, portfolio) >= 0.2f // Eşiği biraz düşürdük ki daha fazla sonuç yakalansın
        }.sortedByDescending { scoreMatch(client, it) }
    }
    
    private fun normalizePropertyType(type: String): String {
        return when(type.trim().lowercase()) {
            "konut", "rezidans", "daire", "apartman dairesi" -> "daire"
            "ticari", "işyeri", "isyeri", "dükkan", "ofis", "bina" -> "işyeri"
            "arsa", "tarla", "bağ", "bahçe" -> "arsa"
            "villa", "müstakil", "köşk", "yalı" -> "villa"
            else -> type.trim().lowercase()
        }
    }
    
    private fun scoreMatch(client: Client, portfolio: Portfolio): Float {
        var score = 0f
        
        // --- KONUM PUANLAMASI ---
        val pLoc = portfolio.location.lowercase()
        val cIlce = client.ilce.lowercase().trim()
        val cMahalle = client.mahalle.lowercase().trim()
        
        if (cIlce.isNotEmpty() && pLoc.contains(cIlce)) score += 0.5f
        if (cMahalle.isNotEmpty() && pLoc.contains(cMahalle)) score += 0.3f
        
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
        
        // --- FİYAT PUANLAMASI (%15 tolerans payı ile) ---
        if (client.preferredPriceMax.isNotEmpty() && portfolio.price.isNotEmpty()) {
            val maxPrice = client.preferredPriceMax.filter { it.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE
            val portPrice = portfolio.price.filter { it.isDigit() }.toLongOrNull() ?: 0L
            
            // Müşteri bütçesinin %15 üzerine kadar olan ilanları da gösterelim (Pazarlık payı)
            val tolerancePrice = maxPrice * 1.15
            
            if (portPrice <= tolerancePrice && portPrice > 0) {
                score += 0.3f
            }
        } else {
            score += 0.1f
        }
        
        return score
    }
}
