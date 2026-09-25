package com.example.sesliportfoyara
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean


import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.russhwolf.settings.StorageSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@JsFun("(onResult, onError) => { " +
    "console.log('🎤 Web Speech API initiation...'); " +
    "if (!window.isSecureContext) { " +
    "  onError('Sesli arama için güvenli bağlantı (HTTPS) gereklidir.'); " +
    "  return; " +
    "} " +
    "const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition; " +
    "if (!SpeechRecognition) { " +
    "  onError('Bu tarayıcı Web Speech API desteklemiyor. Lütfen Chrome veya Edge kullanın.'); " +
    "  return; " +
    "} " +
    "navigator.mediaDevices.getUserMedia({ audio: true }) " +
    "  .then((stream) => { " +
    "    console.log('✅ Mikrofon izni alındı'); " +
    "    stream.getTracks().forEach(track => track.stop()); " +
    "    const recognition = new SpeechRecognition(); " +
    "    recognition.lang = 'tr-TR'; " +
    "    recognition.interimResults = false; " +
    "    recognition.maxAlternatives = 1; " +
    "    recognition.onresult = (event) => { " +
    "      const text = event.results[0][0].transcript; " +
    "      console.log('🎤 Algılanan ses:', text); " +
    "      onResult(text); " +
    "    }; " +
    "    recognition.onerror = (event) => { " +
    "      console.error('❌ SpeechRecognition hatası:', event.error); " +
    "      onError('Hata: ' + event.error); " +
    "    }; " +
    "    recognition.onend = () => { " +
    "      console.log('🎤 Dinleme bitti'); " +
    "      window._currentRecognition = null; " +
    "    }; " +
    "    recognition.start(); " +
    "    window._currentRecognition = recognition; " +
    "  }) " +
    "  .catch((err) => { " +
    "    console.error('❌ Mikrofon erişim hatası:', err); " +
    "    onError('Mikrofon izni verilmedi veya erişilemiyor.'); " +
    "  }); " +
    "}")
external fun jsStartVoiceRecognition(onResult: (JsString) -> Unit, onError: (JsString) -> Unit)

@JsFun("() => { " +
    "if (window._currentRecognition) { " +
    "  try { window._currentRecognition.stop(); } catch(e) {} " +
    "  window._currentRecognition = null; " +
    "} " +
    "}")
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
@JsFun("(url, key, path, body, bearer, callback) => { " +
    "const doFetch = (retry) => { " +
    "  fetch(url + path, { method: 'POST', headers: { 'apikey': key, 'Content-Type': 'application/json', ...(bearer ? {'Authorization':'Bearer ' + bearer} : {}) }, body: body })" +
    "    .then(async r => { const t = await r.text(); callback(r.ok, t); })" +
    "    .catch(e => { " +
    "      if (retry) { setTimeout(() => doFetch(false), 300); } " +
    "      else { callback(false, 'Baglanti hatasi: ' + e.message); } " +
    "    }); " +
    "}; " +
    "doFetch(true); " +
    "}")
external fun jsSupabasePost(url: JsString, key: JsString, path: JsString, body: JsString, bearer: JsString, callback: (Boolean, JsString) -> Unit)

@JsFun("(url, key, path, body, bearer, callback) => { " +
    "const doFetch = (retry) => { " +
    "  fetch(url + path, { method: 'PATCH', headers: { 'apikey': key, 'Content-Type': 'application/json', 'Prefer': 'return=minimal', ...(bearer ? {'Authorization':'Bearer ' + bearer} : {}) }, body: body })" +
    "    .then(async r => { const t = await r.text(); callback(r.ok, t); })" +
    "    .catch(e => { " +
    "      if (retry) { setTimeout(() => doFetch(false), 300); } " +
    "      else { callback(false, 'Baglanti hatasi: ' + e.message); } " +
    "    }); " +
    "}; " +
    "doFetch(true); " +
    "}")
external fun jsSupabasePatch(url: JsString, key: JsString, path: JsString, body: JsString, bearer: JsString, callback: (Boolean, JsString) -> Unit)

@JsFun("(url, path, body, callback) => { fetch(url + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body }).then(async r => { const t = await r.text(); callback(r.ok, t); }).catch(e => callback(false, 'Baglanti hatasi: ' + e.message)); }")
external fun jsFirebasePost(url: JsString, path: JsString, body: JsString, callback: (Boolean, JsString) -> Unit)

@JsFun("(url, path, callback) => { fetch(url + path, { method: 'GET' }).then(async r => { const t = await r.text(); callback(r.ok, t); }).catch(e => callback(false, '{}')); }")
external fun jsFirebaseGet(url: JsString, path: JsString, callback: (Boolean, JsString) -> Unit)

@JsFun("(url, path, callback) => { fetch(url + path, { method: 'DELETE' }).then(async r => { callback(r.ok); }).catch(e => callback(false)); }")
external fun jsFirebaseDelete(url: JsString, path: JsString, callback: (Boolean) -> Unit)

@JsFun("(text) => { try { const j=JSON.parse(text); return (j.access_token || '').toString(); } catch(e) { return ''; } }")
external fun jsAccessToken(text: JsString): JsString

@JsFun("(text) => { try { const j=JSON.parse(text); return (j.msg || j.message || j.error_description || j.error || text).toString(); } catch(e) { return text; } }")
external fun jsApiMessage(text: JsString): JsString

@JsFun("(url) => { " +
    "if (document.getElementById('cepte_overlay')) return; " +
    "const overlay = document.createElement('div'); " +
    "overlay.id = 'cepte_overlay';" +
    "overlay.style.position = 'fixed';" +
    "overlay.style.top = '0';" +
    "overlay.style.left = '0';" +
    "overlay.style.width = '100vw';" +
    "overlay.style.height = '100vh';" +
    "overlay.style.zIndex = '999999';" +
    "overlay.style.background = '#0F141E';" +
    "overlay.style.display = 'flex';" +
    "overlay.style.flexDirection = 'column';" +
    "" +
    "const topBar = document.createElement('div');" +
    "topBar.style.height = '50px';" +
    "topBar.style.background = '#1b1b1b';" +
    "topBar.style.color = '#fff';" +
    "topBar.style.display = 'flex';" +
    "topBar.style.alignItems = 'center';" +
    "topBar.style.justifyContent = 'space-between';" +
    "topBar.style.padding = '0 16px';" +
    "topBar.style.fontFamily = 'sans-serif';" +
    "topBar.style.borderBottom = '1px solid #333';" +
    "" +
    "const title = document.createElement('span');" +
    "title.innerText = 'Cepte Emlak Ara';" +
    "title.style.fontWeight = 'bold';" +
    "title.style.fontSize = '16px';" +
    "topBar.appendChild(title);" +
    "" +
    "const btnGroup = document.createElement('div');" +
    "btnGroup.style.display = 'flex';" +
    "btnGroup.style.gap = '10px';" +
    "" +
    "const menuBtn = document.createElement('button');" +
    "menuBtn.innerText = 'Ana Menü'; " +
    "menuBtn.style.background = '#22A447';" +
    "menuBtn.style.color = '#000';" +
    "menuBtn.style.border = 'none';" +
    "menuBtn.style.padding = '6px 14px';" +
    "menuBtn.style.borderRadius = '6px';" +
    "menuBtn.style.cursor = 'pointer';" +
    "menuBtn.style.fontWeight = 'bold';" +
    "menuBtn.onclick = () => { document.body.removeChild(overlay); };" +
    "btnGroup.appendChild(menuBtn);" +
    "" +
    "const closeBtn = document.createElement('button');" +
    "closeBtn.innerText = 'Geri'; " +
    "closeBtn.style.background = '#333';" +
    "closeBtn.style.color = '#fff';" +
    "closeBtn.style.border = 'none';" +
    "closeBtn.style.padding = '6px 14px';" +
    "closeBtn.style.borderRadius = '6px';" +
    "closeBtn.style.cursor = 'pointer';" +
    "closeBtn.style.fontWeight = 'bold';" +
    "closeBtn.onclick = () => { document.body.removeChild(overlay); };" +
    "btnGroup.appendChild(closeBtn);" +
    "" +
    "topBar.appendChild(btnGroup);" +
    "overlay.appendChild(topBar);" +
    "" +
    "const iframe = document.createElement('iframe');" +
    "iframe.src = url;" +
    "iframe.style.width = '100%';" +
    "iframe.style.height = 'calc(100vh - 50px)';" +
    "iframe.style.border = 'none';" +
    "overlay.appendChild(iframe);" +
    "" +
    "document.body.appendChild(overlay);" +
    "}")
external fun jsOpenOverlay(url: JsString)


@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        val platformUtils = object : PlatformUtils {
            override fun openUri(uri: String) {
                kotlinx.browser.window.open(uri, "_blank")
            }
            override fun openEmlakAsistan() {
                jsOpenOverlay("emlakasistan/index.html".toJsString())
            }

            override fun openArsaTakip() {
                jsOpenOverlay("arsatakip/index.html".toJsString())
            }

            override fun hasActiveSession(): Boolean {
                val s1 = window.localStorage.getItem("cepte_emlak_authorized")
                val s2 = window.sessionStorage.getItem("cepte_emlak_authorized")
                return s1 == "true" || s2 == "true"
            }

            override fun setActiveSession(active: Boolean) {
                if (active) {
                    window.localStorage.setItem("cepte_emlak_authorized", "true")
                    window.sessionStorage.setItem("cepte_emlak_authorized", "true")
                } else {
                    window.localStorage.removeItem("cepte_emlak_authorized")
                    window.sessionStorage.removeItem("cepte_emlak_authorized")
                }
            }

            override fun checkAppAuthorization(phone: String, onResult: (Boolean, String, Boolean, Boolean, String) -> Unit) {
                val normalized = phone.filter { it.isDigit() }.let { if (it.length == 10 && it.startsWith("5")) "0$it" else it }
                if (normalized.length != 11 || !normalized.startsWith("05")) { onResult(false, "", false, false, "Geçerli bir cep telefonu numarası giriniz."); return }
                
                jsFirebaseGet(
                    "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                    "/authorized_users.json".toJsString()
                ) { ok, response ->
                    var fbAuthorized = false
                    var fbName = ""
                    var fbAdmin = false
                    var fbTools = false
                    if (ok) {
                        try {
                            val text = response.toString()
                            if (text != "null" && text.isNotBlank()) {
                                val map = Json.parseToJsonElement(text).jsonObject
                                for ((_, value) in map) {
                                    val uObj = value.jsonObject
                                    val uPhone = uObj["phone"]?.jsonPrimitive?.content?.filter { it.isDigit() }
                                    if (uPhone == normalized) {
                                        val isActive = uObj["is_active"]?.jsonPrimitive?.boolean ?: true
                                        if (isActive) {
                                            fbAuthorized = true
                                            fbName = uObj["full_name"]?.jsonPrimitive?.content ?: uObj["fullName"]?.jsonPrimitive?.content ?: ""
                                            fbAdmin = uObj["is_admin"]?.jsonPrimitive?.boolean ?: uObj["isAdmin"]?.jsonPrimitive?.boolean ?: false
                                            fbTools = uObj["can_use_tools"]?.jsonPrimitive?.boolean ?: uObj["canUseTools"]?.jsonPrimitive?.boolean ?: false
                                        }
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    if (fbAuthorized) {
                        onResult(true, fbName, fbAdmin, fbTools, "")
                    } else {
                        val body = """{"p_phone":${JsonPrimitive(normalized)}}"""
                        jsSupabasePost("https://jcjerwvibjetomqeelsy.supabase.co".toJsString(), "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(), "/rest/v1/rpc/check_app_authorization".toJsString(), body.toJsString(), "".toJsString()) { supOk, supResp ->
                            try {
                                val text = supResp.toString()
                                val arr = Json.parseToJsonElement(text).jsonArray
                                if (supOk && arr.isNotEmpty()) {
                                    val o = arr[0].jsonObject
                                    val authorized = o["authorized"]?.jsonPrimitive?.boolean ?: false
                                    val fullName = o["full_name"]?.jsonPrimitive?.content ?: ""
                                    val isAdmin = o["is_admin"]?.jsonPrimitive?.boolean ?: false
                                    val canUseTools = o["can_use_tools"]?.jsonPrimitive?.boolean ?: false
                                    if (authorized) {
                                        onResult(true, fullName, isAdmin, canUseTools, "")
                                        return@jsSupabasePost
                                    }
                                }
                            } catch (_: Exception) {}
                            onResult(false, "", false, false, "Bu telefon numarası için kullanım yetkisi bulunmuyor.")
                        }
                    }
                }
            }

            override fun requestRegistration(name: String, phone: String, onResult: (Boolean, String) -> Unit) {
                val normalized = phone.filter { it.isDigit() }.let { if (it.length == 10 && it.startsWith("5")) "0$it" else it }
                if (normalized.length != 11 || !normalized.startsWith("05")) { onResult(false, "Geçerli bir cep telefonu numarası giriniz."); return }
                val body = """{"name":${JsonPrimitive(name.trim())},"phone":${JsonPrimitive(normalized)},"createdAt":${getCurrentTimeMillis()}}"""
                jsFirebasePost(
                    "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                    "/registration_requests.json".toJsString(),
                    body.toJsString()
                ) { ok, response ->
                    onResult(ok, if (ok) "Kayıt talebiniz alındı. Yönetici onayı bekleniyor." else "Kayıт talebi gönderilemedi.")
                }
            }

            override fun getRegistrationRequests(onResult: (String) -> Unit) {
                jsFirebaseGet(
                    "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                    "/registration_requests.json".toJsString()
                ) { ok, response ->
                    if (ok) onResult(response.toString()) else onResult("{}")
                }
            }

            override fun deleteRegistrationRequest(id: String, onResult: (Boolean) -> Unit) {
                jsFirebaseDelete(
                    "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                    "/registration_requests/$id.json".toJsString()
                ) { ok ->
                    onResult(ok)
                }
            }

            override fun adminSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit) {
                val body = """{"email":${kotlinx.serialization.json.JsonPrimitive(email.trim())},"password":${kotlinx.serialization.json.JsonPrimitive(password)}}"""
                jsSupabasePost(
                    "https://jcjerwvibjetomqeelsy.supabase.co".toJsString(),
                    "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(),
                    "/auth/v1/signup".toJsString(),
                    body.toJsString(),
                    "".toJsString()
                ) { ok, response ->
                    val text = response.toString()
                    val message = if (ok) "Kayıt işlemi başarılı. E-posta doğrulaması gerekiyorsa gelen kutunuzu kontrol edin." else jsApiMessage(response).toString()
                    onResult(ok, message)
                }
            }

            override fun adminSignIn(email: String, password: String, onResult: (Boolean, String, String) -> Unit) {
                val body = """{"email":${kotlinx.serialization.json.JsonPrimitive(email.trim())},"password":${kotlinx.serialization.json.JsonPrimitive(password)}}"""
                jsSupabasePost(
                    "https://jcjerwvibjetomqeelsy.supabase.co".toJsString(),
                    "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(),
                    "/auth/v1/token?grant_type=password".toJsString(),
                    body.toJsString(),
                    "".toJsString()
                ) { ok, response ->
                    val token = if (ok) jsAccessToken(response).toString() else ""
                    val success = ok && token.isNotBlank()
                    val message = if (success) "Yönetici doğrulaması başarılı." else jsApiMessage(response).toString()
                    onResult(success, token, message)
                }
            }

            override fun adminResendConfirmation(email: String, onResult: (Boolean, String) -> Unit) {
                val body = """{"type":"signup","email":${kotlinx.serialization.json.JsonPrimitive(email.trim())}}"""
                jsSupabasePost(
                    "https://jcjerwvibjetomqeelsy.supabase.co".toJsString(),
                    "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(),
                    "/auth/v1/resend".toJsString(),
                    body.toJsString(),
                    "".toJsString()
                ) { ok, response ->
                    val message = if (ok) "Doğrulama e-postası yeniden gönderildi." else jsApiMessage(response).toString()
                    onResult(ok, message)
                }
            }

            override fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit) {
                try {
                    val obj = Json.parseToJsonElement(requestJson).jsonObject
                    val action = obj["action"]?.jsonPrimitive?.content
                    if (action == "list") {
                        jsFirebaseGet(
                            "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                            "/authorized_users.json".toJsString()
                        ) { fbOk, fbResp ->
                            val fbUsers = mutableMapOf<String, JsonObject>()
                            if (fbOk) {
                                try {
                                    val text = fbResp.toString()
                                    if (text != "null" && text.isNotBlank()) {
                                        val map = Json.parseToJsonElement(text).jsonObject
                                        for ((_, value) in map) {
                                            val uObj = value.jsonObject
                                            val phone = uObj["phone"]?.jsonPrimitive?.content?.filter { it.isDigit() } ?: ""
                                            if (phone.isNotBlank()) {
                                                fbUsers[phone] = uObj
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }

                            jsSupabasePost(
                                "https://jcjerwvibjetomqeelsy.supabase.co".toJsString(),
                                "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(),
                                "/functions/v1/admin-users".toJsString(),
                                requestJson.toJsString(),
                                accessToken.toJsString()
                            ) { supOk, supResp ->
                                if (supOk) {
                                    try {
                                        val supText = supResp.toString()
                                        val supRoot = Json.parseToJsonElement(supText).jsonObject
                                        supRoot["users"]?.jsonArray?.forEach { el ->
                                            val uObj = el.jsonObject
                                            val phone = uObj["phone"]?.jsonPrimitive?.content?.filter { it.isDigit() } ?: ""
                                            if (phone.isNotBlank() && !fbUsers.containsKey(phone)) {
                                                fbUsers[phone] = uObj
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                val resultJson = """{"users":[${fbUsers.values.joinToString(",")}]}"""
                                onResult(true, resultJson)
                            }
                        }
                        return
                    } else if (action == "add") {
                        val fullName = obj["full_name"]?.jsonPrimitive?.content ?: ""
                        val phone = obj["phone"]?.jsonPrimitive?.content ?: ""
                        val officeName = obj["office_name"]?.jsonPrimitive?.content ?: obj["officeName"]?.jsonPrimitive?.content ?: "RE/MAX"
                        val canUseTools = obj["can_use_tools"]?.jsonPrimitive?.boolean ?: false
                        val isAdmin = obj["is_admin"]?.jsonPrimitive?.boolean ?: false
                        val id = getCurrentTimeMillis()
                        val userJson = """{"id":$id,"full_name":${JsonPrimitive(fullName)},"phone":${JsonPrimitive(phone)},"office_name":${JsonPrimitive(officeName)},"is_active":true,"can_use_tools":$canUseTools,"is_admin":$isAdmin}"""
                        val sanitizedPhone = phone.filter { it.isDigit() }
                        jsFirebasePost(
                            "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                            "/authorized_users/$sanitizedPhone.json".toJsString(),
                            userJson.toJsString()
                        ) { ok, resp ->
                            onResult(ok, resp.toString())
                        }
                        return
                    } else if (action == "update") {
                        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                        jsFirebaseGet(
                            "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                            "/authorized_users.json".toJsString()
                        ) { ok, resp ->
                            if (ok) {
                                try {
                                    val text = resp.toString()
                                    if (text == "null" || text.isBlank()) {
                                        onResult(false, "Kullanıcı bulunamadı")
                                        return@jsFirebaseGet
                                    }
                                    val map = Json.parseToJsonElement(text).jsonObject
                                    var targetKey = ""
                                    var targetVal = JsonObject(emptyMap())
                                    for ((key, value) in map) {
                                        val uObj = value.jsonObject
                                        if (uObj["id"]?.jsonPrimitive?.content?.toLongOrNull() == id) {
                                            targetKey = key
                                            targetVal = uObj
                                            break
                                        }
                                    }
                                    if (targetKey.isNotBlank()) {
                                        val mutableObj = targetVal.toMutableMap()
                                        obj["is_active"]?.let { mutableObj["is_active"] = it }
                                        obj["can_use_tools"]?.let { mutableObj["can_use_tools"] = it }
                                        obj["is_admin"]?.let { mutableObj["is_admin"] = it }
                                        obj["office_name"]?.let { mutableObj["office_name"] = it }
                                        val updatedJson = JsonObject(mutableObj).toString()
                                        jsFirebasePost(
                                            "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                                            "/authorized_users/$targetKey.json".toJsString(),
                                            updatedJson.toJsString()
                                        ) { updateOk, updateResp ->
                                            onResult(updateOk, updateResp.toString())
                                        }
                                    } else {
                                        onResult(false, "Kullanıcı bulunamadı")
                                    }
                                } catch (e: Exception) {
                                    onResult(false, e.message ?: "Güncelleme hatası")
                                }
                            } else {
                                onResult(false, "Yetkili kullanıcılar okunamadı")
                            }
                        }
                        return
                    } else if (action == "delete") {
                        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                        jsFirebaseGet(
                            "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                            "/authorized_users.json".toJsString()
                        ) { ok, resp ->
                            if (ok) {
                                try {
                                    val text = resp.toString()
                                    if (text == "null" || text.isBlank()) {
                                        onResult(false, "Kullanıcı bulunamadı")
                                        return@jsFirebaseGet
                                    }
                                    val map = Json.parseToJsonElement(text).jsonObject
                                    var targetKey = ""
                                    for ((key, value) in map) {
                                        val uObj = value.jsonObject
                                        if (uObj["id"]?.jsonPrimitive?.content?.toLongOrNull() == id) {
                                            targetKey = key
                                            break
                                        }
                                    }
                                    if (targetKey.isNotBlank()) {
                                        jsFirebaseDelete(
                                            "https://sesliaraportfoy-default-rtdb.europe-west1.firebasedatabase.app".toJsString(),
                                            "/authorized_users/$targetKey.json".toJsString()
                                        ) { deleteOk ->
                                            onResult(deleteOk, if (deleteOk) "{}" else "Silme başarısız")
                                        }
                                    } else {
                                        onResult(false, "Kullanıcı bulunamadı")
                                    }
                                } catch (e: Exception) {
                                    onResult(false, e.message ?: "Silme hatası")
                                }
                            } else {
                                onResult(false, "Kullanıcılar okunamadı")
                            }
                        }
                        return
                    }
                } catch (_: Exception) {}

                onResult(false, "Geçersiz işlem")
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
