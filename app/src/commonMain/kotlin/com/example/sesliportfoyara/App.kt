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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sesliportfoyara.ui.CRMMainScreen
import com.example.sesliportfoyara.ui.AddClientScreen
import com.example.sesliportfoyara.ui.ClientDetailScreen
import com.example.sesliportfoyara.ui.theme.SesliportfoyaraTheme
import com.russhwolf.settings.Settings
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
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Uygulamanın hatırladığı danışman bilgileri (Sizin kimliğiniz)
    var myName by remember { mutableStateOf(settings.getString("my_consultant_name", "")) }
    var myPhone by remember { mutableStateOf(settings.getString("my_consultant_phone", "")) }
    var isAdmin by remember { mutableStateOf(settings.getBoolean("is_admin", false)) }
    
    // Eğer bilgiler boşsa, başlangıç ekranını profil kurulumu yapıyoruz
    var currentScreen by remember { 
        mutableStateOf<Screen>(if (myName.isEmpty() || myPhone.isEmpty()) Screen.ProfileSetup else Screen.VoiceSearch) 
    }
    
    CompositionLocalProvider(
        LocalConsultantInfo provides ConsultantInfo(myName, myPhone),
        LocalSnackbarHostState provides snackbarHostState
    ) {
        SesliportfoyaraTheme(darkTheme = true) {
        val officePortfolios = remember { mutableStateListOf<Portfolio>() }
        val localPortfolios by localPortfolioManager.portfolios.collectAsState()
        val clients by crmManager.clients.collectAsState()
        
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
                    Screen.ProfileSetup -> ProfileSetupScreen { name, phone, adminSecret ->
                        settings.putString("my_consultant_name", name)
                        settings.putString("my_consultant_phone", phone)
                        
                        // Gizli şifreyi kontrol et (Şifre: enginadmin)
                        val adminStatus = adminSecret == "enginadmin"
                        settings.putBoolean("is_admin", adminStatus)
                        
                        myName = name
                        myPhone = phone
                        isAdmin = adminStatus
                        currentScreen = Screen.VoiceSearch
                    }
                    Screen.VoiceSearch -> VoiceSearchScreen(officePortfolios + localPortfolios)
                    Screen.AddPortfolio -> AddPortfolioScreen(editingPortfolio, myName, myPhone) { p, saveLocally ->
                        val wasEditing = editingPortfolio != null
                        val wasEditingLocal = isEditingLocal
                        
                        scope.launch {
                            try {
                                if (wasEditing) {
                                    if (wasEditingLocal) {
                                        localPortfolioManager.updatePortfolio(p)
                                    } else {
                                        dbManager.updatePortfolio(p)
                                    }
                                } else {
                                    val newP = p.copy(createdAt = Clock.now())
                                    if (saveLocally) {
                                        localPortfolioManager.addPortfolio(newP)
                                    } else {
                                        dbManager.addPortfolio(newP)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        editingPortfolio = null
                        isEditingLocal = false
                        currentScreen = Screen.MyPortfolio
                    }
                    Screen.MyPortfolio -> MyPortfolioScreen(
                        officePortfolios = officePortfolios,
                        localPortfolios = localPortfolios,
                        onDeleteOffice = { p -> scope.launch { dbManager.deletePortfolio(p.id) } },
                        onDeleteLocal = { p -> localPortfolioManager.deletePortfolio(p.id) },
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
                        onEditProfile = {
                            currentScreen = Screen.ProfileSetup
                        },
                        currentName = myName,
                        currentPhone = myPhone,
                        isAdmin = isAdmin
                    )
                    Screen.CRM -> CRMMainScreen(
                        clients = clients,
                        allPortfolios = officePortfolios + localPortfolios,
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
                            allPortfolios = officePortfolios + localPortfolios,
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
fun ProfileSetupScreen(onComplete: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var adminSecret by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFF2C2C2C), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = Color(0xFFFFC107), modifier = Modifier.size(40.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Hoş Geldiniz", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Devam etmek için lütfen danışman bilgilerinizi girin. Bu bilgiler ilanlarınızın size ait olduğunu belirlemek için kullanılacaktır.", 
            color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        CustomInputField("Adınız Soyadınız", name) { name = it }
        CustomInputField("Telefon Numaranız", phone) { phone = it }
        CustomInputField("Admin Şifresi (Opsiyonel)", adminSecret) { adminSecret = it }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    onComplete(name.trim(), phone.trim(), adminSecret.trim())
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
            enabled = name.isNotBlank() && phone.isNotBlank()
        ) {
            Text("Uygulamaya Başla", color = Color.Black, fontWeight = FontWeight.Bold)
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
    val platformUtils = LocalPlatformUtils.current

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
            Text(
                text = "Sesli Portföy CRM v1.2.1",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "PREMIUM REAL ESTATE MANAGEMENT",
                color = Color(0xFFc9a15a),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
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
                Icon(Icons.Default.Upload, "Yükle", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun TabNavigation(currentScreen: Screen, onNavigate: (Screen) -> Unit) {
    val screens = listOf(Screen.VoiceSearch, Screen.AddPortfolio, Screen.CRM, Screen.MyPortfolio)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp, top = 8.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        screens.forEach { screen ->
            val selected = (currentScreen == screen)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF2C2C2C) else Color.Transparent)
                    .clickable { onNavigate(screen) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = screen.title,
                    color = if (selected) Color(0xFFFFC107) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun VoiceSearchScreen(portfolios: List<Portfolio>) {
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
                    .background(if (isListening) Color(0xFFFFC107) else Color(0xFF2C2C2C), CircleShape)
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
                    tint = if (isListening) Color.Black else Color(0xFFFFC107)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = if (isListening) "Sizi dinliyorum..." else lastQuery.ifEmpty { "Mikrofona dokunup portföyü tarif edin" },
            color = if (isListening) Color(0xFFFFC107) else Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Örn: \"Bahçelievler 3+1 asansörlü daire\"",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Text(
            text = "${portfolios.size} portföy içinde aranıyor",
            color = Color(0xFFFFC107).copy(alpha = 0.6f),
            fontSize = 11.sp,
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
                placeholder = { Text("ya da yazarak arayın...", color = Color.Gray) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(searchText) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2C2C2C),
                    unfocusedContainerColor = Color(0xFF2C2C2C),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { performSearch(searchText) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Ara", color = Color(0xFFFFC107))
            }
        }

        if (lastQuery.isNotEmpty()) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = if (searchResults.isNotEmpty()) "${searchResults.size} eşleşme bulundu" else "Eşleşme bulunamadı",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            searchResults.forEach { portfolio ->
                PortfolioItem(portfolio, onDelete = null, onEdit = null)
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
fun AddPortfolioScreen(editingPortfolio: Portfolio?, defaultName: String, defaultPhone: String, onAdd: (Portfolio, Boolean) -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Text(
            if (editingPortfolio != null) "Portföyü Düzenle" else "Yeni Portföy Ekle", 
            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (editingPortfolio == null) {
            Text("Portföy Nerede Saklansın?", color = Color.Gray, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to "Sadece Benim (Yerel)", false to "Herkesle Paylaş (Ofis)").forEach { (isLocal, label) ->
                    val selected = saveLocally == isLocal
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFFFFC107) else Color(0xFF2C2C2C))
                            .clickable { saveLocally = isLocal }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        CustomInputField("İlan Başlığı", title) { title = it }

        Text("İlan Tipi", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
                        .background(if (selected) Color(0xFFFFC107) else Color(0xFF2C2C2C))
                        .clickable { type = option }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Emlak Türü", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                        .background(if (selected) Color(0xFFFFC107) else Color(0xFF2C2C2C))
                        .clickable { propertyType = option }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, color = if (selected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Gray.copy(0.2f))
        Text("MÜLK SAHİBİ BİLGİLERİ (Özel)", color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { CustomInputField("Sahibi Adı", ownerName) { ownerName = it } }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { CustomInputField("Sahibi Telefon", ownerPhone) { ownerPhone = it } }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.Gray.copy(0.2f))
        Text("DANIŞMAN BİLGİLERİ", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { CustomInputField("Danışman", consultantName) { consultantName = it } }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { CustomInputField("Danışman Tel", consultantPhone) { consultantPhone = it } }
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
fun CustomInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2C2C2C),
                unfocusedContainerColor = Color(0xFF2C2C2C),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
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
    onEditOffice: (Portfolio) -> Unit,
    onEditLocal: (Portfolio) -> Unit,
    onEditProfile: () -> Unit,
    currentName: String,
    currentPhone: String,
    isAdmin: Boolean
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Benim (Lokal), 1: Ofis (Genel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portföylerim", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.background(Color(0xFFFF5252), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("ADMIN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onEditProfile, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Settings, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // TABLAR
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp)).padding(4.dp)) {
            val tabs = listOf(
                "Benim (${localPortfolios.size} adet)", 
                "Ofis (${officePortfolios.size} adet)"
            )
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Color(0xFF2C2C2C) else Color.Transparent)
                        .clickable { selectedTab = index }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = if (selected) Color(0xFFFFC107) else Color.Gray, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentList = if (selectedTab == 0) localPortfolios else officePortfolios
        
        if (currentList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bu bölümde henüz portföy yok.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(currentList) { portfolio ->
                    val cleanMyPhone = currentPhone.filter { it.isDigit() }
                    val cleanConsultantPhone = portfolio.consultantPhone.filter { it.isDigit() }
                    val isOwner = portfolio.consultantName.equals(currentName, ignoreCase = true) && cleanConsultantPhone == cleanMyPhone && cleanMyPhone.isNotEmpty()
                    
                    // YETKİ: Lokal ise her zaman düzenlenir. Ofis ise sahibi veya admin ise düzenlenir.
                    val canManage = (selectedTab == 0) || isOwner || isAdmin || portfolio.consultantName.isEmpty()
                    
                    PortfolioItem(
                        portfolio = portfolio, 
                        onDelete = if (canManage) (if (selectedTab == 0) onDeleteLocal else onDeleteOffice) else null, 
                        onEdit = if (canManage) (if (selectedTab == 0) onEditLocal else onEditOffice) else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioItem(portfolio: Portfolio, onDelete: ((Portfolio) -> Unit)?, onEdit: ((Portfolio) -> Unit)?) {
    val currentConsultant = LocalConsultantInfo.current
    
    // Akıllı Eşleşme Mantığı: Telefonun son 10 hanesini karşılaştır (ülke kodu/sıfır farkını önlemek için)
    val cleanMyPhone = currentConsultant.phone.filter { it.isDigit() }.let { if (it.length > 10) it.takeLast(10) else it }
    val cleanConsultantPhone = portfolio.consultantPhone.filter { it.isDigit() }.let { if (it.length > 10) it.takeLast(10) else it }
    
    // Portföyün bana ait olup olmadığını belirle:
    // 1. Yerel bir portföy mü? (ID'si local_ ile mi başlıyor?)
    // 2. Danışman ismi ve telefonu benimle uyuşuyor mu?
    val isMine = portfolio.id.startsWith("local_") || 
                (portfolio.consultantName.trim().equals(currentConsultant.name.trim(), ignoreCase = true) && 
                 cleanConsultantPhone == cleanMyPhone && cleanMyPhone.isNotEmpty())

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2126)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ÜST BÖLÜM: TİP VE FİYAT
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = if (portfolio.type == "Satılık") Color(0xFFFFC107) else Color(0xFF6FAE8C), shape = RoundedCornerShape(4.dp)) {
                        Text(portfolio.type, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color.White.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(portfolio.propertyType, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(text = formatPrice(portfolio.price), color = Color(0xFFFFC107), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // BAŞLIK (TAM GÖRÜNÜM - LİMİTSİZ SATIR)
            Text(
                text = portfolio.title, 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // KONUM VE BİLGİLER
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.6.dp))
                Text(portfolio.location, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
                
                Spacer(Modifier.width(12.dp))
                Surface(color = Color.White.copy(0.08f), shape = RoundedCornerShape(4.dp)) {
                    Text(portfolio.rooms, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            // DANIŞMAN / SAHİBİ (İhtiyaca göre akıllı gösterim)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = Color.Gray.copy(0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.6.dp))
                
                val contactName = if (isMine && portfolio.ownerName.isNotEmpty()) {
                    "Mülk Sahibi: ${portfolio.ownerName}" 
                } else if (portfolio.consultantName.isNotEmpty()) {
                    "Danışman: ${portfolio.consultantName}"
                } else {
                    "Sahibi: ${portfolio.ownerName}"
                }
                
                Text(contactName, color = Color.Gray, fontSize = 12.sp, fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal)
            }
            
            val platformUtils = LocalPlatformUtils.current
            Spacer(modifier = Modifier.height(18.dp))
            
            // BUTONLAR (Mülk sahibi benimse ona, değilse danışmana yönlendir)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val callPhone = if (isMine && portfolio.ownerPhone.isNotEmpty()) {
                    portfolio.ownerPhone
                } else if (portfolio.consultantPhone.isNotEmpty()) {
                    portfolio.consultantPhone
                } else {
                    portfolio.ownerPhone
                }
                
                if (callPhone.isNotEmpty()) {
                    IconButton(onClick = { platformUtils.openUri("tel:${callPhone.filter { it.isDigit() }}") }, modifier = Modifier.size(42.dp).background(Color(0xFF2196F3).copy(0.18f), CircleShape)) {
                        Icon(Icons.Default.Phone, null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { platformUtils.openUri("https://wa.me/${callPhone.filter { it.isDigit() }}") }, modifier = Modifier.size(42.dp).background(Color(0xFF25D366).copy(0.18f), CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                    }
                }
                
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
                    IconButton(onClick = { onEdit(portfolio) }, modifier = Modifier.size(42.dp).background(Color.Gray.copy(0.15f), CircleShape)) {
                        Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
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

fun borderStroke(width: Dp, color: Color) = BorderStroke(width, color)

fun formatPrice(p: String): String {
    if (p.isEmpty()) return "Fiyat belirtilmedi"
    val cleanP = p.replace(".", "").replace(",", "")
    val n = cleanP.toLongOrNull() ?: cleanP.toDoubleOrNull()?.toLong() ?: return p
    return n.toString().reversed().chunked(3).joinToString(".").reversed() + " TL"
}
