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
            })
        }
    }

    private val baseUrl = "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app/listings"

    override fun getPortfolios(): Flow<List<Portfolio>> = flow {
        while (true) {
            try {
                val response: Map<String, Portfolio>? = client.get("$baseUrl.json").body()
                val list = response?.map { (key, portfolio) ->
                    portfolio.copy(id = key)
                } ?: emptyList()
                emit(list)
            } catch (e: Exception) {
                emit(emptyList())
            }
            delay(5000) 
        }
    }

    override suspend fun addPortfolio(portfolio: Portfolio) {
        try {
            client.post("$baseUrl.json") {
                contentType(ContentType.Application.Json)
                setBody(portfolio)
            }
        } catch (e: Exception) {}
    }

    override suspend fun updatePortfolio(portfolio: Portfolio) {
        try {
            client.put("$baseUrl/${portfolio.id}.json") {
                contentType(ContentType.Application.Json)
                setBody(portfolio)
            }
        } catch (e: Exception) {}
    }

    override suspend fun deletePortfolio(id: String) {
        try {
            client.delete("$baseUrl/$id.json")
        } catch (e: Exception) {}
    }
}
