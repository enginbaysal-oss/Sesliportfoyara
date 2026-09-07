package com.example.sesliportfoyara

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.russhwolf.settings.StorageSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

@JsFun("(onResult, onError) => { " +
    "try { " +
    "  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition; " +
    "  if (!SpeechRecognition) { onError('Bu tarayıcı sesli aramayı desteklemiyor.'); return; } " +
    "  const recognition = new SpeechRecognition(); " +
    "  recognition.lang = 'tr-TR'; " +
    "  recognition.onresult = (event) => { " +
    "    const text = event.results[0][0].transcript; " +
    "    onResult(text); " +
    "  }; " +
    "  recognition.onerror = (event) => { onError('Hata: ' + event.error); }; " +
    "  recognition.start(); " +
    "  window._currentRecognition = recognition; " +
    "} catch (e) { onError('Başlatılamadı: ' + e.message); } " +
    "}")
external fun jsStartVoiceRecognition(onResult: (JsString) -> Unit, onError: (JsString) -> Unit)

@JsFun("() => { if (window._currentRecognition) { window._currentRecognition.stop(); } }")
external fun jsStopVoiceRecognition()

@JsFun("(content, fileName) => { " +
    "try { " +
    "  const blob = new Blob([content], {type: 'application/json;charset=utf-8'}); " +
    "  const url = window.URL.createObjectURL(blob); " +
    "  const a = document.createElement('a'); " +
    "  a.href = url; " +
    "  a.download = fileName; " +
    "  document.body.appendChild(a); " +
    "  a.click(); " +
    "  setTimeout(() => { " +
    "    document.body.removeChild(a); " +
    "    window.URL.revokeObjectURL(url); " +
    "  }, 2000); " +
    "  console.log('✅ WEB: Download triggered for ' + fileName); " +
    "} catch (e) { " +
    "  console.error('❌ WEB: Download failed', e); " +
    "  alert('Yedekleme hatası: ' + e.message); " +
    "} " +
    "}")
external fun jsDownloadFile(content: JsString, fileName: JsString)

@JsFun("(callback) => { " +
    "try { " +
    "  const input = document.createElement('input'); " +
    "  input.type = 'file'; " +
    "  input.accept = '.json'; " +
    "  input.style.display = 'none'; " +
    "  input.onchange = (e) => { " +
    "    const file = e.target.files[0]; " +
    "    if (!file) { " +
    "      console.log('⚠️ WEB: No file selected'); " +
    "      callback(null); " +
    "      return; " +
    "    } " +
    "    const reader = new FileReader(); " +
    "    reader.onload = (re) => { " +
    "      console.log('✅ WEB: File read successfully'); " +
    "      callback(re.target.result); " +
    "      document.body.removeChild(input); " +
    "    }; " +
    "    reader.onerror = (err) => { " +
    "      console.error('❌ WEB: FileReader error', err); " +
    "      alert('Dosya okuma hatası!'); " +
    "    }; " +
    "    reader.readAsText(file); " +
    "  }; " +
    "  document.body.appendChild(input); " +
    "  input.click(); " +
    "} catch (e) { " +
    "  console.error('❌ WEB: Picker error', e); " +
    "  alert('Dosya seçici hatası: ' + e.message); " +
    "} " +
    "}")
external fun jsOpenFilePicker(callback: (JsString?) -> Unit)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        val platformUtils = object : PlatformUtils {
            override fun openUri(uri: String) {
                kotlinx.browser.window.open(uri, "_blank")
            }
            override fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit) {
                jsStartVoiceRecognition(
                    { result -> onResult(result.toString()) },
                    { error -> onError(error.toString()) }
                )
            }
            override fun stopVoiceRecognition() {
                jsStopVoiceRecognition()
            }
            override fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit) {
                try {
                    jsDownloadFile(content.toJsString(), fileName.toJsString())
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
