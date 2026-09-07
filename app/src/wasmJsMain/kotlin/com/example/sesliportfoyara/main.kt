package com.example.sesliportfoyara

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.russhwolf.settings.StorageSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        val platformUtils = object : PlatformUtils {
            override fun openUri(uri: String) {
                // Tarayıcıda yeni sekmede aç
                kotlinx.browser.window.open(uri, "_blank")
            }
            override fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit) {
                onError("Web sürümünde sesli arama yakında eklenecek")
            }
            override fun stopVoiceRecognition() {}
            override fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit) {
                onResult(false) // Web'de doğrudan dosya kaydetme farklıdır
            }
            override fun pickFile(onResult: (String?) -> Unit) {
                onResult(null)
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
