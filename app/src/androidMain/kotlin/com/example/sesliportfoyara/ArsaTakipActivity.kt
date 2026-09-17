package com.example.sesliportfoyara

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.*
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.*
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.zip.ZipInputStream
import java.io.InputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class ArsaTakipActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var menuLayout: LinearLayout
    private lateinit var rootLayout: RelativeLayout
    private lateinit var backToMenuBtn: Button
    private lateinit var recordBtn: Button
    private var isRecording = false
    private lateinit var projectionManager: MediaProjectionManager
    private var pendingGeoJson: String? = null
    private val parcelList = mutableListOf<String>()

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == -1L) return
            
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                
                if (statusIdx != -1) {
                    val status = cursor.getInt(statusIdx)
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) else "Dosya"
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uriString = if (uriIdx != -1) cursor.getString(uriIdx) else null
                        Log.d("ArsaTakip", "Download completed: $title")
                        runOnUiThread { Toast.makeText(this@ArsaTakipActivity, "$title İndirildi!", Toast.LENGTH_SHORT).show() }
                        if (uriString != null) {
                            processDownloadedFile(Uri.parse(uriString), title)
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        val reason = if (reasonIdx != -1) cursor.getInt(reasonIdx) else -1
                        Log.e("ArsaTakip", "Download failed: $reason")
                        runOnUiThread { Toast.makeText(this@ArsaTakipActivity, "İndirme Hatası! Kod: $reason", Toast.LENGTH_LONG).show() }
                    }
                }
            }
            cursor.close()
        }
    }

    private fun processDownloadedFile(uri: Uri, fileName: String) {
        try {
            val content: String? = when {
                fileName.endsWith(".kmz", true) -> readKmzContent(uri)
                fileName.endsWith(".kml", true) -> contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                else -> contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }

            if (content != null && (content.contains("type") || content.contains("<kml") || content.contains("<Placemark"))) {
                parcelList.add(content)
                pendingGeoJson = content
                runOnUiThread {
                    Toast.makeText(this, "Parsel Verisi Alındı, Harita Güncelleniyor...", Toast.LENGTH_SHORT).show()
                    if (webView.visibility == View.VISIBLE && webView.url?.contains("index.html") == true) {
                        injectGeoJsonToWebView()
                    } else {
                        showWebView("file:///android_asset/arsatakip/index.html")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ArsaTakip", "File read error: ${e.message}", e)
        }
    }

    private fun readKmzContent(uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.let { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".kml", true)) {
                        return zis.reader().readText()
                    }
                    entry = zis.nextEntry
                }
                null
            }
        }
    }
    
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            fileChooserCallback?.onReceiveValue(uris)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    private val recordLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, RecordService::class.java).apply {
                action = "START"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isRecording = true
            updateRecordButtonUI()
            Toast.makeText(this, "Kayıt Başladı", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Kayıt İzni Reddedildi", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (!notificationsGranted) {
            Toast.makeText(this, "Bildirim izni verilmedi, kayıt sırasında sorun oluşabilir.", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setupUI()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            webView.clearCache(true)
            setContentView(rootLayout)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Log.e("ArsaTakip", "Init error", e)
            finish()
        }
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.visibility == View.VISIBLE) {
                    if (webView.canGoBack()) webView.goBack() else hideWebView()
                } else finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {}
    }

    private fun setupUI() {
        rootLayout = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "ARSA TAKİP 3D\nv3.2 - Sade Sürüm"
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#006064"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }
        menuLayout.addView(title)

        menuLayout.addView(createMenuButton("TKGM PARSEL SORGU", "#007BFF") { showWebView("https://parselsorgu.tkgm.gov.tr/") })
        menuLayout.addView(createMenuButton("3D DRONE & HARİTA", "#28A745") { showWebView("file:///android_asset/arsatakip/index.html") })
        menuLayout.addView(createMenuButton("KAYITLARIMI İZLE", "#6C757D") { showLastVideo() })
        menuLayout.addView(createMenuButton("WHATSAPP İLE GÖNDER", "#25D366") { shareLastVideoOnWhatsApp() })
        menuLayout.addView(createMenuButton("← ARAÇLAR MENÜSÜNE DÖN", "#DC3545") { finish() })

        webView = WebView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        setupWebView()

        backToMenuBtn = Button(this).apply {
            text = "ANA MENÜ"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            visibility = View.GONE
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 120)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            params.setMargins(40, 0, 0, 80)
            layoutParams = params
            setOnClickListener { hideWebView() }
        }

        recordBtn = Button(this).apply {
            text = "KAYDI BAŞLAT"
            setBackgroundColor(Color.parseColor("#FF8C00"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 120)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            params.setMargins(0, 0, 40, 80)
            layoutParams = params
            setOnClickListener { toggleRecording() }
        }

        rootLayout.addView(menuLayout)
        rootLayout.addView(webView)
        rootLayout.addView(backToMenuBtn)
        rootLayout.addView(recordBtn)
    }

    private fun createMenuButton(txt: String, color: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(color))
            val params = LinearLayout.LayoutParams(600, 150)
            params.setMargins(0, 20, 0, 20)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun showLastVideo() {
        try {
            val lastVideoUri = getLastVideoUri()
            if (lastVideoUri == null) {
                Toast.makeText(this, "Henüz kaydedilmiş bir video bulunamadı.", Toast.LENGTH_SHORT).show()
                return
            }

            val container = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            val videoView = VideoView(this).apply {
                setVideoURI(lastVideoUri)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val mediaController = MediaController(this).apply {
                setAnchorView(videoView)
            }
            videoView.setMediaController(mediaController)

            val closeButton = Button(this).apply {
                text = "← KAPAT"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.RED)
                setOnClickListener {
                    videoView.stopPlayback()
                    setContentView(rootLayout)
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    120
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    setMargins(30, 200, 0, 0)
                }
            }

            val toolsButton = Button(this).apply {
                text = "← ARAÇLAR MENÜSÜNE DÖN"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.DKGRAY)
                setOnClickListener {
                    videoView.stopPlayback()
                    finish()
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    120
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, 200, 30, 0)
                }
            }

            container.addView(videoView)
            container.addView(closeButton)
            container.addView(toolsButton)
            setContentView(container)

            videoView.setOnPreparedListener {
                videoView.start()
                mediaController.show(3000)
            }

            videoView.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "Video oynatılamadı.", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Log.e("ArsaTakip", "Video oynatma hatası: ${e.message}", e)
            Toast.makeText(this, "Video açılırken hata oluştu.", Toast.LENGTH_LONG).show()
        }
    }
    private fun getLastVideoUri(): Uri? {
        return try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE
            )

            val selection =
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Video.Media.SIZE} > 0"
            val selectionArgs = arrayOf("ArsaDrone_%")
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val id = cursor.getLong(idColumn)

                    val uri = ContentUris.withAppendedId(collection, id)
                    Log.i("ArsaTakip", "Son ArsaDrone videosu bulundu: $uri")
                    return uri
                }
            }

            Log.w("ArsaTakip", "MediaStore icinde ArsaDrone videosu bulunamadi")
            null

        } catch (e: Exception) {
            Log.e("ArsaTakip", "Son video aranirken hata olustu", e)
            null
        }
    }
    private fun findMp4Files(parent: File, resultList: MutableList<File>) {
        parent.listFiles()?.forEach { file ->
            if (file.isDirectory) findMp4Files(file, resultList)
            else if (file.name.startsWith("ArsaDrone_") && file.name.lowercase().endsWith(".mp4")) {
                resultList.add(file)
            }
        }
    }

    private fun shareLastVideoOnWhatsApp() {
        try {
            val lastVideoUri = getLastVideoUri()
            if (lastVideoUri == null) {
                Toast.makeText(this, "Henüz paylaşılacak bir kayıt bulunamadı.", Toast.LENGTH_SHORT).show()
                return
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, lastVideoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val whatsappIntent = Intent(shareIntent).apply {
                setPackage("com.whatsapp")
            }

            try {
                startActivity(whatsappIntent)
            } catch (e: Exception) {
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        "Videoyu Paylaş"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ArsaTakip", "Share error", e)
            Toast.makeText(this, "Paylaşım sırasında hata oluştu.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showWebView(url: String) {
        menuLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        backToMenuBtn.visibility = View.VISIBLE
        if (url.contains("index.html")) recordBtn.visibility = View.VISIBLE
        recordBtn.bringToFront()
        setImmersiveMode(true)
        
        // Eğer zaten aynı sayfa yüklüyse tekrar yükleme yapma
        if (webView.url != url) {
            if (url == "file:///android_asset/arsatakip/index.html") {
                try {
                    val html = assets.open("arsatakip/index.html")
                        .bufferedReader()
                        .use { it.readText() }
                        .replace("__MAPBOX_TOKEN__", BuildConfig.MAPBOX_TOKEN)

                    webView.loadDataWithBaseURL(
                        "file:///android_asset/arsatakip/",
                        html,
                        "text/html",
                        "UTF-8",
                        url
                    )
                } catch (e: Exception) {
                    Log.e("ArsaTakip", "Harita HTML yukleme hatasi", e)
                    Toast.makeText(
                        this,
                        "3D harita yuklenemedi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                webView.loadUrl(url)
            }
        }
    }

    private fun hideWebView() {
        if (isRecording) stopRecording()
        webView.stopLoading()
        webView.visibility = View.GONE
        backToMenuBtn.visibility = View.GONE
        recordBtn.visibility = View.GONE
        menuLayout.visibility = View.VISIBLE
        setImmersiveMode(false)
    }

    private fun setImmersiveMode(enable: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enable) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val config = MediaProjectionConfig.createConfigForDefaultDisplay()
                projectionManager.createScreenCaptureIntent(config)
            } else {
                projectionManager.createScreenCaptureIntent()
            }
            recordLauncher.launch(captureIntent)
        }
    }

    private fun stopRecording() {
        startService(Intent(this, RecordService::class.java).apply { action = "STOP" })
        isRecording = false
        updateRecordButtonUI()
        Toast.makeText(this, "Kayıt Durduruldu", Toast.LENGTH_SHORT).show()
    }

    private fun updateRecordButtonUI() {
        if (isRecording) {
            recordBtn.text = "DURDUR"
            recordBtn.setBackgroundColor(Color.BLACK)
            // Kayıt sırasında diğer butonları gizle (Kayıtta görünmesinler)
            backToMenuBtn.visibility = View.GONE
            setImmersiveMode(true) 
            Toast.makeText(this, "Tam Ekran Kaydı Başladı", Toast.LENGTH_SHORT).show()
        } else {
            recordBtn.text = "KAYDI DURDUR"
            recordBtn.setBackgroundColor(Color.parseColor("#FF8C00"))
            backToMenuBtn.visibility = View.VISIBLE
            setImmersiveMode(true)
        }
    }

    private fun injectGeoJsonToWebView() {
        if (pendingGeoJson != null) {
            Log.d("ArsaTakip", "Injecting GeoJSON to existing map")
            val base64Data = Base64.encodeToString(pendingGeoJson?.toByteArray(), Base64.NO_WRAP)
            webView.evaluateJavascript("processGeoJSONBase64('$base64Data')", null)
            pendingGeoJson = null
            Toast.makeText(this, "Yeni Parsel Eklendi! Toplam: ${parcelList.size}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun injectAllParcels() {
        Log.d("ArsaTakip", "Injecting all parcels: ${parcelList.size}")
        for (parcel in parcelList) {
            val base64Data = Base64.encodeToString(parcel.toByteArray(), Base64.NO_WRAP)
            webView.evaluateJavascript("processGeoJSONBase64('$base64Data')", null)
        }
        pendingGeoJson = null
        if (parcelList.isNotEmpty()) {
            Toast.makeText(this, "${parcelList.size} Parsel Yüklendi!", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun clearParcelList() {
                parcelList.clear()
                runOnUiThread { Toast.makeText(this@ArsaTakipActivity, "Kayıtlı parsel listesi temizlendi.", Toast.LENGTH_SHORT).show() }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("ArsaTakip", "Page finished loading: $url")
                if (url != null && (url.contains("index.html") || url == "file:///android_asset/arsatakip/")) {
                    injectAllParcels()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView?, f: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                fileChooserCallback = f
                val intent = p?.createIntent()
                if (intent != null) fileChooserLauncher.launch(intent) else return false
                return true
            }
        }

        webView.setDownloadListener { url, ua, dis, mime, _ ->
            performDirectDownload(url, ua, mime)
        }
    }

    private fun performDirectDownload(url: String, ua: String, mime: String) {
        val cookie = CookieManager.getInstance().getCookie(url)
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ArsaTakipActivity, "Hızlı İndirme Başlatıldı...", Toast.LENGTH_SHORT).show()
                hideWebView()
            }

            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Cookie", cookie ?: "")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Sunucu Hatası: ${response.code}")
                    
                    val body = response.body
                    if (body != null) {
                        val bytes = body.bytes()
                        val fileName = URLUtil.guessFileName(url, null, mime)
                        
                        val content: String? = when {
                            fileName.endsWith(".kmz", true) -> readKmzFromBytes(bytes)
                            else -> String(bytes)
                        }

                        if (content != null && (content.contains("type") || content.contains("<kml") || content.contains("<Placemark"))) {
                            parcelList.add(content)
                            pendingGeoJson = content
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ArsaTakipActivity, "Parsel Alındı!", Toast.LENGTH_SHORT).show()
                                if (webView.visibility == View.VISIBLE && webView.url?.contains("index.html") == true) {
                                    injectGeoJsonToWebView()
                                } else {
                                    showWebView("file:///android_asset/arsatakip/index.html")
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ArsaTakipActivity, "Dosya tanınamadı veya hatalı.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ArsaTakip", "Direct download error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ArsaTakipActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun readKmzFromBytes(bytes: ByteArray): String? {
        try {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".kml", true)) {
                        return zis.bufferedReader().readText()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e("ArsaTakip", "KMZ read error", e)
        }
        return null
    }
}


















