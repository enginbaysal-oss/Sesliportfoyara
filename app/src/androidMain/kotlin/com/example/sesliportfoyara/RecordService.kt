package com.example.sesliportfoyara

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.content.ContentValues
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

class RecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    private var isStarting = false
    private var videoFilePath: String? = null
    private var startTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START") {
            val resultCode = intent.getIntExtra("RESULT_CODE", -1)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("RESULT_DATA", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("RESULT_DATA")
            }
            
            if (resultData != null) {
                isStarting = true
                startForegroundService()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isStarting) {
                        try {
                            startRecording(resultCode, resultData)
                        } catch (e: Exception) {
                            Log.e("RecordService", "Recording failed to start", e)
                            stopSelf()
                        }
                    }
                    isStarting = false
                }, 1000)
            }
        } else if (action == "STOP") {
            isStarting = false
            stopRecording()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "RecordChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Ekran Kaydı", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ekran Kaydediliyor")
            .setContentText("Arsa Takip 3D uçuş kaydı devam ediyor.")
            .setSmallIcon(R.drawable.ic_media_play)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (isRecording) return

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
            }
        }, Handler(Looper.getMainLooper()))

        val metrics = resources.displayMetrics
        // Çözünürlüğü makul bir seviyede tut (720p)
        var width = metrics.widthPixels
        var height = metrics.heightPixels
        val maxDimension = 1280
        
        if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height
            if (width > height) {
                width = maxDimension
                height = (maxDimension / ratio).toInt()
            } else {
                height = maxDimension
                width = (maxDimension * ratio).toInt()
            }
        }
        
        width = (width / 16) * 16
        height = (height / 16) * 16

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (moviesDir != null && !moviesDir.exists()) moviesDir.mkdirs()
        val videoFile = File(moviesDir, "ArsaDrone_${System.currentTimeMillis()}.mp4")
        videoFilePath = videoFile.absolutePath

        try {
            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(3 * 1024 * 1024)
                setOutputFile(videoFilePath)
                prepare()
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )

            mediaRecorder?.start()
            
            startTime = System.currentTimeMillis()
            isRecording = true
            Log.i("RecordService", "Recording started: $videoFilePath")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Video kaydı başladı", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("RecordService", "Start error", e)
            stopRecording()
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            Log.i("RecordService", "stopRecording called but not recording")
            return
        }
        isRecording = false
        try {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 2000) {
                Thread.sleep(2000 - elapsedTime)
            }
            
            mediaRecorder?.apply {
                try {
                    stop()
                    Log.i("RecordService", "MediaRecorder stopped")
                } catch (e: Exception) {
                    Log.e("RecordService", "MediaRecorder.stop() failed", e)
                }
                release()
            }
            
            virtualDisplay?.release()
            mediaProjection?.stop()
            
            videoFilePath?.let { path ->
                val file = File(path)
                Log.i("RecordService", "Recording finished at: $path, size: ${file.length()}")
                if (file.exists() && file.length() > 0) {
                    saveToMediaStore(file)
                }
            }
        } catch (e: Exception) {
            Log.e("RecordService", "Stop error", e)
        } finally {
            mediaRecorder = null
            virtualDisplay = null
            mediaProjection = null
        }
    }

    private fun saveToMediaStore(file: File) {
        val fileName = "ArsaDrone_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ArsaTakip")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val videoUri = resolver.insert(collection, contentValues)

        videoUri?.let { uri ->
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("video/mp4")) { _, uri ->
                    Handler(Looper.getMainLooper()).post {
                        // Daha belirgin ve güncel bir mesaj
                        Toast.makeText(this, "Video Başarıyla Galeriye Aktarıldı ✅", Toast.LENGTH_LONG).show()
                    }
                }
                
                // Temp dosyayı temizle
                file.delete()
                
            } catch (e: Exception) {
                Log.e("RecordService", "Error saving to MediaStore", e)
            }
        }
    }
}


