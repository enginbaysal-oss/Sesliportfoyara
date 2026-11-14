package com.example.sesliportfoyara

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class FirebaseDatabaseManager : DatabaseManager {
    private val db = Firebase.database.reference("listings")
    private val announcementDb = Firebase.database.reference("announcements/current")
    private val progressDb = Firebase.database.reference("portfolio_progress")

    // Public RE/MAX listings are separate from the private Firebase office CRM.
    private val publishedClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
    }
    private var publishedCache: List<Portfolio> = emptyList()
    private var publishedFetchedAt = 0L

    private suspend fun readPublishedOfficeListings(): List<Portfolio> {
        val now = System.currentTimeMillis()
        if (publishedCache.isNotEmpty() && now - publishedFetchedAt < 5 * 60 * 1000L) return publishedCache
        return try {
            val url = "https://enginbaysal-oss.github.io/Sesliportfoyara/data/ilyada3-portfoyler.json"
            val response = publishedClient.get("${url}?t=${now / 60000L}")
            if (!response.status.isSuccess()) return publishedCache
            val list: List<Portfolio> = response.body()
            val valid = list.filter { it.id.startsWith("remax_P") &&
                it.officeName.contains("İlyada 3", ignoreCase = true) &&
                it.link.startsWith("https://remax.com.tr/tr/portfoy/")
            }.distinctBy { it.id }
            if (valid.isNotEmpty()) {
                publishedCache = valid
                publishedFetchedAt = now
            }
            publishedCache
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            publishedCache
        }
    }

    override fun getPortfolios(): Flow<List<Portfolio>> = flow {
        while (true) {
            try {
                // Show public listings immediately even if Firebase's stream never connects.
                emit(readPublishedOfficeListings())
                emitAll(db.valueEvents.map { snap ->
                    val remote = snap.children.map { child ->
                        val p = child.value<Portfolio>()
                        p.copy(id = child.key ?: "")
                    }
                    (remote + readPublishedOfficeListings()).distinctBy { it.id }
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("EmlakCep", "Firebase portfoy yetkisi reddedildi; kamuya acik ilanlar kullaniliyor.")
                emit(readPublishedOfficeListings())
                delay(60_000L)
            }
        }
    }

    override suspend fun addPortfolio(portfolio: Portfolio): String? {
        return try {
            if (portfolio.id.startsWith("remax_")) {
                val key = portfolio.id
                db.child(key).setValue(portfolio)
                key
            } else {
                val newRef = db.push()
                val key = newRef.key ?: return null
                db.child(key).setValue(portfolio.copy(id = key))
                key
            }
        } catch (e: Exception) { null }
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

    override suspend fun clearAllPortfolios(): Boolean {
        return try {
            db.removeValue()
            true
        } catch (e: Exception) { false }
    }

    override suspend fun getAnnouncement(): AppAnnouncement? {
        return try {
            announcementDb.valueEvents.first().value<AppAnnouncement>()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveAnnouncement(announcement: AppAnnouncement): Boolean {
        return try {
            announcementDb.setValue(announcement)
            true
        } catch (e: Exception) { false }
    }

    override suspend fun clearAnnouncement(): Boolean {
        return try {
            announcementDb.removeValue()
            true
        } catch (e: Exception) { false }
    }

    override suspend fun getProgressData(portfolioId: String): ProgressData? {
        return try {
            progressDb.child(portfolioId).valueEvents.first().value<ProgressData>()
        } catch (e: Exception) { null }
    }

    override suspend fun saveProgressData(portfolioId: String, data: ProgressData): Boolean {
        return try {
            progressDb.child(portfolioId).setValue(data)
            true
        } catch (e: Exception) { false }
    }
}