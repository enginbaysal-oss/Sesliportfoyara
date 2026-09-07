package com.example.sesliportfoyara

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.russhwolf.settings.StorageSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

@JsFun("(content, fileName) => { " +
    "const blob = new Blob([content], {type: 'application/json'}); " +
    "const url = URL.createObjectURL(blob); " +
    "const a = document.createElement('a'); " +
    "a.href = url; " +
    "a.download = fileName; " +
    "document.body.appendChild(a); " +
    "a.click(); " +
    "document.body.removeChild(a); " +
    "URL.revokeObjectURL(url); " +
    "}")
external fun jsDownloadFile(content: String, fileName: String)

@JsFun("() => { " +
    "window.pickFile = (callback) => { " +
    "  const input = document.createElement('input'); " +
    "  input.type = 'file'; " +
    "  input.accept = '.json'; " +
    "  input.onchange = (e) => { " +
    "    const file = e.target.files[0]; " +
    "    if (!file) { callback(null); return; } " +
    "    const reader = new FileReader(); " +
    "    reader.onload = (re) => { callback(re.target.result); }; " +
    "    reader.readAsText(file); " +
    "  }; " +
    "  input.click(); " +
    "}; " +
    "}")
external fun initJsFilePicker()

@JsFun("(callback) => window.pickFile(callback)")
external fun jsOpenFilePicker(callback: (JsString?) -> Unit)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initJsFilePicker()
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        val scope = rememberCoroutineScope()
        val platformUtils = object : PlatformUtils {
            override fun openUri(uri: String) {
                kotlinx.browser.window.open(uri, "_blank")
            }
            override fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit) {
                onError("Web sürümünde sesli arama yakında eklenecek")
            }
            override fun stopVoiceRecognition() {}
            override fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit) {
                try {
                    jsDownloadFile(content, fileName)
                    onResult(true)
                } catch (e: Exception) {
                    onResult(false)
                }
            }
            override fun pickFile(onResult: (String?) -> Unit) {
                jsOpenFilePicker { content ->
                    onResult(content?.toString())
                }
            }
        }

        val settings = remember { StorageSettings() } // Tarayıcı hafızasını (localStorage) kullanır
        val crmManager = remember { LocalCRMManager(settings) }
        val localPortfolioManager = remember { LocalPortfolioManager(settings) }

        CompositionLocalProvider(
            LocalPlatformUtils provides platformUtils,
            LocalDatabaseManager provides FirebaseDatabaseManager(),
            LocalCRMManagerProvider provides crmManager,
            LocalPortfolioManagerProvider provides localPortfolioManager
        ) {
            App()
        }
    }
}
