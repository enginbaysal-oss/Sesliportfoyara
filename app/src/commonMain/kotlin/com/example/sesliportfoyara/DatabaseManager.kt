package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow

interface DatabaseManager {
    fun getPortfolios(): Flow<List<Portfolio>>
    suspend fun addPortfolio(portfolio: Portfolio)
    suspend fun updatePortfolio(portfolio: Portfolio)
    suspend fun deletePortfolio(id: String)
}

// Bunu App() içine dışarıdan vereceğiz
val LocalDatabaseManager = staticCompositionLocalOf<DatabaseManager> {
    error("DatabaseManager not provided")
}
