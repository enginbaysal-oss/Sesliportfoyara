package com.example.sesliportfoyara

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class FirebaseDatabaseManager : DatabaseManager {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
                encodeDefaults = true // Varsayılan değerleri (Daire vb.) Firebase'e zorunlu gönder
            })
        }
    }

    private val baseUrl = "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app/listings"

    override fun getPortfolios(): Flow<List<Portfolio>> = flow {
        while (true) {
            try {
                // Cache-busting: 't' parametresi ile tarayıcının eski veriyi getirmesini engelliyoruz
                val timestamp = getCurrentTimeMillis()
                val response: Map<String, Portfolio>? = client.get("$baseUrl.json?t=$timestamp").body()
                val list = response?.map { (key, portfolio) ->
                    portfolio.copy(id = key)
                } ?: emptyList()
                emit(list)
            } catch (e: Exception) {
                println("❌ Veri çekme hatası: ${e.message}")
                // Hata durumunda boş liste emit etmiyoruz, böylece ekran sıfırlanmıyor (mevcut veriler kalıyor)
            }
            delay(10000) // Polling interval 10 saniyeye çıkarıldı (performans için)
        }
    }

    override suspend fun addPortfolio(portfolio: Portfolio): Boolean {
        return try {
            println("📤 Yeni portföy gönderiliyor: ${portfolio.title}")
            val response = client.post("$baseUrl.json") {
                contentType(ContentType.Application.Json)
                setBody(portfolio)
            }
            if (response.status.isSuccess()) {
                println("✅ Portföy başarıyla eklendi.")
                true
            } else {
                println("⚠️ Portföy ekleme başarısız: ${response.status}")
                false
            }
        } catch (e: Exception) {
            println("❌ Ekleme hatası: ${e.message}")
            false
        }
    }

    override suspend fun updatePortfolio(portfolio: Portfolio): Boolean {
        return try {
            println("🔄 Portföy güncelleniyor: ${portfolio.id} - ${portfolio.title} (${portfolio.propertyType})")
            val response = client.put("$baseUrl/${portfolio.id}.json") {
                contentType(ContentType.Application.Json)
                setBody(portfolio)
            }
            if (response.status.isSuccess()) {
                println("✅ Portföy başarıyla güncellendi (Tip: ${portfolio.propertyType}, Fiyat: ${portfolio.price})")
                true
            } else {
                println("⚠️ Güncelleme başarısız: ${response.status}")
                false
            }
        } catch (e: Exception) {
            println("❌ Güncelleme hatası: ${e.message}")
            false
        }
    }

    override suspend fun deletePortfolio(id: String): Boolean {
        return try {
            println("🗑️ Portföy siliniyor: $id")
            val response = client.delete("$baseUrl/$id.json")
            if (response.status.isSuccess()) {
                println("✅ Silme başarılı.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("❌ Silme hatası: ${e.message}")
            false
        }
    }
}
