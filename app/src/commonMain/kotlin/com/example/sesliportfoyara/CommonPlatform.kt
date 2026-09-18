package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf

expect fun getCurrentTimeMillis(): Long

interface PlatformUtils {
    fun openUri(uri: String)
    fun openEmlakAsistan()
    fun openArsaTakip()
    fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopVoiceRecognition()
    fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit)
    fun adminSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit)
    fun adminSignIn(email: String, password: String, onResult: (Boolean, String, String) -> Unit)
    fun adminResendConfirmation(email: String, onResult: (Boolean, String) -> Unit)
    fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit)
    fun pickFile(onResult: (String?) -> Unit)
}

val LocalPlatformUtils = staticCompositionLocalOf<PlatformUtils> {
    error("PlatformUtils not provided")
}



