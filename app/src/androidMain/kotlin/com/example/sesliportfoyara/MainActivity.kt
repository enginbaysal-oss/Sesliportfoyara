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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.russhwolf.settings.Settings
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

class MainActivity : ComponentActivity() {
    private val supabaseClient = OkHttpClient()

    data class AuthorizationResult(
        val authorized: Boolean,
        val fullName: String = "",
        val isAdmin: Boolean = false,
        val canUseTools: Boolean = false,
        val error: String? = null
    )

    private fun normalizePhone(phone: String): String {
        var digits = phone.filter { it.isDigit() }

        if (digits.startsWith("90") && digits.length == 12) {
            digits = "0" + digits.substring(2)
        } else if (digits.length == 10 && digits.startsWith("5")) {
            digits = "0$digits"
        }

        return digits
    }

    private fun checkSupabaseAuthorization(
        phone: String,
        onResult: (AuthorizationResult) -> Unit
    ) {
        val normalizedPhone = normalizePhone(phone)

        if (normalizedPhone.length != 11 || !normalizedPhone.startsWith("05")) {
            onResult(
                AuthorizationResult(
                    authorized = false,
                    error = "Geçerli bir cep telefonu numarası giriniz."
                )
            )
            return
        }

        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) {
            onResult(
                AuthorizationResult(
                    authorized = false,
                    error = "Yetkilendirme servisi yapılandırılmamış."
                )
            )
            return
        }

        val json = JSONObject()
            .put("p_phone", normalizedPhone)
            .toString()

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/check_app_authorization")
            .addHeader("apikey", BuildConfig.SUPABASE_KEY)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        supabaseClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    onResult(
                        AuthorizationResult(
                            authorized = false,
                            error = "Yetki kontrolü yapılamadı. İnternet bağlantınızı kontrol edin."
                        )
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) {
                            runOnUiThread {
                                onResult(
                                    AuthorizationResult(
                                        authorized = false,
                                        error = "Yetki sunucusu hatası (${it.code})."
                                    )
                                )
                            }
                            return
                        }

                        val body = it.body?.string().orEmpty()
                        val array = JSONArray(body)

                        if (array.length() == 0) {
                            runOnUiThread {
                                onResult(
                                    AuthorizationResult(
                                        authorized = false,
                                        error = "Bu telefon numarası için kullanım yetkisi bulunmuyor."
                                    )
                                )
                            }
                            return
                        }

                        val item = array.getJSONObject(0)

                        runOnUiThread {
                            onResult(
                                AuthorizationResult(
                                    authorized = item.optBoolean("authorized", false),
                                    fullName = item.optString("full_name", ""),
                                    isAdmin = item.optBoolean("is_admin", false),
                                    canUseTools = item.optBoolean("can_use_tools", false)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            onResult(
                                AuthorizationResult(
                                    authorized = false,
                                    error = "Yetkilendirme cevabı okunamadı."
                                )
                            )
                        }
                    }
                }
            }
        })
    }
    private fun adminSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        val json = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/signup")
            .addHeader("apikey", BuildConfig.SUPABASE_KEY)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        supabaseClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onResult(false, "Bağlantı hatası: ${e.message ?: e.javaClass.simpleName}") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) {
                        runOnUiThread { onResult(true, "Yönetici hesabı oluşturuldu.") }
                    } else {
                        val message = try {
                            JSONObject(body).optString("msg",
                                JSONObject(body).optString("message", "Hesap oluşturulamadı."))
                        } catch (_: Exception) {
                            "Hesap oluşturulamadı (${it.code})."
                        }
                        runOnUiThread { onResult(false, message) }
                    }
                }
            }
        })
    }

    private fun adminResendConfirmation(email: String, onResult: (Boolean, String) -> Unit) {
        val json = JSONObject()
            .put("type", "signup")
            .put("email", email.trim())
            .toString()

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/resend")
            .addHeader("apikey", BuildConfig.SUPABASE_KEY)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        supabaseClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    onResult(false, "Bağlantı hatası: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()

                    if (it.isSuccessful) {
                        runOnUiThread {
                            onResult(true, "Yeni doğrulama e-postası gönderildi.")
                        }
                    } else {
                        val message = try {
                            val obj = JSONObject(body)
                            obj.optString(
                                "msg",
                                obj.optString(
                                    "message",
                                    "Doğrulama e-postası gönderilemedi (${it.code})."
                                )
                            )
                        } catch (_: Exception) {
                            "Doğrulama e-postası gönderilemedi (${it.code})."
                        }

                        runOnUiThread {
                            onResult(false, message)
                        }
                    }
                }
            }
        })
    }
    private fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/functions/v1/admin-users")
            .addHeader("apikey", BuildConfig.SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        supabaseClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    onResult(false, "Bağlantı hatası: ${e.message ?: e.javaClass.simpleName}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    runOnUiThread {
                        onResult(it.isSuccessful, body)
                    }
                }
            }
        })
    }
    private fun adminSignIn(email: String, password: String, onResult: (Boolean, String, String) -> Unit) {
        val json = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password")
            .addHeader("apikey", BuildConfig.SUPABASE_KEY)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        supabaseClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onResult(false, "", "Bağlantı hatası: ${e.message ?: e.javaClass.simpleName}") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) {
                        try {
                            val obj = JSONObject(body)
                            val accessToken = obj.optString("access_token", "")
                            if (accessToken.isNotBlank()) {
                                runOnUiThread { onResult(true, accessToken, "") }
                            } else {
                                runOnUiThread { onResult(false, "", "Oturum anahtarı alınamadı.") }
                            }
                        } catch (_: Exception) {
                            runOnUiThread { onResult(false, "", "Giriş cevabı okunamadı.") }
                        }
                    } else {
                        val message = try {
                            JSONObject(body).optString("error_description",
                                JSONObject(body).optString("msg", "Yönetici girişi başarısız."))
                        } catch (_: Exception) {
                            "Yönetici girişi başarısız (${it.code})."
                        }
                        runOnUiThread { onResult(false, "", message) }
                    }
                }
            }
        })
    }

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

        RemaxSyncScheduler.schedule(applicationContext)

        checkPermissions()

        val platformUtils = object : PlatformUtils {
            override fun scheduleReminder(dateTime: String, note: String): Boolean {
                return ReminderScheduler.schedule(this@MainActivity, dateTime, note)
            }

            override fun cancelReminder(dateTime: String, note: String) {
                ReminderScheduler.cancel(this@MainActivity, dateTime, note)
            }

            override fun saveRemaxUrl(url: String) {
                this@MainActivity
                    .getSharedPreferences("ceptemlak_background", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("remax_office_url", url)
                    .apply()
            }
            override fun openEmlakAsistan() {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "EmlakAsistan açılıyor",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                try {
                    val intent = Intent(
                        this@MainActivity,
                        EmlakAsistanActivity::class.java
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "HATA: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
            override fun openArsaTakip() {
                try {
                    val intent = Intent(this@MainActivity, ArsaTakipActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "ArsaTakip açılamadı: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }

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

            override fun checkAppAuthorization(phone: String, onResult: (Boolean, String, Boolean, Boolean, String) -> Unit) {
                this@MainActivity.checkSupabaseAuthorization(phone) { r -> onResult(r.authorized, r.fullName, r.isAdmin, r.canUseTools, r.error ?: "") }
            }

            override fun adminSignUp(email: String, password: String, onResult: (Boolean, String) -> Unit) {
                this@MainActivity.adminSignUp(email, password, onResult)
            }

            override fun adminUsersRequest(accessToken: String, requestJson: String, onResult: (Boolean, String) -> Unit) {
                this@MainActivity.adminUsersRequest(accessToken, requestJson, onResult)
            }

            override fun adminResendConfirmation(email: String, onResult: (Boolean, String) -> Unit) {
                this@MainActivity.adminResendConfirmation(email, onResult)
            }

            override fun adminSignIn(email: String, password: String, onResult: (Boolean, String, String) -> Unit) {
                this@MainActivity.adminSignIn(email, password, onResult)
            }

            override fun pickFile(onResult: (String?) -> Unit) {
                pickFileCallback = onResult
                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
        }

        setContent {
            val settings = remember { Settings() }

            var authorized by remember { mutableStateOf(false) }
            var checking by remember { mutableStateOf(false) }
            var name by remember {
                mutableStateOf(settings.getString("my_consultant_name", ""))
            }
            var phone by remember {
                mutableStateOf(settings.getString("my_consultant_phone", ""))
            }
            var errorMessage by remember { mutableStateOf("") }
            var showAdminLogin by remember { mutableStateOf(false) }
            var adminCode by remember { mutableStateOf("") }

            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Color(0xFF22A447))) {
                if (!authorized) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Spacer(Modifier.height(20.dp))

                            Text(
                                "EmlakCep",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Uygulamayı kullanabilmek için yetkili kullanıcı bilgilerinizi giriniz.",
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(28.dp))

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Ad Soyad") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Telefon Numarası") },
                                placeholder = { Text("05XXXXXXXXX") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (errorMessage.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (name.isBlank() || phone.isBlank()) {
                                        errorMessage =
                                            "Ad soyad ve telefon numarası gereklidir."
                                    } else {
                                        checking = true
                                        errorMessage = ""

                                        checkSupabaseAuthorization(phone) { result ->
                                            checking = false

                                            if (result.authorized) {
                                                val serverName =
                                                    result.fullName.ifBlank {
                                                        name.trim()
                                                    }

                                                name = serverName
                                                phone = normalizePhone(phone)

                                                settings.putString(
                                                    "my_consultant_name",
                                                    serverName
                                                )
                                                settings.putString(
                                                    "my_consultant_phone",
                                                    phone
                                                )
                                                settings.putBoolean(
                                                    "is_admin",
                                                    result.isAdmin
                                                )
                                                settings.putBoolean(
                                                    "can_use_tools",
                                                    result.canUseTools
                                                )

                                                authorized = true
                                            } else {
                                                settings.putBoolean(
                                                    "is_admin",
                                                    false
                                                )
                                                errorMessage =
                                                    result.error
                                                        ?: "Kullanım yetkiniz bulunmuyor."
                                            }
                                        }
                                    }
                                },
                                enabled = !checking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                if (checking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Yetki Kontrol Ediliyor...")
                                } else {
                                    Text(
                                        "GİRİŞ YAP",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            TextButton(
                                onClick = {
                                    showAdminLogin = !showAdminLogin
                                    adminCode = ""
                                    errorMessage = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (showAdminLogin) "YÖNETİCİ GİRİŞİNİ KAPAT"
                                    else "YÖNETİCİ GİRİŞİ",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (showAdminLogin) {
                                Spacer(Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = adminCode,
                                    onValueChange = { adminCode = it },
                                    label = { Text("Yönetici Kodu") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (adminCode.trim() == "enginadmin") {
                                            settings.putString(
                                                "my_consultant_name",
                                                "Engin Baysal"
                                            )
                                            settings.putBoolean(
                                                "is_admin",
                                                true
                                            )
                                            settings.putBoolean("can_use_tools", false)
                                            errorMessage = ""
                                            authorized = true
                                        } else {
                                            errorMessage = "Yönetici kodu hatalı."
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Text(
                                        "YÖNETİCİ OLARAK GİR",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val crmManager =
                        remember { LocalCRMManager(settings) }
                    val localPortfolioManager =
                        remember { LocalPortfolioManager(settings) }

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





