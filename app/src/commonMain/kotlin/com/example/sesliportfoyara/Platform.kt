package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf

expect fun getCurrentTimeMillis(): Long

interface PlatformUtils {
    fun openUri(uri: String)
    fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopVoiceRecognition()
    fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit)
    fun pickFile(onResult: (String?) -> Unit)
}

val LocalPlatformUtils = staticCompositionLocalOf<PlatformUtils> {
    error("PlatformUtils not provided")
}
