package com.example.sesliportfoyara

import androidx.compose.runtime.staticCompositionLocalOf

expect fun getCurrentTimeMillis(): Long

interface PlatformUtils {
    fun openUri(uri: String)
    fun openEmlakAsistan() {}
    fun openArsaTakip() {}
    fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopVoiceRecognition()
    fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit)
    fun checkAppAuthorization(phone: String, onResult: (Boolean, String, Boolean, Boolean, String) -> Unit) { onResult(false, "", false, false, "Bu platformda yetkilendirme desteklenmiyor.") }
    fun supportsSecureUserAuth(): Boolean = false
    fun userSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "Bu platformda kullanıcı kaydı desteklenmiyor.")
    }
    fun userSignIn(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "Bu platformda kullanıcı girişi desteklenmiyor.")
    }
    fun restoreUserSession(onResult: (Boolean, String) -> Unit) {
        onResult(false, "Aktif oturum bulunamadı.")
    }
    fun refreshUserSession(onResult: (Boolean, String) -> Unit) {
        onResult(false, "Oturum yenileme desteklenmiyor.")
    }
    fun userSignOut() {}
    fun getUserAccessToken(): String = ""
    fun hasActiveSession(): Boolean = false
    fun setActiveSession(active: Boolean) {}
    fun saveRemaxUrl(url: String) {}
    fun scheduleReminder(dateTime: String, note: String): Boolean = false
    fun cancelReminder(dateTime: String, note: String) {}
    fun adminSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "Bu platformda yönetici kaydı desteklenmiyor.")
    }
    fun adminSignIn(email: String, password: String, onResult: (Boolean, String, String) -> Unit) {
        onResult(false, "", "Bu platformda yönetici girişi desteklenmiyor.")
    }
    fun adminResendConfirmation(email: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "Bu platformda e-posta doğrulaması desteklenmiyor.")
    }
    fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "Bu platformda kullanıcı yönetimi desteklenmiyor.")
    }
    fun pickFile(onResult: (String?) -> Unit)
}

val LocalPlatformUtils = staticCompositionLocalOf<PlatformUtils> {
    error("PlatformUtils not provided")
}
