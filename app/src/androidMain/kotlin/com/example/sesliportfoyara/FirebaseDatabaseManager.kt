package com.example.sesliportfoyara

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseDatabaseManager : DatabaseManager {
    private val db = Firebase.database.reference("listings")

    override fun getPortfolios(): Flow<List<Portfolio>> {
        return db.valueEvents.map { snap ->
            snap.children.map { child ->
                val p = child.value<Portfolio>()
                p.copy(id = child.key ?: "")
            }
        }
    }

    override suspend fun addPortfolio(portfolio: Portfolio): Boolean {
        return try {
            val newRef = db.push()
            val key = newRef.key ?: return false
            db.child(key).setValue(portfolio.copy(id = key))
            true
        } catch (e: Exception) { false }
    }

    override suspend fun updatePortfolio(portfolio: Portfolio): Boolean {
        return try {
            db.child(portfolio.id).setValue(portfolio)
            true
        } catch (e: Exception) { false }
    }

    override suspend fun deletePortfolio(id: String): Boolean {
        return try {
            db.child(id).removeValue()
            true
        } catch (e: Exception) { false }
    }
}
