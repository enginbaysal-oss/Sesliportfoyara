package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalPortfolioManager(private val settings: Settings) {
    private val PORTFOLIOS_KEY = "local_portfolios_v2" // Anahtarı güncelledik (V2)
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
        coerceInputValues = true
    }
    
    private val _portfolios = MutableStateFlow<List<Portfolio>>(loadPortfolios())
    val portfolios: StateFlow<List<Portfolio>> = _portfolios

    private fun loadPortfolios(): List<Portfolio> {
        // Not: Desktop tarafında V2 ile dosya sistemine geçişi desteklemek için
        // settings içindeki veriyi alıyoruz. Eğer çok büyükse settings hata verebilir,
        // bu yüzden ilerde tamamen dosya yoluna (File) geçişi main.kt üzerinden yöneteceğiz.
        val jsonString = settings.getString(PORTFOLIOS_KEY, "[]")
        return try {
            json.decodeFromString<List<Portfolio>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun savePortfolios(list: List<Portfolio>) {
        try {
            val jsonString = json.encodeToString(list)
            // Sınırı aşmamak için güvenli kayıt
            settings.putString(PORTFOLIOS_KEY, jsonString)
            _portfolios.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            // Hata durumunda en azından arayüzü güncelleyelim
            _portfolios.value = list
        }
    }

    fun addPortfolio(portfolio: Portfolio) {
        val current = _portfolios.value.toMutableList()
        val newP = if (portfolio.id.isEmpty() || !portfolio.id.startsWith("local_")) {
            portfolio.copy(id = "local_" + Clock.now().toString(), createdAt = Clock.now())
        } else {
            portfolio
        }
        current.add(0, newP) // En başa ekle
        savePortfolios(current)
    }

    fun updatePortfolio(portfolio: Portfolio) {
        val current = _portfolios.value.map {
            if (it.id == portfolio.id) portfolio else it
        }
        savePortfolios(current)
    }

    fun deletePortfolio(id: String) {
        val current = _portfolios.value.filter { it.id != id }
        savePortfolios(current)
    }

    fun getAllPortfolios(): List<Portfolio> = _portfolios.value

    fun importLocalPortfolios(list: List<Portfolio>) {
        val current = _portfolios.value.toMutableList()
        list.forEach { np ->
            val index = current.indexOfFirst { it.id == np.id }
            if (index != -1) current[index] = np else current.add(np)
        }
        savePortfolios(current)
    }
}

val LocalPortfolioManagerProvider = staticCompositionLocalOf<LocalPortfolioManager> {
    error("LocalPortfolioManager not provided")
}
