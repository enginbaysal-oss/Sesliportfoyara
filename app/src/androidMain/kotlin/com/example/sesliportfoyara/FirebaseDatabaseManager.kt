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

    override suspend fun addPortfolio(portfolio: Portfolio) {
        val newRef = db.push()
        val key = newRef.key ?: ""
        db.child(key).setValue(portfolio.copy(id = key))
    }

    override suspend fun updatePortfolio(portfolio: Portfolio) {
        db.child(portfolio.id).setValue(portfolio)
    }

    override suspend fun deletePortfolio(id: String) {
        db.child(id).removeValue()
    }
}
