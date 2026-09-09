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
                val timestamp = getCurrentTimeMillis()
                val response = client.get("$baseUrl.json?t=$timestamp")
                
                if (response.status.isSuccess()) {
                    val responseBody: Map<String, Portfolio>? = response.body()
                    val list = responseBody?.map { (key, portfolio) ->
                        portfolio.copy(id = key)
                    } ?: emptyList()
                    emit(list)
                } else if (response.status == HttpStatusCode.Unauthorized) {
                    println("❌ Firebase Yetki Hatası (401): Lütfen veri tabanı kurallarını kontrol edin.")
                } else {
                    println("❌ Sunucu Hatası (${response.status.value})")
                }
            } catch (e: Exception) {
                println("❌ Veri çekme hatası: ${e.message}")
                emit(emptyList()) 
            }
            delay(10000) 
        }
    }

    override suspend fun addPortfolio(portfolio: Portfolio): String? {
        return try {
            println("📤 Yeni portföy gönderiliyor: ${portfolio.title}")
            val response = client.post("$baseUrl.json") {
                contentType(ContentType.Application.Json)
                setBody(portfolio)
            }
            if (response.status.isSuccess()) {
                println("✅ Portföy başarıyla eklendi.")
                val body: Map<String, String> = response.body()
                body["name"] // Firebase REST API post sonucu oluşturulan ID'yi 'name' içinde döner
            } else {
                println("⚠️ Portföy ekleme başarısız: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("❌ Ekleme hatası: ${e.message}")
            null
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
