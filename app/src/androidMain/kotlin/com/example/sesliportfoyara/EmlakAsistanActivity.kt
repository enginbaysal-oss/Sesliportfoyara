package com.example.sesliportfoyara

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EmlakAsistanActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            val data = result.data

            val uris = if (result.resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            } else {
                null
            }

            callback.onReceiveValue(uris)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript("typeof window.jspdf + ' / ' + (window.jspdf ? typeof window.jspdf.jsPDF : 'YOK')") { result ->
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@EmlakAsistanActivity.filePathCallback?.onReceiveValue(null)
                this@EmlakAsistanActivity.filePathCallback = filePathCallback

                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }

                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@EmlakAsistanActivity.filePathCallback = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }
        }

        webView.addJavascriptInterface(
            EmlakAsistanBridge(this),
            "AndroidInterface"
        )

        setContentView(webView)

        webView.loadUrl(
            "file:///android_asset/emlakasistan/index.html"
        )
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("AndroidInterface")
        webView.destroy()
        super.onDestroy()
    }
}

class EmlakAsistanBridge(
    private val activity: Activity
) {
    @JavascriptInterface
    fun anaMenuyeDon() {
        activity.runOnUiThread {
            activity.finish()
        }
    }


    private val archiveDir: File
        get() = File(activity.filesDir, "emlakasistan_archive").apply {
            if (!exists()) mkdirs()
        }

    @JavascriptInterface
    fun downloadFile(
        base64Data: String,
        fileName: String,
        mimeType: String
    ): Boolean {
        return try {
            val safeName = File(fileName).name
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)

            val file = File(archiveDir, safeName)
            file.writeBytes(bytes)

            if (safeName.endsWith(".pdf", ignoreCase = true)) {
                activity.runOnUiThread {
                    openArchiveFile(safeName)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun getArchiveList(): String {
        return try {
            val result = JSONArray()

            archiveDir.listFiles()
                ?.filter { it.isFile }
                ?.forEach { file ->
                    val obj = JSONObject()

                    obj.put("name", file.name)
                    obj.put("date", file.lastModified())
                    obj.put("size", file.length())

                    result.put(obj)
                }

            result.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "[]"
        }
    }

    @JavascriptInterface
    fun readArchiveFile(fileName: String): String {
        return try {
            val safeName = File(fileName).name
            val file = File(archiveDir, safeName)

            if (file.exists()) {
                file.readText(Charsets.UTF_8)
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JavascriptInterface
    fun deleteArchiveFile(fileName: String): Boolean {
        return try {
            val safeName = File(fileName).name
            val file = File(archiveDir, safeName)

            file.exists() && file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun openArchiveFile(fileName: String) {
        try {
            val safeName = File(fileName).name
            val file = File(archiveDir, safeName)

            if (!file.exists()) return

            if (file.extension.equals("json", ignoreCase = true)) {
                val json = file.readText(Charsets.UTF_8)

                val quotedJson = JSONObject.quote(json)

                activity.runOnUiThread {
                    val webView =
                        activity.findViewById<WebView>(android.R.id.content)
                            ?: return@runOnUiThread

                    webView.evaluateJavascript(
                        "if(window.app && app.loadCalculationFromJson){" +
                            "app.loadCalculationFromJson($quotedJson);" +
                        "}",
                        null
                    )
                }

                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                activity,
                activity.packageName + ".fileprovider",
                file
            )

            val mimeType =
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}



