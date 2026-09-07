package com.example.sesliportfoyara

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.russhwolf.settings.Settings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null
    
    private var saveFileCallback: ((Boolean) -> Unit)? = null
    private var saveFileContent: String = ""
    private var pickFileCallback: ((String?) -> Unit)? = null

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { 
                    it.write(saveFileContent.toByteArray())
                }
                saveFileCallback?.invoke(true)
            } catch (e: Exception) {
                e.printStackTrace()
                saveFileCallback?.invoke(false)
            }
        } else {
            saveFileCallback?.invoke(false)
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                pickFileCallback?.invoke(content)
            } catch (e: Exception) {
                e.printStackTrace()
                pickFileCallback?.invoke(null)
            }
        } else {
            pickFileCallback?.invoke(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val options = FirebaseOptions(
            applicationId = "1:756529793201:web:cd77ae8b393bf211893a90",
            apiKey = "AIzaSyBbLAjj7BkVKpVWtap90ZLszMvpBQsD39Y",
            databaseUrl = "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app",
            projectId = "sesliaraportfoy",
            storageBucket = "sesliaraportfoy.firebasestorage.app"
        )
        
        try {
            Firebase.initialize(this, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        checkPermissions()

        val platformUtils = object : PlatformUtils {
            override fun openUri(uri: String) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit) {
                runOnUiThread {
                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                    }
                    
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    }

                    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            onError("Hata kodu: $error")
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onResult(matches[0])
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    },)
                    speechRecognizer?.startListening(intent)
                }
            }

            override fun stopVoiceRecognition() {
                runOnUiThread {
                    speechRecognizer?.stopListening()
                }
            }

            override fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit) {
                saveFileCallback = onResult
                saveFileContent = content
                createDocumentLauncher.launch(fileName)
            }

            override fun pickFile(onResult: (String?) -> Unit) {
                pickFileCallback = onResult
                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
        }

        setContent {
            val settings = remember { Settings() }
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

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
