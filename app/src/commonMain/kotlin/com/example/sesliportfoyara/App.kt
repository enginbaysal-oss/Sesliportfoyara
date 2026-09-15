package com.example.sesliportfoyara

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sesliportfoyara.ui.CRMMainScreen
import com.example.sesliportfoyara.ui.AddClientScreen
import com.example.sesliportfoyara.ui.ClientDetailScreen
import com.example.sesliportfoyara.ui.theme.SesliportfoyaraTheme
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.russhwolf.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Portfolio(
    val id: String = "",
    val title: String = "",
    val price: String = "",
    val rooms: String = "",
    val area: String = "",
    val location: String = "",
    val consultantName: String = "",
    val consultantPhone: String = "",
    val ownerName: String = "",
    val ownerPhone: String = "",
    val type: String = "Satılık",
    val propertyType: String = "Daire", // Daire, Arsa, Tarla, Villa, İşyeri
    val features: List<String> = emptyList(),
    val imageUrl: String = "",
    val link: String = "",
    val createdAt: Long = 0
)

@Serializable
data class ConsultantInfo(
    val name: String = "",
    val phone: String = ""
)

val LocalConsultantInfo = staticCompositionLocalOf { ConsultantInfo() }
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState") }

sealed class Screen(val title: String) {
    object VoiceSearch : Screen("Sesli Ara")
    object AddPortfolio : Screen("Portföy Ekle")
    object MyPortfolio : Screen("Portföyüm")
    object ProfileSetup : Screen("Profil Kurulumu")
    object CRM : Screen("Müşterilerim")
    object AddClient : Screen("Müşteri Ekle")
    object ClientDetails : Screen("Müşteri Detayı")
}

@Composable
fun App() {
    val dbManager = LocalDatabaseManager.current
    val settings = remember { Settings() }
    val crmManager = LocalCRMManagerProvider.current
    val localPortfolioManager = LocalPortfolioManagerProvider.current
    val remaxService = remember { RemaxService() }
    val snackbarHostState = remember { SnackbarHostState() }

    // Uygulamanın hatırladığı danışman bilgileri (Sizin kimliğiniz)
    var myName by remember { mutableStateOf(settings.getString("my_consultant_name", "")) }
    var myPhone by remember { mutableStateOf(settings.getString("my_consultant_phone", "")) }
    var remaxUrl by remember { mutableStateOf(settings.getString("remax_office_url", "")) }
    var isAdmin by remember { mutableStateOf(settings.getBoolean("is_admin", false)) }

    // Eğer bilgiler boşsa, başlangıç ekranını profil kurulumu yapıyoruz
    var currentScreen by remember {
        mutableStateOf<Screen>(if (myName.isEmpty() || myPhone.isEmpty()) Screen.ProfileSetup else Screen.VoiceSearch)
    }

    // GECE 00:00'DA OTOMATİK SENKRONİZASYON
    LaunchedEffect(remaxUrl) {
        if (remaxUrl.isNotBlank()) {
            // NOT: Açılışta otomatik senkronizasyon devre dışı bırakıldı.
            // Web (wasmJs) tarafında CORS engeli nedeniyle bu çağrı askıda kalıp
            // tüm ekranı kilitleyebiliyordu. Artık kullanıcı "İçe Aktar" butonuyla
            // manuel olarak tetikliyor. Gerekirse (proxy/CORS çözümü sonrası)
            // aşağıdaki satırın başındaki // işaretini kaldırıp tekrar aktif edebilirsiniz.
            // remaxService.syncWithFirebase(remaxUrl, dbManager)

            while (true) {
                // Türkiye saatiyle (UTC+3) gece 00:00'ı hesapla
                val now = Clock.now()
                val millisInDay = 24 * 60 * 60 * 1000L
                // Basit bir yaklaşımla gece yarısına kalan süreyi bul
                val currentMillisInDay = (now + (3 * 3600_000)) % millisInDay // UTC+3 ayarı
                var delayToMidnight = millisInDay - currentMillisInDay

                // Eğer çok yakınsa (1 dakikadan az), bir sonraki günü bekle
                if (delayToMidnight < 60_000) delayToMidnight += millisInDay

                println("🔄 Gece 00:00 senkronizasyonu için bekleniyor: ${delayToMidnight / 1000 / 60} dakika")
                delay(delayToMidnight)

                println("🌙 Saat 00:00: Otomatik portföy güncellemesi başlatılıyor...")
                remaxService.syncWithFirebase(remaxUrl, dbManager)

                delay(300_000) // 5 dakika bekle ki aynı gün içinde tekrar tetiklenmesin
            }
        }
    }

    CompositionLocalProvider(
        LocalConsultantInfo provides ConsultantInfo(myName, myPhone),
        LocalSnackbarHostState provides snackbarHostState,
        LocalRemaxServiceProvider provides remaxService
    ) {
        SesliportfoyaraTheme(darkTheme = false) {
            val officePortfolios = remember { mutableStateListOf<Portfolio>() }
            val localPortfolios by localPortfolioManager.portfolios.collectAsState()
            val clients by crmManager.clients.collectAsState()

            val allPortfolios by remember {
                derivedStateOf { officePortfolios + localPortfolios }
            }

            var editingPortfolio by remember { mutableStateOf<Portfolio?>(null) }
            var isEditingLocal by remember { mutableStateOf(false) }
            var selectedClient by remember { mutableStateOf<Client?>(null) }

            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                dbManager.getPortfolios().collectLatest { list ->
                    officePortfolios.clear()
                    officePortfolios.addAll(list.sortedByDescending { it.createdAt })
                }
            }

            Scaffold(
                topBar = {
                    if (currentScreen != Screen.ProfileSetup && currentScreen != Screen.AddClient && currentScreen != Screen.ClientDetails) {
                        HeaderSection()
                    }
                },
                bottomBar = {
                    if (currentScreen != Screen.ProfileSetup && currentScreen != Screen.AddClient && currentScreen != Screen.ClientDetails) {
                        TabNavigation(currentScreen) {
                            currentScreen = it
                            if (it != Screen.AddPortfolio) {
                                editingPortfolio = null
                                isEditingLocal = false
                            }
                            if (it != Screen.CRM) selectedClient = null
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentScreen) {
                        Screen.ProfileSetup -> ProfileSetupScreen(
                            initialName = myName,
                            initialPhone = myPhone,
                            initialUrl = remaxUrl
                        ) { name, phone, url, adminSecret ->
                            settings.putString("my_consultant_name", name)
                            settings.putString("my_consultant_phone", phone)
                            settings.putString("remax_office_url", url)

                            // Gizli şifreyi kontrol et (Şifre: adminengin)
                            val adminStatus = adminSecret == "adminengin"
                            settings.putBoolean("is_admin", adminStatus)

                            myName = name
                            myPhone = phone
                            remaxUrl = url
                            isAdmin = adminStatus
                            currentScreen = Screen.VoiceSearch
                        }
                        Screen.VoiceSearch -> VoiceSearchScreen(officePortfolios) { p ->
                            scope.launch {
                                try {
                                    val officeCopy = p.copy(
                                        id = "",
                                        createdAt = Clock.now()
                                    )
                                    val newId = dbManager.addPortfolio(officeCopy)
                                    if (newId != null) {
                                        snackbarHostState.showSnackbar("✅ Ofise kopyalandı. Yerel kopyanız duruyor.")
                                    } else {
                                        snackbarHostState.showSnackbar("❌ Ofise kopyalanamadı (Yetki veya Ağ hatası).")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    snackbarHostState.showSnackbar("❌ Kopyalama hatası: ${e.message}")
                                }
                            }
                        }
                        Screen.AddPortfolio -> AddPortfolioScreen(editingPortfolio, myName, myPhone, isAdmin) { p, saveLocally ->
                            val wasEditing = editingPortfolio != null
                            val wasEditingLocal = isEditingLocal

                            scope.launch {
                                try {
                                    if (wasEditing) {
                                        if (wasEditingLocal) {
                                            localPortfolioManager.updatePortfolio(p)
                                            snackbarHostState.showSnackbar("✅ Yerel portföy güncellendi.")
                                        } else {
                                            val ok = dbManager.updatePortfolio(p)
                                            if (ok) snackbarHostState.showSnackbar("✅ Ofis portföyü güncellendi.")
                                            else snackbarHostState.showSnackbar("❌ Güncelleme başarısız (Ağ hatası).")
                                        }
                                    } else {
                                        val newP = p.copy(createdAt = Clock.now())
                                        if (saveLocally) {
                                            localPortfolioManager.addPortfolio(newP)
                                            snackbarHostState.showSnackbar("✅ Yerel portföy eklendi.")
                                        } else {
                                            val newId = dbManager.addPortfolio(newP)
                                            if (newId != null) snackbarHostState.showSnackbar("✅ Ofis portföyü eklendi.")
                                            else snackbarHostState.showSnackbar("❌ Ofise eklenemedi (Ağ hatası).")
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    snackbarHostState.showSnackbar("❌ Bir hata oluştu.")
                                }
                            }
                            editingPortfolio = null
                            isEditingLocal = false
                            currentScreen = Screen.MyPortfolio
                        }
                        Screen.MyPortfolio -> MyPortfolioScreen(
                            officePortfolios = officePortfolios,
                            localPortfolios = localPortfolios,
                            onDeleteOffice = { p ->
                                scope.launch {
                                    val ok = dbManager.deletePortfolio(p.id)
                                    if (ok) snackbarHostState.showSnackbar("✅ Ofis portföyü silindi.")
                                    else snackbarHostState.showSnackbar("❌ Silme başarısız (Ağ hatası).")
                                }
                            },
                            onDeleteLocal = { p ->
                                localPortfolioManager.deletePortfolio(p.id)
                                scope.launch { snackbarHostState.showSnackbar("✅ Yerel portföy silindi.") }
                            },
                            onClearOffice = {
                                // Ekranın anında temizlendiğini görmek için asenkron isteğin cevabını beklemeden yerel listeyi sıfırlayalım
                                officePortfolios.clear()
                                scope.launch {
                                    val ok = dbManager.clearAllPortfolios()
                                    if (ok) snackbarHostState.showSnackbar("✅ Tüm ofis portföyleri silindi.")
                                    else snackbarHostState.showSnackbar("ℹ️ Veritabanı sıfırlandı.")
                                }
                            },
                            onClearLocal = {
                                localPortfolioManager.clearAllPortfolios()
                                scope.launch { snackbarHostState.showSnackbar("✅ Tüm yerel portföyler silindi.") }
                            },
                            onEditOffice = {
                                editingPortfolio = it
                                isEditingLocal = false
                                currentScreen = Screen.AddPortfolio
                            },
                            onEditLocal = {
                                editingPortfolio = it
                                isEditingLocal = true
                                currentScreen = Screen.AddPortfolio
                            },
                            onPublishLocal = { p ->
                                scope.launch {
                                    try {
                                        println("🛡️ Yerel liste korunuyor. Mevcut adet: ${localPortfolios.size}")

                                        // Sadece Firebase'e gönderiyoruz.
                                        // Yerel veri tabanına (localPortfolioManager) dair HİÇBİR işlem yapmıyoruz.
                                        val officeCopy = p.copy(
                                            id = "",
                                            createdAt = Clock.now()
                                        )
                                        val newId = dbManager.addPortfolio(officeCopy)

                                        if (newId != null) {
                                            snackbarHostState.showSnackbar("✅ Ofise kopyalandı. Yerel kopyanız duruyor.")
                                            println("🛡️ Kopyalama bitti. Yerel adet: ${localPortfolios.size}")
                                        } else {
                                            snackbarHostState.showSnackbar("❌ Ofise kopyalanamadı (Yetki veya Ağ hatası).")
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        snackbarHostState.showSnackbar("❌ Kopyalama hatası: ${e.message}")
                                    }
                                }
                            },
                            onEditProfile = {
                                currentScreen = Screen.ProfileSetup
                            },
                            onImportRemax = { url ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("⌛ Portföyler ofise aktarılıyor...")
                                    // syncWithFirebase artık kaç tane yeni ilan eklendiğini dönüyor
                                    val count = remaxService.syncWithFirebase(url, dbManager)
                                    if (count > 0) {
                                        snackbarHostState.showSnackbar("✅ $count yeni portföy başarıyla aktarıldı.")
                                    } else {
                                        snackbarHostState.showSnackbar("ℹ️ Yeni portföy bulunamadı veya hepsi zaten mevcut.")
                                    }
                                }
                            },
                            currentName = myName,
                            currentPhone = myPhone,
                            isAdmin = isAdmin
                        )
                        Screen.CRM -> CRMMainScreen(
                            clients = clients,
                            allPortfolios = allPortfolios,
                            onAddClient = {
                                selectedClient = null
                                currentScreen = Screen.AddClient
                            },
                            onClientClick = {
                                selectedClient = it
                                currentScreen = Screen.ClientDetails
                            }
                        )
                        Screen.AddClient -> AddClientScreen(
                            editingClient = selectedClient,
                            onSave = { client ->
                                if (selectedClient == null) {
                                    crmManager.addClient(client)
                                } else {
                                    crmManager.updateClient(client)
                                }
                                currentScreen = Screen.CRM
                            },
                            onCancel = {
                                currentScreen = Screen.CRM
                            }
                        )
                        Screen.ClientDetails -> selectedClient?.let { client ->
                            ClientDetailScreen(
                                client = client,
                                allPortfolios = allPortfolios,
                                onEdit = {
                                    currentScreen = Screen.AddClient
                                },
                                onBack = {
                                    currentScreen = Screen.CRM
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(
    initialName: String = "",
    initialPhone: String = "",
    initialUrl: String = "",
    onComplete: (String, String, String, String) -> Unit
) {
    var nameValue by remember { mutableStateOf(TextFieldValue(initialName)) }
    var phoneValue by remember { mutableStateOf(TextFieldValue(initialPhone)) }
    var remaxUrl by remember { mutableStateOf(initialUrl) }
    var adminSecret by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Danışman Profili", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Otomatik portföy çekme için RE/MAX ofis linkinizi girebilirsiniz.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        CustomTextFieldValueInput("Adınız Soyadınız", nameValue) { nameValue = it }
        CustomTextFieldValueInput("Telefon Numaranız", phoneValue) { phoneValue = it }
        CustomInputField("RE/MAX Ofis Linki (Otomatik Çekme İçin)", remaxUrl) { remaxUrl = it }
        CustomInputField("Admin Şifresi (Opsiyonel)", adminSecret) { adminSecret = it }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                if (nameValue.text.isNotBlank() && phoneValue.text.isNotBlank()) {
                    onComplete(nameValue.text.trim(), phoneValue.text.trim(), remaxUrl.trim(), adminSecret.trim())
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
            enabled = nameValue.text.isNotBlank() && phoneValue.text.isNotBlank()
        ) {
            Text("Bilgileri Kaydet", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

object Clock {
    fun now() = getCurrentTimeMillis()
}

@Composable
fun PremiumLogo(modifier: Modifier = Modifier) {
    val goldColor = Color(0xFFc9a15a)
    Canvas(modifier = modifier.size(52.dp)) {
        val w = size.width
        val h = size.height

        // 1. Kalkan (Shield) Formu
        val shieldPath = Path().apply {
            moveTo(w * 0.12f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.88f, h * 0.25f)
            lineTo(w * 0.88f, h * 0.6f)
            quadraticTo(w * 0.88f, h * 0.85f, w * 0.5f, h * 0.95f)
            quadraticTo(w * 0.12f, h * 0.85f, w * 0.12f, h * 0.6f)
            close()
        }
        drawPath(path = shieldPath, color = goldColor, style = Stroke(width = 2.dp.toPx()))

        // 2. Ses Dalgası (Üstte)
        val wavePath = Path().apply {
            moveTo(w * 0.38f, h * 0.35f)
            lineTo(w * 0.44f, h * 0.35f)
            lineTo(w * 0.47f, h * 0.28f)
            lineTo(w * 0.53f, h * 0.42f)
            lineTo(w * 0.56f, h * 0.35f)
            lineTo(w * 0.62f, h * 0.35f)
        }
        drawPath(path = wavePath, color = goldColor, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))

        // 3. Stilize SP Monogramı (Merkezde)
        // S Çizimi
        drawArc(
            color = goldColor,
            startAngle = 180f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(w * 0.3f, h * 0.42f),
            size = Size(w * 0.18f, h * 0.12f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = goldColor,
            startAngle = 0f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(w * 0.3f, h * 0.54f),
            size = Size(w * 0.18f, h * 0.12f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // P Çizimi
        drawLine(
            color = goldColor,
            start = Offset(w * 0.54f, h * 0.42f),
            end = Offset(w * 0.54f, h * 0.7f),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawArc(
            color = goldColor,
            startAngle = 270f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.54f, h * 0.42f),
            size = Size(w * 0.2f, h * 0.16f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. CRM Yükseliş Grafiği (Altta)
        // Grafik Taban Çizgisi
        drawLine(color = goldColor.copy(alpha = 0.5f), start = Offset(w * 0.35f, h * 0.82f), end = Offset(w * 0.65f, h * 0.82f), strokeWidth = 1.dp.toPx())

        // Barlar
        drawRect(color = goldColor, topLeft = Offset(w * 0.42f, h * 0.76f), size = Size(w * 0.05f, h * 0.06f))
        drawRect(color = goldColor, topLeft = Offset(w * 0.50f, h * 0.70f), size = Size(w * 0.05f, h * 0.12f))
        drawRect(color = goldColor, topLeft = Offset(w * 0.58f, h * 0.65f), size = Size(w * 0.05f, h * 0.17f))

        // Yükseliş Oku (Arrow)
        val arrowPath = Path().apply {
            moveTo(w * 0.38f, h * 0.82f)
            lineTo(w * 0.68f, h * 0.62f)
            // Ok başı
            moveTo(w * 0.60f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.70f)
        }
        drawPath(path = arrowPath, color = goldColor, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun HeaderSection() {
    val crmManager = LocalCRMManagerProvider.current
    val localPortfolioManager = LocalPortfolioManagerProvider.current
    val remaxService = LocalRemaxServiceProvider.current
    val platformUtils = LocalPlatformUtils.current
    val isSyncing by remaxService.isSyncing.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PremiumLogo()

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sesli Portföy CRM v1.2.4",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                if (isSyncing) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "PREMIUM REAL ESTATE MANAGEMENT",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
        }

        // ANA SAYFAYA TAŞINAN YEDEKLEME BUTONLARI
        val snackbarHostState = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val fullBackup = crmManager.exportFullBackup(localPortfolioManager.getAllPortfolios())
                platformUtils.saveFile("sesliportfoy_yedek.json", fullBackup) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) "✅ Yedekleme başarılı!" else "❌ Yedekleme başarısız.")
                    }
                }
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Download, "Yedekle", tint = Color(0xFFc9a15a), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {
                platformUtils.pickFile { json ->
                    if (json != null) {
                        val ok = crmManager.importFullBackup(json, localPortfolioManager)
                        scope.launch {
                            snackbarHostState.showSnackbar(if (ok) "✅ Yedek başarıyla yüklendi!" else "❌ Hatalı yedek dosyası.")
                        }
                    }
                }
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Upload, "Yükle", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun TabNavigation(currentScreen: Screen, onNavigate: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.VoiceSearch, Icons.Default.Mic, Color(0xFFFFC107)),
        Triple(Screen.AddPortfolio, Icons.Default.AddCircle, Color(0xFF4CAF50)),
        Triple(Screen.CRM, Icons.Default.Groups, Color(0xFF2196F3)),
        Triple(Screen.MyPortfolio, Icons.Default.Inventory, Color(0xFFFF9800))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 16.dp, top = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)), RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (screen, icon, color) ->
            val selected = (currentScreen == screen)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onNavigate(screen) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = icon,
                        contentDescription = screen.title,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = screen.title,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceSearchScreen(portfolios: List<Portfolio>, onPublish: (Portfolio) -> Unit) {
    val scrollState = rememberScrollState()
    var searchResults by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var lastQuery by remember { mutableStateOf("") }
    val platformUtils = LocalPlatformUtils.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val performSearch: (String) -> Unit = { query ->
        lastQuery = query
        searchResults = PortfolioSearchEngine.search(query, portfolios)
        focusManager.clearFocus()
    }

    // Portföy listesi arkada güncellenirse arama sonuçlarını da tazele
    LaunchedEffect(portfolios) {
        if (lastQuery.isNotBlank()) {
            searchResults = PortfolioSearchEngine.search(lastQuery, portfolios)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                SonarAnimation()
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)), CircleShape)
                    .clickable {
                        if (isListening) {
                            platformUtils.stopVoiceRecognition()
                            isListening = false
                        } else {
                            platformUtils.startVoiceRecognition(
                                onResult = { result ->
                                    isListening = false
                                    performSearch(result)
                                },
                                onError = { error ->
                                    isListening = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar(error)
                                    }
                                }
                            )
                            isListening = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Mic",
                    modifier = Modifier.size(40.dp),
                    tint = if (isListening) Color.White else MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = if (isListening) "Sizi dinliyorum..." else lastQuery.ifEmpty { "Mikrofona dokunup portföyü tarif edin" },
            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Örn: \"Bahçelievler 3+1 asansörlü daire\"",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "${portfolios.size} portföy içinde aranıyor",
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        var searchText by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("ya da yazarak arayın...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(searchText) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { performSearch(searchText) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Ara", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (lastQuery.isNotEmpty()) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = if (searchResults.isNotEmpty()) "${searchResults.size} eşleşme bulundu" else "Eşleşme bulunamadı",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            searchResults.forEach { portfolio ->
                PortfolioItem(
                    portfolio = portfolio,
                    onDelete = null,
                    onEdit = null,
                    onPublish = if (portfolio.id.startsWith("local_")) onPublish else null
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SonarAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val scales = listOf(
        infiniteTransition.animateFloat(0.6f, 1.4f, infiniteRepeatable(tween(2200, easing = LinearEasing))),
        infiniteTransition.animateFloat(0.6f, 1.4f, infiniteRepeatable(tween(2200, delayMillis = 600, easing = LinearEasing))),
        infiniteTransition.animateFloat(0.6f, 1.4f, infiniteRepeatable(tween(2200, delayMillis = 1200, easing = LinearEasing)))
    )
    val opacities = listOf(
        infiniteTransition.animateFloat(0.6f, 0f, infiniteRepeatable(tween(2200, easing = LinearEasing))),
        infiniteTransition.animateFloat(0.6f, 0f, infiniteRepeatable(tween(2200, delayMillis = 600, easing = LinearEasing))),
        infiniteTransition.animateFloat(0.6f, 0f, infiniteRepeatable(tween(2200, delayMillis = 1200, easing = LinearEasing)))
    )

    Box(contentAlignment = Alignment.Center) {
        scales.forEachIndexed { i, scale ->
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = opacities[i].value
                    }
                    .border(1.dp, Color(0xFFFFC107), CircleShape)
            )
        }
    }
}

@Composable
fun AddPortfolioScreen(
    editingPortfolio: Portfolio?,
    defaultName: String,
    defaultPhone: String,
    isAdmin: Boolean,
    onAdd: (Portfolio, Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    var title by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.title ?: "") }
    var price by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.price ?: "") }
    var rooms by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.rooms ?: "") }
    var area by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.area ?: "") }
    var location by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.location ?: "") }
    var consultantName by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.consultantName ?: defaultName) }
    var consultantPhone by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.consultantPhone ?: defaultPhone) }
    var ownerName by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.ownerName ?: "") }
    var ownerPhone by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.ownerPhone ?: "") }
    var type by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.type ?: "Satılık") }
    var propertyType by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.propertyType ?: "Daire") }
    var features by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.features?.joinToString(", ") ?: "") }
    var link by remember(editingPortfolio) { mutableStateOf(editingPortfolio?.link ?: "") }
    var saveLocally by remember { mutableStateOf(editingPortfolio?.id?.startsWith("local_") ?: true) }

    // Danışman bilgileri sadece admin tarafından veya yeni eklerken (default olarak) değiştirilebilir.
    // Mevcut bir ilanı düzenleyen normal kullanıcı kendi adını değiştiremez.
    val canEditConsultant = isAdmin || editingPortfolio == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Text(
            if (editingPortfolio != null) "Portföyü Düzenle" else "Yeni Portföy Ekle",
            color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (editingPortfolio == null) {
            Text("Portföy Nerede Saklansın?", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to "Sadece Benim (Yerel)", false to "Herkesle Paylaş (Ofis)").forEach { (isLocal, label) ->
                    val selected = saveLocally == isLocal
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)), RoundedCornerShape(8.dp))
                            .clickable { saveLocally = isLocal }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        CustomInputField("İlan Başlığı", title) { title = it }

        Text("İlan Tipi", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Satılık", "Kiralık").forEach { option ->
                val selected = (type == option)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)), RoundedCornerShape(8.dp))
                        .clickable { type = option }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Emlak Türü", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Daire", "Arsa", "Tarla", "Villa", "İşyeri").forEach { option ->
                val selected = (propertyType == option)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)), RoundedCornerShape(20.dp))
                        .clickable { propertyType = option }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f).copy(0.2f))
        Text("MÜLK SAHİBİ BİLGİLERİ (Özel)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { CustomInputField("Sahibi Adı", ownerName) { ownerName = it } }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { CustomInputField("Sahibi Telefon", ownerPhone) { ownerPhone = it } }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f).copy(0.2f))
        Text("DANIŞMAN BİLGİLERİ", color = if (canEditConsultant) Color(0xFF2196F3) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                CustomInputField("Danışman", consultantName, enabled = canEditConsultant) { consultantName = it }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                CustomInputField("Danışman Tel", consultantPhone, enabled = canEditConsultant) { consultantPhone = it }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { CustomInputField("Fiyat (TL)", price) { price = it } }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { CustomInputField("Oda Sayısı", rooms) { rooms = it } }
        }

        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { CustomInputField("Metrekare", area) { area = it } }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { CustomInputField("Konum", location) { location = it } }
        }
        CustomInputField("Özellikler (virgülle ayırın)", features) { features = it }
        CustomInputField("İlan Linki", link) { link = it }

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                if (title.isNotEmpty()) {
                    onAdd(Portfolio(
                        id = editingPortfolio?.id ?: "",
                        title = title,
                        price = price,
                        rooms = rooms,
                        area = area,
                        location = location,
                        consultantName = consultantName,
                        consultantPhone = consultantPhone,
                        ownerName = ownerName,
                        ownerPhone = ownerPhone,
                        type = type,
                        propertyType = propertyType,
                        features = features.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        link = link,
                        createdAt = editingPortfolio?.createdAt ?: 0L
                    ), saveLocally)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
        ) {
            Text(
                if (editingPortfolio != null) "Değişiklikleri Kaydet" else "Portföyü Kaydet",
                color = Color.Black, fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun CustomTextFieldValueInput(label: String, value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@Composable
fun CustomInputField(label: String, value: String, enabled: Boolean = true, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
fun MyPortfolioScreen(
    officePortfolios: List<Portfolio>,
    localPortfolios: List<Portfolio>,
    onDeleteOffice: (Portfolio) -> Unit,
    onDeleteLocal: (Portfolio) -> Unit,
    onClearOffice: () -> Unit,
    onClearLocal: () -> Unit,
    onEditOffice: (Portfolio) -> Unit,
    onEditLocal: (Portfolio) -> Unit,
    onPublishLocal: (Portfolio) -> Unit,
    onEditProfile: () -> Unit,
    onImportRemax: (String) -> Unit,
    currentName: String,
    currentPhone: String,
    isAdmin: Boolean
) {
    var selectedTab by remember { mutableStateOf(1) } // Ekran açıldığında doğrudan "Ofis (1)" sekmesini aktif yapalım (Görselde Ofis seçili)
    var portfolioToDelete by remember { mutableStateOf<Portfolio?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var remaxUrl by remember { mutableStateOf("https://remax.com.tr/tr/ofis/detay/ilyada-3") }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if (selectedTab == 0) "Tüm Yerel Portföyleri Sil?" else "Tüm Ofis Portföylerini Sil?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Seçili sekmedeki tüm ilanlar silinecek. Bu işlem geri alınamaz.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedTab == 0) onClearLocal() else onClearOffice()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Hepsini Sil", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("RE/MAX'tan İçe Aktar", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text("Ofis veya arama sayfası linkini girin:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    CustomInputField("URL", remaxUrl) { remaxUrl = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportRemax(remaxUrl)
                        showImportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                ) {
                    Text("Aktar", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("İptal", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (portfolioToDelete != null) {
        AlertDialog(
            onDismissRequest = { portfolioToDelete = null },
            title = { Text("Portföyü Sil?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("${portfolioToDelete?.title} başlıklı ilan silinecek. Bu işlem geri alınamaz.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = {
                        portfolioToDelete?.let {
                            if (selectedTab == 0) onDeleteLocal(it) else onDeleteOffice(it)
                        }
                        portfolioToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Sil", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { portfolioToDelete = null }) {
                    Text("İptal", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portföylerim", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.background(Color(0xFFFF5252), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("ADMIN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onEditProfile, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // İŞLEM BUTONLARI (İçe Aktar ve Hepsini Sil)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("İçe Aktar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }

            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Temizle", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TABLAR
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(4.dp)) {
            val tabs = listOf(
                "Benim (${localPortfolios.size} adet)",
                "Ofis (${officePortfolios.size} adet)"
            )
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = index }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentList = if (selectedTab == 0) localPortfolios else officePortfolios

        if (currentList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bu bölümde henüz portföy yok.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f) // Parent Column içindeki alanı doğru şekilde doldurup kaydırmayı bağımsızlaştırır
            ) {
                items(currentList) { portfolio ->
                    val cleanMyPhone = currentPhone.filter { it.isDigit() }
                    val cleanConsultantPhone = portfolio.consultantPhone.filter { it.isDigit() }
                    val isOwner = portfolio.consultantName.equals(currentName, ignoreCase = true) && cleanConsultantPhone == cleanMyPhone && cleanMyPhone.isNotEmpty()

                    // YETKİ: Lokal ise her zaman düzenlenir. Ofis ise sahibi veya admin ise düzenlenir.
                    val canManage = (selectedTab == 0) || isOwner || isAdmin || portfolio.consultantName.isEmpty()

                    PortfolioItem(
                        portfolio = portfolio,
                        onDelete = if (canManage) { { portfolioToDelete = it } } else null,
                        onEdit = if (canManage) (if (selectedTab == 0) onEditLocal else onEditOffice) else null,
                        onPublish = if (selectedTab == 0) onPublishLocal else null
                    )
                }
            }
        }
    }
}

fun borderStroke(width: Dp, color: Color) = BorderStroke(width, color)

fun formatPrice(p: String): String {
    if (p.isEmpty()) return "Fiyat belirtilmedi"
    // Sadece rakamları al
    val digitsOnly = p.filter { it.isDigit() }
    if (digitsOnly.isEmpty()) return p

    val n = digitsOnly.toLongOrNull() ?: return p
    val formatted = n.toString().reversed().chunked(3).joinToString(".").reversed()

    // Orijinal string'de para birimi simgesi varsa onu korumaya çalış (genelde sonundadır)
    return when {
        p.contains("$") -> "$formatted $"
        p.contains("€") -> "$formatted €"
        p.contains("£") -> "$formatted £"
        else -> "$formatted TL"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioItem(
    portfolio: Portfolio,
    onDelete: ((Portfolio) -> Unit)?,
    onEdit: ((Portfolio) -> Unit)?,
    onPublish: ((Portfolio) -> Unit)? = null
) {
    val currentConsultant = LocalConsultantInfo.current

    // Akıllı Eşleşme Mantığı: Telefonun son 10 hanesini karşılaştır (ülke kodu/sıfır farkını önlemek için)
    val cleanMyPhone = currentConsultant.phone.filter { it.isDigit() }.let { if (it.length > 10) it.takeLast(10) else it }
    val cleanConsultantPhone = portfolio.consultantPhone.filter { it.isDigit() }.let { if (it.length > 10) it.takeLast(10) else it }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
    ) {
        Column {
            Column(modifier = Modifier.padding(16.dp)) {
                // ÜST BÖLÜM: FOTOĞRAF VE SAĞINDA TEMEL BİLGİLER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // SOL TARAF: DİKDÖRTGEN GÜZEL KAPAK FOTOĞRAFI
                    if (portfolio.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = portfolio.imageUrl,
                            contentDescription = "Portföy Görseli",
                            modifier = Modifier
                                .size(width = 140.dp, height = 100.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // SAĞ TARAF: ETİKETLER VE FİYAT
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(color = if (portfolio.type == "Satılık") MaterialTheme.colorScheme.primary else Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp)) {
                                    Text(portfolio.type, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                                Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                    Text(portfolio.propertyType, color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                                val isLocal = portfolio.id.startsWith("local_")
                                Surface(
                                    color = if (isLocal) Color(0xFF2196F3).copy(0.15f) else Color(0xFFFF5252).copy(0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, if (isLocal) Color(0xFF2196F3) else Color(0xFFFF5252))
                                ) {
                                    Text(
                                        text = if (isLocal) "YEREL" else "OFİS",
                                        color = if (isLocal) Color(0xFF2196F3) else Color(0xFFFF5252),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp) // Dikey ve yatay padding değerlerini diğer etiketlerle (2.dp) eşitleyerek taşmayı ve kaymayı önledik
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = formatPrice(portfolio.price), 
                            color = MaterialTheme.colorScheme.primary, 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // BAŞLIK (TAM GÖRÜNÜM - LİMİTSİZ SATIR)
                Text(
                    text = portfolio.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // KONUM VE BİLGİLER
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.6.dp))
                    Text(portfolio.location, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.weight(1f))

                    Spacer(Modifier.width(12.dp))
                    Surface(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), shape = RoundedCornerShape(4.dp)) {
                        Text(portfolio.rooms, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                // MÜLK SAHİBİ BİLGİSİ (Varsa Her Zaman Göster)
                if (portfolio.ownerName.isNotEmpty() || portfolio.ownerPhone.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.6.dp))
                        Text(
                            text = "Mülk Sahibi: ${portfolio.ownerName.ifEmpty { "Belirtilmedi" }}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // DANIŞMAN BİLGİSİ (Varsa Her Zaman Göster)
                if (portfolio.consultantName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f).copy(0.6f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.6.dp))
                        Text(
                            text = "Danışman: ${portfolio.consultantName}${if(portfolio.consultantPhone.isNotEmpty()) " (${portfolio.consultantPhone})" else ""}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }

                val platformUtils = LocalPlatformUtils.current
                Spacer(modifier = Modifier.height(18.dp))

                // BUTONLAR
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sahibini Ara
                    if (portfolio.ownerPhone.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sahibini Ara:", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, modifier = Modifier.width(75.dp))
                            IconButton(
                                onClick = { platformUtils.openUri("tel:${portfolio.ownerPhone.filter { it.isDigit() }}") },
                                modifier = Modifier.size(36.dp).background(Color(0xFF2196F3).copy(0.18f), CircleShape)
                            ) {
                                Icon(Icons.Default.Phone, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { platformUtils.openUri("https://wa.me/${portfolio.ownerPhone.filter { it.isDigit() }}") },
                                modifier = Modifier.size(36.dp).background(Color(0xFF25D366).copy(0.18f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                            }
                            Text(portfolio.ownerPhone, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 11.sp)
                        }
                    }

                    // Danışmanı Ara (Yeni Eklenen Row)
                    if (portfolio.consultantPhone.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Danışmanı Ara:", color = Color(0xFF2196F3), fontSize = 10.sp, modifier = Modifier.width(75.dp))
                            IconButton(
                                onClick = { platformUtils.openUri("tel:${portfolio.consultantPhone.filter { it.isDigit() }}") },
                                modifier = Modifier.size(36.dp).background(Color(0xFF2196F3).copy(0.18f), CircleShape)
                            ) {
                                Icon(Icons.Default.Phone, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { platformUtils.openUri("https://wa.me/${portfolio.consultantPhone.filter { it.isDigit() }}") },
                                modifier = Modifier.size(36.dp).background(Color(0xFF25D366).copy(0.18f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                            }
                            Text(portfolio.consultantPhone, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 11.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (portfolio.link.isNotEmpty()) {
                            Button(
                                onClick = { platformUtils.openUri(portfolio.link) },
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("İlanı Aç", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (onEdit != null) {
                            IconButton(onClick = { onEdit(portfolio) }, modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), CircleShape)) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                            }
                        }

                        if (onPublish != null) {
                            IconButton(onClick = { onPublish(portfolio) }, modifier = Modifier.size(42.dp).background(Color(0xFFFFC107).copy(0.15f), CircleShape)) {
                                Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }

                        if (onDelete != null) {
                            IconButton(onClick = { onDelete(portfolio) }, modifier = Modifier.size(42.dp).background(Color.Red.copy(0.1f), CircleShape)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
