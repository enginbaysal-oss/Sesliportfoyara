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
@JsFun("(url, key, path, body, bearer, callback) => { fetch(url + path, { method: 'POST', headers: { 'apikey': key, 'Content-Type': 'application/json', ...(bearer ? {'Authorization':'Bearer ' + bearer} : {}) }, body: body }).then(async r => { const t = await r.text(); callback(r.ok, t); }).catch(e => callback(false, 'Baglanti hatasi: ' + e.message)); }")
external fun jsSupabasePost(url: JsString, key: JsString, path: JsString, body: JsString, bearer: JsString, callback: (Boolean, JsString) -> Unit)

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
                val body = """{"p_phone":${kotlinx.serialization.json.JsonPrimitive(normalized)}}"""
                jsSupabasePost("https://jcjerwvibjetomqeelsy.supabase.co".toJsString(), "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(), "/rest/v1/rpc/check_app_authorization".toJsString(), body.toJsString(), "".toJsString()) { ok, response ->
                    val text = response.toString(); try { val arr = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonArray; if (ok && arr.isNotEmpty()) { val o=arr[0].jsonObject; onResult(o["authorized"]?.jsonPrimitive?.boolean ?: false, o["full_name"]?.jsonPrimitive?.content ?: "", o["is_admin"]?.jsonPrimitive?.boolean ?: false, o["can_use_tools"]?.jsonPrimitive?.boolean ?: false, "") } else onResult(false, "", false, false, if(ok) "Bu telefon numarası için kullanım yetkisi bulunmuyor." else jsApiMessage(response).toString()) } catch(e:Exception) { onResult(false, "", false, false, "Yetkilendirme cevabı okunamadı.") }
                }
            }

            override fun requestRegistration(name: String, phone: String, onResult: (Boolean, String) -> Unit) {
                val normalized = phone.filter { it.isDigit() }.let { if (it.length == 10 && it.startsWith("5")) "0$it" else it }
                if (normalized.length != 11 || !normalized.startsWith("05")) { onResult(false, "Geçerli bir cep telefonu numarası giriniz."); return }
                val body = """{"full_name":${JsonPrimitive(name.trim())},"phone":${kotlinx.serialization.json.JsonPrimitive(normalized)},"is_active":false,"is_admin":false,"can_use_tools":false}"""
                jsSupabasePost(
                    "https://jcjerwvibjetomqeelsy.supabase.co".toJsString(),
                    "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(),
                    "/rest/v1/users".toJsString(),
                    body.toJsString(),
                    "".toJsString()
                ) { ok, response ->
                    onResult(ok, if (ok) "Kayıt talebiniz alındı. Yönetici onayı bekleniyor." else jsApiMessage(response).toString())
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
                    val message = if (ok) "KayÄ±t iÅŸlemi baÅŸarÄ±lÄ±. E-posta doÄŸrulamasÄ± gerekiyorsa gelen kutunuzu kontrol edin." else jsApiMessage(response).toString()
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
                    val message = if (success) "YÃ¶netici doÄŸrulamasÄ± baÅŸarÄ±lÄ±." else jsApiMessage(response).toString()
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
                    val message = if (ok) "DoÄŸrulama e-postasÄ± yeniden gÃ¶nderildi." else jsApiMessage(response).toString()
                    onResult(ok, message)
                }
            }

            override fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit) {
                jsSupabasePost(
                    "https://jcjerwvibjetomqeelsy.supabase.co".toJsString(),
                    "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig".toJsString(),
                    "/functions/v1/admin-users".toJsString(),
                    requestJson.toJsString(),
                    accessToken.toJsString()
                ) { ok, response ->
                    onResult(ok, response.toString())
                }
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
