package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class AppAnnouncement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val active: Boolean = false,
    val createdAt: Long = 0L
)

interface DatabaseManager {
    fun getPortfolios(): Flow<List<Portfolio>>
    suspend fun addPortfolio(portfolio: Portfolio): String? // Başarılıysa ID döner, değilse null
    suspend fun updatePortfolio(portfolio: Portfolio): Boolean
    suspend fun deletePortfolio(id: String): Boolean
    suspend fun clearAllPortfolios(): Boolean
    suspend fun getAnnouncement(): AppAnnouncement?
    suspend fun saveAnnouncement(announcement: AppAnnouncement): Boolean
    suspend fun clearAnnouncement(): Boolean
}

// Bunu App() içine dışarıdan vereceğiz
val LocalDatabaseManager = staticCompositionLocalOf<DatabaseManager> {
    error("DatabaseManager not provided")
}
