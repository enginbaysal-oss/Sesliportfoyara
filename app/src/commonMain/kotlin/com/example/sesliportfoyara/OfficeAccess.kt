package com.example.sesliportfoyara

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

private const val SUPABASE_URL = "https://jcjerwvibjetomqeelsy.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_tz0ZMLExOcLCDnGudSnS6A_ko8TD4Ig"

@Serializable
data class AuthUser(val id: String, val phone: String = "")

@Serializable
data class OtpSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    val user: AuthUser
)

@Serializable
data class OfficeSummary(val id: String, val name: String, val code: String)

@Serializable
data class OfficePermissions(
    @SerialName("voice_search") val voiceSearch: Boolean = true,
    @SerialName("office_portfolios") val officePortfolios: Boolean = true,
    @SerialName("portfolio_write") val portfolioWrite: Boolean = true,
    val crm: Boolean = true,
    val tapumatik: Boolean = true,
    @SerialName("document_assistant") val documentAssistant: Boolean = true,
    @SerialName("land_tracking") val landTracking: Boolean = true
)

@Serializable
data class OfficeMembership(
    val id: String,
    @SerialName("office_id") val officeId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val role: String,
    val status: String,
    val permissions: OfficePermissions = OfficePermissions()
)

@Serializable
private data class MembershipInsert(
    @SerialName("office_id") val officeId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val phone: String
)

@Serializable
data class MembershipUpdate(
    val status: String? = null,
    val role: String? = null,
    val permissions: OfficePermissions? = null
)

class OfficeAccessManager {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    private fun HttpRequestBuilder.supabaseHeaders(accessToken: String? = null) {
        header("apikey", SUPABASE_KEY)
        if (!accessToken.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $accessToken")
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        return when {
            digits.startsWith("90") && digits.length == 12 -> "+$digits"
            digits.startsWith("0") && digits.length == 11 -> "+90${digits.drop(1)}"
            digits.length == 10 -> "+90$digits"
            else -> phone.trim()
        }
    }

    suspend fun requestOtp(phone: String) {
        val response = client.post("$SUPABASE_URL/auth/v1/otp") {
            supabaseHeaders()
            contentType(ContentType.Application.Json)
            setBody("{\"phone\":\"${normalizePhone(phone)}\",\"create_user\":true}")
        }
        if (response.status.value !in 200..299) error(response.body<String>())
    }

    suspend fun verifyOtp(phone: String, code: String): OtpSession {
        val response = client.post("$SUPABASE_URL/auth/v1/verify") {
            supabaseHeaders()
            contentType(ContentType.Application.Json)
            setBody("{\"phone\":\"${normalizePhone(phone)}\",\"token\":\"${code.trim()}\",\"type\":\"sms\"}")
        }
        if (response.status.value !in 200..299) error(response.body<String>())
        return response.body()
    }

    suspend fun refreshSession(refreshToken: String): OtpSession {
        val response = client.post("$SUPABASE_URL/auth/v1/token") {
            supabaseHeaders()
            parameter("grant_type", "refresh_token")
            contentType(ContentType.Application.Json)
            setBody("{\"refresh_token\":\"$refreshToken\"}")
        }
        if (response.status.value !in 200..299) error(response.body<String>())
        return response.body()
    }

    suspend fun getMembership(accessToken: String, userId: String): OfficeMembership? {
        val response = client.get("$SUPABASE_URL/rest/v1/office_members") {
            supabaseHeaders(accessToken)
            parameter("user_id", "eq.$userId")
            parameter("select", "id,office_id,user_id,full_name,phone,role,status,permissions")
            parameter("limit", "1")
        }
        if (response.status.value !in 200..299) error(response.body<String>())
        return response.body<List<OfficeMembership>>().firstOrNull()
    }

    suspend fun joinOffice(accessToken: String, userId: String, code: String, fullName: String, phone: String): OfficeMembership {
        val officeResponse = client.get("$SUPABASE_URL/rest/v1/offices") {
            supabaseHeaders(accessToken)
            parameter("code", "eq.${code.trim().uppercase()}")
            parameter("is_active", "eq.true")
            parameter("select", "id,name,code")
            parameter("limit", "1")
        }
        if (officeResponse.status.value !in 200..299) error(officeResponse.body<String>())
        val office = officeResponse.body<List<OfficeSummary>>().firstOrNull()
            ?: error("Ofis kodu bulunamadı.")

        val response = client.post("$SUPABASE_URL/rest/v1/office_members") {
            supabaseHeaders(accessToken)
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(MembershipInsert(office.id, userId, fullName.trim(), normalizePhone(phone)))
        }
        if (response.status.value !in 200..299) error(response.body<String>())
        return getMembership(accessToken, userId)
            ?: response.body<List<OfficeMembership>>().first()
    }

    suspend fun listMembers(accessToken: String, officeId: String): List<OfficeMembership> {
        val response = client.get("$SUPABASE_URL/rest/v1/office_members") {
            supabaseHeaders(accessToken)
            parameter("office_id", "eq.$officeId")
            parameter("select", "id,office_id,user_id,full_name,phone,role,status,permissions")
            parameter("order", "full_name.asc")
        }
        if (response.status.value !in 200..299) error(response.body<String>())
        return response.body()
    }

    suspend fun updateMember(accessToken: String, memberId: String, update: MembershipUpdate) {
        val response = client.patch("$SUPABASE_URL/rest/v1/office_members") {
            supabaseHeaders(accessToken)
            parameter("id", "eq.$memberId")
            contentType(ContentType.Application.Json)
            setBody(update)
        }
        if (response.status.value !in 200..299) error(response.body<String>())
    }

    suspend fun approveMember(accessToken: String, memberId: String) =
        updateMember(accessToken, memberId, MembershipUpdate(status = "active"))

    suspend fun suspendMember(accessToken: String, memberId: String) =
        updateMember(accessToken, memberId, MembershipUpdate(status = "suspended"))

    suspend fun grantAll(accessToken: String, memberId: String) =
        updateMember(accessToken, memberId, MembershipUpdate(permissions = OfficePermissions()))
}

@Composable
fun OfficeAccessGate(content: @Composable (OfficeMembership, OfficeAccessManager, String) -> Unit) {
    val settings = remember { Settings() }
    val manager = remember { OfficeAccessManager() }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(settings.getString("office_access_token", "")) }
    var refreshToken by remember { mutableStateOf(settings.getString("office_refresh_token", "")) }
    var userId by remember { mutableStateOf(settings.getString("office_user_id", "")) }
    var verifiedPhone by remember { mutableStateOf(settings.getString("office_verified_phone", "")) }
    var membership by remember { mutableStateOf<OfficeMembership?>(null) }
    var loading by remember { mutableStateOf(token.isNotBlank()) }
    var errorText by remember { mutableStateOf("") }

    LaunchedEffect(token, userId) {
        if (token.isNotBlank() && userId.isNotBlank()) {
            loading = true
            runCatching { manager.getMembership(token, userId) }
                .recoverCatching {
                    if (refreshToken.isBlank()) throw it
                    val session = manager.refreshSession(refreshToken)
                    token = session.accessToken
                    refreshToken = session.refreshToken
                    userId = session.user.id
                    settings.putString("office_access_token", token)
                    settings.putString("office_refresh_token", refreshToken)
                    settings.putString("office_user_id", userId)
                    manager.getMembership(token, userId)
                }
                .onSuccess { membership = it }
                .onFailure {
                    errorText = "Oturum yenilenmeli: ${it.message.orEmpty()}"
                    token = ""
                    refreshToken = ""
                    userId = ""
                    settings.remove("office_access_token")
                    settings.remove("office_refresh_token")
                    settings.remove("office_user_id")
                }
            loading = false
        }
    }

    when {
        loading -> FullScreenMessage("EmlakCep hazırlanıyor…")
        token.isBlank() -> PhoneOtpScreen(
            manager = manager,
            errorText = errorText,
            onVerified = { session ->
                token = session.accessToken
                refreshToken = session.refreshToken
                userId = session.user.id
                settings.putString("office_access_token", token)
                settings.putString("office_refresh_token", session.refreshToken)
                settings.putString("office_user_id", userId)
                verifiedPhone = session.user.phone
                settings.putString("office_verified_phone", verifiedPhone)
                errorText = ""
            }
        )
        membership == null -> JoinOfficeScreen(errorText, verifiedPhone) { code, name, phone ->
            scope.launch {
                loading = true
                runCatching { manager.joinOffice(token, userId, code, name, phone) }
                    .onSuccess { membership = it; errorText = "" }
                    .onFailure { errorText = it.message ?: "Ofise katılım başarısız." }
                loading = false
            }
        }
        membership?.status == "pending" -> PendingApprovalScreen {
            scope.launch {
                loading = true
                membership = manager.getMembership(token, userId)
                loading = false
            }
        }
        membership?.status == "suspended" -> FullScreenMessage("EmlakCep erişiminiz ofis yöneticisi tarafından durduruldu.")
        else -> content(membership!!, manager, token)
    }
}

@Composable
private fun PhoneOtpScreen(manager: OfficeAccessManager, errorText: String, onVerified: (OtpSession) -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf(errorText) }

    AccessCard(Icons.Default.PhoneAndroid, "Telefonla Giriş", "Telefonunuza gönderilen doğrulama koduyla güvenli giriş yapın.") {
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefon numarası") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        if (codeSent) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(code, { code = it }, label = { Text("6 haneli SMS kodu") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        }
        if (localError.isNotBlank()) Text(localError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                busy = true
                runCatching {
                    if (!codeSent) manager.requestOtp(phone) else manager.verifyOtp(phone, code)
                }.onSuccess {
                    if (it is OtpSession) onVerified(it) else codeSent = true
                    localError = ""
                }.onFailure { localError = it.message ?: "İşlem başarısız." }
                busy = false
            }
        }, enabled = !busy && phone.isNotBlank() && (!codeSent || code.length >= 6), modifier = Modifier.fillMaxWidth()) {
            Text(if (codeSent) "Kodu Doğrula" else "SMS Kodu Gönder")
        }
    }
}

@Composable
private fun JoinOfficeScreen(errorText: String, verifiedPhone: String, onJoin: (String, String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember(verifiedPhone) { mutableStateOf(verifiedPhone) }
    AccessCard(Icons.Default.Business, "Ofise Katıl", "Yöneticinizin paylaştığı ofis kodunu girin.") {
        OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Ofis kodu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Ad soyad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Doğrulanmış telefon") }, singleLine = true,
            readOnly = verifiedPhone.isNotBlank(), modifier = Modifier.fillMaxWidth())
        if (errorText.isNotBlank()) Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Button(onClick = { onJoin(code, name, phone) }, enabled = code.length >= 4 && name.isNotBlank() && phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Katılım Talebi Gönder")
        }
    }
}

@Composable
private fun AccessCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF2FAF4)).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 460.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = Color(0xFF168A45), modifier = Modifier.size(44.dp))
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun FullScreenMessage(message: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF2FAF4)).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PendingApprovalScreen(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF2FAF4)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Katılım talebiniz ofis yöneticisinin onayını bekliyor.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRefresh) { Text("Durumu Kontrol Et") }
        }
    }
}

@Composable
fun OfficeAdminScreen(manager: OfficeAccessManager, token: String, officeId: String) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<OfficeMembership>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { manager.listMembers(token, officeId) }
                .onSuccess { members = it; error = "" }
                .onFailure { error = it.message ?: "Danışmanlar yüklenemedi." }
            loading = false
        }
    }
    LaunchedEffect(officeId) { refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, null, tint = Color(0xFF168A45), modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Ofis Yetkilendirme", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${members.size} kullanıcı", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    members.filter { it.status == "pending" }.forEach { manager.approveMember(token, it.id) }
                    refresh()
                }
            }) { Text("Bekleyenlerin Tümünü Onayla") }
            OutlinedButton(onClick = {
                scope.launch {
                    members.filter { it.status == "active" }.forEach { manager.grantAll(token, it.id) }
                    refresh()
                }
            }) { Text("Tüm Yetkileri Aç") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(members, key = { it.id }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(member.fullName, fontWeight = FontWeight.Bold)
                            Text("${member.phone} • ${member.role} • ${member.status}", fontSize = 12.sp)
                        }
                        if (member.role != "admin") {
                            if (member.status == "active") {
                                TextButton(onClick = { scope.launch { manager.suspendMember(token, member.id); refresh() } }) { Text("Durdur") }
                            } else {
                                Button(onClick = { scope.launch { manager.approveMember(token, member.id); refresh() } }) { Text("Onayla") }
                            }
                        }
                    }
                }
            }
        }
    }
}
