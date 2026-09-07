package com.example.sesliportfoyara

object MatchingEngine {
    
    fun findMatches(client: Client, portfolios: List<Portfolio>): List<Portfolio> {
        // Sadece alıcılar için portföy eşleşmesi yapıyoruz
        if (client.type == ClientType.SELLER) return emptyList()
        
        return portfolios.filter { portfolio ->
            // 1. KRİTİK FİLTRE: İşlem Tipi (Satılık/Kiralık) mutlaka uymalı
            if (!portfolio.type.equals(client.dealType, ignoreCase = true)) return@filter false

            // 2. KRİTİK FİLTRE: Emlak Tipi mutlaka uymalı
            if (!portfolio.propertyType.equals(client.propertyType, ignoreCase = true)) return@filter false
            
            // 3. PUANLAMA: Diğer kriterlere göre (Konum, Fiyat, Oda) uygunluk puanı
            scoreMatch(client, portfolio) > 0.3f
        }.sortedByDescending { scoreMatch(client, it) }
    }
    
    private fun scoreMatch(client: Client, portfolio: Portfolio): Float {
        var score = 0f
        
        // Konum kontrolü (İlçe veya Mahalle bazlı)
        val clientLocation = "${client.ilce} ${client.mahalle}".trim()
        if (clientLocation.isNotBlank()) {
            if (portfolio.location.contains(client.ilce, ignoreCase = true) || 
                (client.mahalle.isNotBlank() && portfolio.location.contains(client.mahalle, ignoreCase = true))) {
                score += 0.5f
            }
        } else {
            score += 0.2f
        }
        
        // Oda sayısı kontrolü (Daire ve Villa için)
        if (client.preferredRooms.isNotBlank()) {
            if (portfolio.rooms.contains(client.preferredRooms, ignoreCase = true)) {
                score += 0.3f
            }
        } else {
            score += 0.1f
        }
        
        // Fiyat kontrolü
        if (client.preferredPriceMax.isNotBlank() && portfolio.price.isNotEmpty()) {
            val maxPrice = client.preferredPriceMax.filter { it.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE
            val portPrice = portfolio.price.filter { it.isDigit() }.toLongOrNull() ?: 0L
            
            if (portPrice <= maxPrice && portPrice > 0) {
                score += 0.2f
            }
        } else {
            score += 0.1f
        }
        
        return score
    }
}
