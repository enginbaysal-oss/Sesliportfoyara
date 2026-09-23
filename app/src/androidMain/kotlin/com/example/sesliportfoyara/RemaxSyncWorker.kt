package com.example.sesliportfoyara

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RemaxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return try {
            Log.i("RemaxSyncWorker", "BASLADI")

            val remaxUrl = applicationContext
                .getSharedPreferences("ceptemlak_background", Context.MODE_PRIVATE)
                .getString("remax_office_url", "")
                .orEmpty()
                .ifBlank { "https://remax.com.tr/tr/ofis/detay/ilyada-3" }

            if (remaxUrl.isBlank()) {
                Log.w("RemaxSyncWorker", "REMAX URL BOS - SENKRON YAPILMADI")
                return androidx.work.ListenableWorker.Result.success()
            }

            Log.i("RemaxSyncWorker", "REMAX URL BULUNDU")

            val dbManager: DatabaseManager = FirebaseDatabaseManager()
            val remaxService = RemaxService()

            val added = remaxService.syncWithFirebase(
                url = remaxUrl,
                dbManager = dbManager
            )

            Log.i("RemaxSyncWorker", "SENKRON TAMAMLANDI - EKLENEN: $added")
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("RemaxSyncWorker", "HATA: ${e.message}", e)
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}