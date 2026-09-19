package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class LocalCRMManager(private val settings: Settings) {
    private val CLIENTS_KEY = "crm_clients_v1"
    private val SEEN_MATCHES_KEY = "crm_seen_matches_v1"
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        encodeDefaults = true
    }
    
    private val _clients = MutableStateFlow<List<Client>>(loadClients())
    val clients: StateFlow<List<Client>> = _clients

    private fun loadClients(): List<Client> {
        val jsonString = settings.getString(CLIENTS_KEY, "[]")
        return try {
            json.decodeFromString<List<Client>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveClients(list: List<Client>) {
        val jsonString = json.encodeToString(list)
        settings.putString(CLIENTS_KEY, jsonString)
        _clients.value = list
    }

    fun addClient(client: Client) {
        val current = _clients.value.toMutableList()
        val newClient = if (client.id.isEmpty()) {
            client.copy(id = Clock.now().toString(), createdAt = Clock.now())
        } else {
            client
        }
        current.add(newClient)
        saveClients(current)
    }

    fun updateClient(client: Client) {
        val current = _clients.value.map {
            if (it.id == client.id) client else it
        }
        saveClients(current)
    }

    fun deleteClient(id: String) {
        val current = _clients.value.filter { it.id != id }
        saveClients(current)
    }

    private fun matchKey(clientId: String, portfolioId: String): String =
        clientId + "|" + portfolioId

    fun getSeenMatches(): Set<String> {
        val raw = settings.getString(SEEN_MATCHES_KEY, "")
        return raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    fun getNewMatches(clients: List<Client>, portfolios: List<Portfolio>): List<Pair<Client, Portfolio>> {
        val seen = getSeenMatches()
        return clients
            .filter { it.type == ClientType.BUYER }
            .flatMap { client ->
                MatchingEngine.findMatches(client, portfolios)
                    .filter { portfolio -> matchKey(client.id, portfolio.id) !in seen }
                    .map { portfolio -> client to portfolio }
            }
    }

    fun markMatchesSeen(matches: List<Pair<Client, Portfolio>>) {
        if (matches.isEmpty()) return
        val updated = getSeenMatches().toMutableSet()
        matches.forEach { (client, portfolio) ->
            updated.add(matchKey(client.id, portfolio.id))
        }
        settings.putString(SEEN_MATCHES_KEY, updated.joinToString("\n"))
    }
    fun exportFullBackup(localPortfolios: List<Portfolio>): String {
        // Müşterileri ve portföyleri içeren bir yapı oluşturuyoruz
        val backupJson = JsonObject(mapOf(
            "customers" to json.encodeToJsonElement(_clients.value),
            "portfolios" to json.encodeToJsonElement(localPortfolios),
            "version" to JsonPrimitive(3),
            "exportedAt" to JsonPrimitive(Clock.now())
        ))
        return json.encodeToString(backupJson)
    }

    fun importFullBackup(jsonContent: String, localPortfolioManager: LocalPortfolioManager): Boolean {
        return try {
            val cleanJson = jsonContent.trim().removePrefix("\uFEFF")
            val jsonElement = json.parseToJsonElement(cleanJson)

            if (jsonElement is JsonArray) {
                // Sadece müşteri listesi (Eski format)
                val importedList = json.decodeFromJsonElement<List<Client>>(jsonElement)
                mergeClients(importedList)
                return true
            } else if (jsonElement is JsonObject) {
                // Müşterileri ayıkla
                val customersArray = jsonElement["customers"]?.jsonArray
                if (customersArray != null) {
                    val mappedClients = parseCustomers(customersArray)
                    mergeClients(mappedClients)
                }
                
                // Portföyleri ayıkla (V3 Formatı)
                val portfoliosArray = jsonElement["portfolios"]?.jsonArray
                if (portfoliosArray != null) {
                    val importedPortfolios = json.decodeFromJsonElement<List<Portfolio>>(portfoliosArray)
                    localPortfolioManager.importLocalPortfolios(importedPortfolios)
                }
                return true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseCustomers(customersArray: JsonArray): List<Client> {
        return customersArray.map { it.jsonObject }.map { obj ->
            val rawType = obj["type"]?.jsonPrimitive?.content ?: "alici"
            val rawDealType = obj["dealType"]?.jsonPrimitive?.content ?: "satilik"
            val rawPropertyType = obj["propertyType"]?.jsonPrimitive?.content ?: "Daire"
            
            Client(
                id = obj["id"]?.jsonPrimitive?.content ?: Clock.now().toString(),
                name = obj["name"]?.jsonPrimitive?.content ?: "İsimsiz Müşteri",
                phone = obj["phone"]?.jsonPrimitive?.content ?: "",
                type = if (rawType == "satici") ClientType.SELLER else ClientType.BUYER,
                dealType = rawDealType.replaceFirstChar { it.uppercase() },
                propertyType = rawPropertyType.replaceFirstChar { it.uppercase() },
                il = obj["il"]?.jsonPrimitive?.content ?: "",
                ilce = obj["ilce"]?.jsonPrimitive?.content ?: "",
                mahalle = obj["mahalle"]?.jsonPrimitive?.content ?: "",
                preferredPriceMax = obj["price"]?.jsonPrimitive?.content ?: "",
                preferredRooms = obj["preferredRooms"]?.jsonPrimitive?.content ?: "", 
                note = obj["notes"]?.jsonPrimitive?.content ?: obj["note"]?.jsonPrimitive?.content ?: "",
                createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: Clock.now()
            )
        }
    }

    private fun mergeClients(newClients: List<Client>) {
        val current = _clients.value.toMutableList()
        newClients.forEach { nc ->
            val index = current.indexOfFirst { it.id == nc.id }
            if (index != -1) {
                current[index] = nc
            } else {
                current.add(nc)
            }
        }
        saveClients(current)
    }
}

val LocalCRMManagerProvider = staticCompositionLocalOf<LocalCRMManager> {
    error("LocalCRMManager not provided")
}
