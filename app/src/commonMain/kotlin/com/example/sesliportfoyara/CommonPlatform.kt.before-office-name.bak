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
    fun checkAppAuthorization(phone: String, onResult: (Boolean, String, Boolean, Boolean, String) -> Unit) { onResult(false, "", false, false, "Bu platformda yetkilendirme desteklenmiyor.") }
    fun hasActiveSession(): Boolean = false
    fun setActiveSession(active: Boolean) {}
    fun saveRemaxUrl(url: String) {}
    fun scheduleReminder(dateTime: String, note: String): Boolean = false
    fun cancelReminder(dateTime: String, note: String) {}
    fun adminSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit)
    fun adminSignIn(email: String, password: String, onResult: (Boolean, String, String) -> Unit)
    fun adminResendConfirmation(email: String, onResult: (Boolean, String) -> Unit)
    fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit)
    fun pickFile(onResult: (String?) -> Unit)
    fun requestRegistration(name: String, phone: String, onResult: (Boolean, String) -> Unit) { onResult(false, "Bu platformda kayıt talebi desteklenmiyor.") }
    fun getRegistrationRequests(onResult: (String) -> Unit) { onResult("{}") }
    fun deleteRegistrationRequest(id: String, onResult: (Boolean) -> Unit) { onResult(false) }
}

val LocalPlatformUtils = staticCompositionLocalOf<PlatformUtils> {
    error("PlatformUtils not provided")
}



