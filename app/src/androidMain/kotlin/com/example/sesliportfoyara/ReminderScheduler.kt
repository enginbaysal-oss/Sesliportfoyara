package com.example.sesliportfoyara

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun schedule(
        context: Context,
        dateTime: String,
        note: String
    ): Boolean {
        return try {
            val formatter = SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale("tr", "TR")
            ).apply {
                isLenient = false
            }

            val target = formatter.parse(dateTime) ?: return false
            val delay = target.time - System.currentTimeMillis()

            if (delay <= 0L) return false

            val data = Data.Builder()
                .putString("note", note)
                .build()

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager
                .getInstance(context)
                .enqueue(request)

            true
        } catch (_: Exception) {
            false
        }
    }
}
