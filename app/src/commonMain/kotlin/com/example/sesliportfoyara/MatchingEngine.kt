package com.example.sesliportfoyara

object MatchingEngine {
    
    fun findMatches(client: Client, portfolios: List<Portfolio>): List<Portfolio> {
        if (client.type == ClientType.SELLER) return emptyList()
        
        return portfolios.filter { portfolio ->
            // 1. İŞLEM TİPİ KONTROLÜ (Satılık/Kiralık)
            if (!portfolio.type.equals(client.dealType, ignoreCase = true)) return@filter false

            // 2. EMLAK TİPİ NORMALİZASYONU
            val pType = normalizePropertyType(portfolio.propertyType)
            val cType = normalizePropertyType(client.propertyType)
            if (pType != cType) return@filter false

            // 3. KONUM KONTROLÜ (Kesin Mahalle Filtresi)
            val pLoc = normalizeForSearch(portfolio.location)
            val cIlce = normalizeForSearch(client.ilce)
            val cMahalle = normalizeForSearch(client.mahalle)
            
            // İlçe kontrolü: Eğer ilçe girilmişse, ilanda mutlaka geçmeli.
            if (cIlce.isNotEmpty() && !pLoc.contains(cIlce)) return@filter false
            
            // Mahalle kontrolü: Eğer müşteri mahalle belirtmişse (Örn: Muradiye), 
            // ilanda bu mahalle adı geçmiyorsa kesinlikle elensin.
            if (cMahalle.isNotEmpty() && !pLoc.contains(cMahalle)) return@filter false
            
            // 4. PUANLAMA VE FİLTRELEME
            scoreMatch(client, portfolio) >= 0.15f
        }.sortedByDescending { scoreMatch(client, it) }
    }
    
    private fun normalizePropertyType(type: String): String {
        return when(val t = type.trim().lowercase()) {
            "konut", "rezidans", "daire", "apartman dairesi", "apart" -> "daire"
            "ticari", "işyeri", "isyeri", "dükkan", "ofis", "bina", "dukkan" -> "işyeri"
            "arsa", "tarla", "bağ", "bahçe", "bag", "bahce", "zeytinlik" -> "arsa"
            "villa", "müstakil", "köşk", "yalı", "mustakil", "kosk", "yali", "yazlık", "yazlik" -> "villa"
            else -> t
        }
    }

    private fun normalizeForSearch(text: String): String {
        // Türkçe karakterleri daha agresif ve temiz bir şekilde normalize edelim
        return text.trim().lowercase()
            .replace("ç", "c")
            .replace("ğ", "g")
            .replace("ı", "i")
            .replace("i̇", "i") // Özel birleşim karakteri
            .replace("ö", "o")
            .replace("ş", "s")
            .replace("ü", "u")
            .replace("merkez", "") // "Yunusemre Merkez" gibi durumlar için merkez kelimesini görmezden gelelim
            .replace(",", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ") // Fazla boşlukları temizle
            .trim()
    }
    
    private fun scoreMatch(client: Client, portfolio: Portfolio): Float {
        var score = 0f
        
        val pLoc = normalizeForSearch(portfolio.location)
        val cIlce = normalizeForSearch(client.ilce)
        val cMahalle = normalizeForSearch(client.mahalle)
        
        // --- KONUM PUANLAMASI ---
        if (cIlce.isNotEmpty() && pLoc.contains(cIlce)) score += 0.5f
        if (cMahalle.isNotEmpty() && pLoc.contains(cMahalle)) score += 0.4f
        
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
