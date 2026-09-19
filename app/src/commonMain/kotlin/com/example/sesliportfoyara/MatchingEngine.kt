package com.example.sesliportfoyara

object MatchingEngine {

    fun findMatches(client: Client, portfolios: List<Portfolio>): List<Portfolio> {
        if (client.type == ClientType.SELLER) return emptyList()

        return portfolios.filter { portfolio ->

            // 1. ISLEM TIPI - Turkce karakter/yazim farklarini tolere et
            val pDeal = normalizeForSearch(portfolio.type)
            val cDeal = normalizeForSearch(client.dealType)
            if (pDeal != cDeal) return@filter false

            // 2. EMLAK TIPI
            val pType = normalizePropertyType(portfolio.propertyType)
            val cType = normalizePropertyType(client.propertyType)
            if (pType != cType) return@filter false

            // 3. KONUM
            val pLoc = normalizeForSearch(portfolio.location)
            val cIl = normalizeForSearch(client.il)
            val cIlce = normalizeForSearch(client.ilce)
            val cMahalle = normalizeForSearch(client.mahalle)

            if (cIl.isNotEmpty() && !pLoc.contains(cIl)) return@filter false
            if (cIlce.isNotEmpty() && !pLoc.contains(cIlce)) return@filter false
            if (cMahalle.isNotEmpty() && !pLoc.contains(cMahalle)) return@filter false

            // 4. PUAN
            scoreMatch(client, portfolio) >= 0.15f

        }.sortedByDescending { scoreMatch(client, it) }
    }

    private fun normalizePropertyType(type: String): String {
        val t = normalizeForSearch(type)

        return when (t) {
            "konut", "rezidans", "daire", "apartman dairesi", "apart" -> "daire"

            "ticari", "isyeri", "dukkan", "ofis", "bina" -> "isyeri"

            "arsa", "imarlı arsa", "imarli arsa" -> "arsa"

            "tarla", "bag", "bahce", "zeytinlik" -> "tarla"

            "villa", "mustakil", "mustakil ev", "kosk", "yali", "yazlik" -> "villa"

            else -> t
        }
    }

    private fun normalizeForSearch(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace("ç", "c")
            .replace("ğ", "g")
            .replace("ı", "i")
            .replace("i̇", "i")
            .replace("ö", "o")
            .replace("ş", "s")
            .replace("ü", "u")
            .replace(Regex("\\bmahallesi\\b"), "")
            .replace(Regex("\\bmahalle\\b"), "")
            .replace(Regex("\\bmah\\.?\\b"), "")
            .replace(Regex("\\bmerkez\\b"), "")
            .replace(",", " ")
            .replace(".", " ")
            .replace("/", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scoreMatch(client: Client, portfolio: Portfolio): Float {
        var score = 0f

        val pLoc = normalizeForSearch(portfolio.location)
        val cIl = normalizeForSearch(client.il)
        val cIlce = normalizeForSearch(client.ilce)
        val cMahalle = normalizeForSearch(client.mahalle)

        if (cIl.isNotEmpty() && pLoc.contains(cIl)) score += 0.2f
        if (cIlce.isNotEmpty() && pLoc.contains(cIlce)) score += 0.5f
        if (cMahalle.isNotEmpty() && pLoc.contains(cMahalle)) score += 0.4f

        val cRooms = normalizeForSearch(client.preferredRooms).replace(" ", "")
        val pRooms = normalizeForSearch(portfolio.rooms).replace(" ", "")

        if (cRooms.isNotEmpty() && pRooms.isNotEmpty()) {
            if (pRooms.contains(cRooms) || cRooms.contains(pRooms)) {
                score += 0.3f
            }
        } else {
            score += 0.1f
        }

        if (client.preferredPriceMax.isNotEmpty() && portfolio.price.isNotEmpty()) {
            val maxPrice = client.preferredPriceMax
                .filter { it.isDigit() }
                .toLongOrNull()

            val portPrice = portfolio.price
                .filter { it.isDigit() }
                .toLongOrNull()

            // "yok" gibi metinler butce siniri sayilmaz
            if (maxPrice == null || maxPrice == 0L) {
                score += 0.1f
            } else if (portPrice != null && portPrice > 0L) {
                val tolerancePrice = maxPrice * 1.20
                if (portPrice <= tolerancePrice) {
                    score += 0.3f
                }
            }
        } else {
            score += 0.1f
        }

        return score
    }
}