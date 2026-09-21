package com.example.sesliportfoyara.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sesliportfoyara.*
import kotlinx.coroutines.launch

private fun formatReminderDate(epochMillis: Long): String {
    var z = epochMillis / 86_400_000L + 719_468L
    val era = if (z >= 0) z / 146_097L else (z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    var year = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val day = doy - (153L * mp + 2L) / 5L + 1L
    val month = mp + if (mp < 10L) 3L else -9L
    if (month <= 2L) year += 1L

    return "${day.toString().padStart(2, '0')}.${month.toString().padStart(2, '0')}.$year"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CRMMainScreen(
    clients: List<Client>,
    allPortfolios: List<Portfolio>,
    onAddClient: () -> Unit,
    onClientClick: (Client) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Alıcılar, 1: Satıcılar
    val crmManager = LocalCRMManagerProvider.current
    val platformUtils = LocalPlatformUtils.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var newMatches by remember { mutableStateOf<List<Pair<Client, Portfolio>>>(emptyList()) }
    val generalReminders by crmManager.generalReminders.collectAsState()
    var showGeneralReminderDialog by remember { mutableStateOf(false) }
    var showReminderListDialog by remember { mutableStateOf(false) }
    var showGeneralDatePicker by remember { mutableStateOf(false) }
    var showGeneralTimePicker by remember { mutableStateOf(false) }
    var generalReminderDateTime by remember { mutableStateOf("") }
    var generalReminderDate by remember { mutableStateOf("") }
    var generalReminderHour by remember { mutableStateOf<Int?>(null) }
    var generalReminderMinute by remember { mutableStateOf<Int?>(null) }
    var generalReminderNote by remember { mutableStateOf("") }

    // Gorulmemis eslesmeleri her CRM acilisinda yeniden hesapla.
    // Bildirim, kullanici ilgili musteriyi acana kadar kaybolmaz.
    LaunchedEffect(allPortfolios, clients) {
        if (allPortfolios.isNotEmpty() && clients.isNotEmpty()) {
            newMatches = crmManager.getNewMatches(clients, allPortfolios)
            crmManager.markOfficePortfoliosKnown(allPortfolios)
        } else {
            newMatches = emptyList()
        }
    }
    if (showGeneralReminderDialog) {
        AlertDialog(
            onDismissRequest = { showGeneralReminderDialog = false },
            title = {
                Text("Genel Hatırlatma Ekle")
            },
            text = {
                Column {
                    OutlinedButton(
                        onClick = { showGeneralDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (generalReminderDate.isBlank()) "Tarih Seç" else generalReminderDate)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showGeneralTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = generalReminderDate.isNotBlank()
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val timeText = if (generalReminderHour != null && generalReminderMinute != null) {
                            "${generalReminderHour.toString().padStart(2, '0')}:${generalReminderMinute.toString().padStart(2, '0')}"
                        } else {
                            "Saat Seç"
                        }
                        Text(timeText)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = generalReminderNote,
                        onValueChange = { generalReminderNote = it },
                        label = { Text("Hatırlatma Notu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = generalReminderDate.isNotBlank() &&
                        generalReminderHour != null &&
                        generalReminderMinute != null,
                    onClick = {
                        generalReminderDateTime =
                            "$generalReminderDate ${generalReminderHour.toString().padStart(2, '0')}:${generalReminderMinute.toString().padStart(2, '0')}"
                        crmManager.addGeneralReminder(
                            CRMReminder(
                                dateTime = generalReminderDateTime,
                                note = generalReminderNote.trim()
                            )
                        )
                        platformUtils.scheduleReminder(
                            generalReminderDateTime,
                            generalReminderNote.trim()
                        )
                        generalReminderDateTime = ""
                        generalReminderDate = ""
                        generalReminderHour = null
                        generalReminderMinute = null
                        generalReminderNote = ""
                        showGeneralReminderDialog = false
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGeneralReminderDialog = false }
                ) {
                    Text("İptal")
                }
            }
        )
    }

    if (showGeneralDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showGeneralDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            generalReminderDate = formatReminderDate(millis)
                            showGeneralDatePicker = false
                            showGeneralTimePicker = true
                        }
                    }
                ) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeneralDatePicker = false }) {
                    Text("İptal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showGeneralTimePicker) {
        var tempHour by remember { mutableStateOf(generalReminderHour ?: 12) }
        var tempMinute by remember { mutableStateOf(generalReminderMinute ?: 0) }
        var hourMenuExpanded by remember { mutableStateOf(false) }
        var minuteMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGeneralTimePicker = false },
            title = { Text("Saat Seç") },
            text = {
                Column {
                    Text(
                        "Saati seçin",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { hourMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Saat  ${tempHour.toString().padStart(2, '0')}")
                        }

                        DropdownMenu(
                            expanded = hourMenuExpanded,
                            onDismissRequest = { hourMenuExpanded = false }
                        ) {
                            (0..23).forEach { hour ->
                                DropdownMenuItem(
                                    text = { Text(hour.toString().padStart(2, '0')) },
                                    onClick = {
                                        tempHour = hour
                                        hourMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { minuteMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dakika  ${tempMinute.toString().padStart(2, '0')}")
                        }

                        DropdownMenu(
                            expanded = minuteMenuExpanded,
                            onDismissRequest = { minuteMenuExpanded = false }
                        ) {
                            (0..59).forEach { minute ->
                                DropdownMenuItem(
                                    text = { Text(minute.toString().padStart(2, '0')) },
                                    onClick = {
                                        tempMinute = minute
                                        minuteMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Seçilen saat: ${tempHour.toString().padStart(2, '0')}:${tempMinute.toString().padStart(2, '0')}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        generalReminderHour = tempHour
                        generalReminderMinute = tempMinute
                        showGeneralTimePicker = false
                    }
                ) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeneralTimePicker = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showReminderListDialog) {
        val customerReminders = clients.flatMap { client ->
            client.reminders.map { reminder -> client to reminder }
        }

        AlertDialog(
            onDismissRequest = { showReminderListDialog = false },
            title = {
                Text("Hatırlatmalar")
            },
            text = {
                if (generalReminders.isEmpty() && customerReminders.isEmpty()) {
                    Text("Kayıtlı hatırlatma bulunmuyor.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(generalReminders.size) { index ->
                            val reminder = generalReminders[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF3E0)
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "GENEL",
                                        color = Color(0xFFE65100),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (reminder.dateTime.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(reminder.dateTime, fontWeight = FontWeight.Bold)
                                    }
                                    if (reminder.note.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(reminder.note)
                                    }
                                    Text(
                                        "Sil",
                                        color = Color.Red,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clickable { platformUtils.cancelReminder(reminder.dateTime, reminder.note); crmManager.deleteGeneralReminder(index) }
                                    )
                                }
                            }
                        }

                        items(customerReminders.size) { index ->
                            val (client, reminder) = customerReminders[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showReminderListDialog = false
                                        onClientClick(client)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        client.name,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (reminder.dateTime.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(reminder.dateTime, fontWeight = FontWeight.Bold)
                                    }
                                    if (reminder.note.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(reminder.note)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Müşteri detayını aç",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderListDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CRM", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                Button(
                    onClick = onAddClient,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22A447)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ekle", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (newMatches.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val client = newMatches.firstOrNull()?.first
                        if (client != null) {
                            crmManager.markMatchesSeen(newMatches)
                            newMatches = crmManager.getNewMatches(clients, allPortfolios)
                            onClientClick(client)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5EC)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, Color(0xFF22A447))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔔 YENİ PORTFÖY EŞLEŞMELERİ",
                                color = Color(0xFF137A34),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${newMatches.size} yeni müşteri-portföy eşleşmesi sizi bekliyor.",
                                color = Color(0xFF333333),
                                fontSize = 13.sp
                            )
                        }
                        Surface(
                            color = Color(0xFF22A447),
                            shape = CircleShape
                        ) {
                            Text(
                                newMatches.size.toString(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            val allReminders = clients.flatMap { client ->
                client.reminders.map { reminder -> client to reminder }
            }.sortedBy { it.second.dateTime }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReminderListDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFF9800))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            "HATIRLATMALAR",
                            color = Color(0xFFE65100),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFFF9800),
                            shape = CircleShape
                        ) {
                            Text(
                                (allReminders.size + generalReminders.size).toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            modifier = Modifier.size(26.dp).clickable {
                                showGeneralReminderDialog = true
                            },
                            color = Color(0xFFFF9800),
                            shape = CircleShape
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), RoundedCornerShape(8.dp)).padding(3.dp)
            ) {
                listOf("Alıcılar", "Satıcılar").forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedTab = index }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val filteredClients = if (selectedTab == 0) {
                clients.filter { it.type == ClientType.BUYER }
            } else {
                clients.filter { it.type == ClientType.SELLER }
            }

            if (filteredClients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz müşteri kaydı yok.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredClients) { client ->
                        val matchedCount = remember(client, allPortfolios) {
                            MatchingEngine.findMatches(client, allPortfolios).size
                        }
                        ClientItem(client, matchedCount, onClientClick)
                    }
                }
            }
        }
    }
}

@Composable
fun ClientItem(client: Client, matchedCount: Int, onClick: (Client) -> Unit) {
    val platformUtils = LocalPlatformUtils.current
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick(client) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Beyaz kart, gri zemin üzerinde parlar
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), // Biraz daha belirgin gölge
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(client.name, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (client.phone.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(client.phone, color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            // Arama Butonu
                            IconButton(
                                onClick = { platformUtils.openUri("tel:${client.phone.filter { it.isDigit() }}") },
                                modifier = Modifier.size(32.dp).background(Color(0xFF2196F3).copy(0.1f), CircleShape)
                            ) {
                                Icon(Icons.Default.Phone, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            // WhatsApp Butonu
                            IconButton(
                                onClick = { platformUtils.openUri("https://wa.me/${client.phone.filter { it.isDigit() }}") },
                                modifier = Modifier.size(32.dp).background(Color(0xFF25D366).copy(0.1f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    // Arama Rozeti
                    val badgeColor = if (client.dealType == "Kiralık") Color(0xFFE3F2FD) else Color(0xFFE8F5E9)
                    val textColor = if (client.dealType == "Kiralık") Color(0xFF1976D2) else Color(0xFF2E7D32)
                    Surface(color = badgeColor, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "${client.dealType} arıyor",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (matchedCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Surface(color = Color(0xFFC62828), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "🎯 $matchedCount Portföy",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                val loc = listOf(client.ilce, client.il).filter { it.isNotEmpty() }.joinToString(", ")
                Text(loc, color = Color.DarkGray, fontSize = 14.sp)
                
                Spacer(Modifier.width(16.dp))
                
                Icon(Icons.Default.Home, null, tint = Color(0xFF8D6E63), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(client.propertyType, color = Color.DarkGray, fontSize = 14.sp)
            }
            
            Spacer(Modifier.height(10.dp))
            
            val budgetLabel = if (client.dealType == "Kiralık") "Kira bütçesi" else "Bütçe"
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) { append("$budgetLabel: ") }
                    append(if (client.preferredPriceMax.isEmpty()) "-" else formatPrice(client.preferredPriceMax))
                },
                fontSize = 15.sp, color = Color.Black
            )
            
            if (client.note.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\"${client.note}\"",
                    color = Color.Gray, fontSize = 13.sp, fontStyle = FontStyle.Italic
                )
            }
            
            if (matchedCount > 0) {
                Spacer(Modifier.height(12.dp))
                Surface(color = Color(0xFFE0F2F1), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = Color(0xFF00796B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Uygun portföyleri gör", color = Color(0xFF00796B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddClientScreen(
    editingClient: Client?,
    onSave: (Client) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(editingClient?.name ?: "") }
    var phone by remember { mutableStateOf(editingClient?.phone ?: "") }
    var type by remember { mutableStateOf(editingClient?.type ?: ClientType.BUYER) }
    var dealType by remember { mutableStateOf(editingClient?.dealType ?: "Satılık") }
    var propertyType by remember { mutableStateOf(editingClient?.propertyType ?: "Daire") }
    var il by remember { mutableStateOf(editingClient?.il ?: "Manisa") }
    var ilce by remember { mutableStateOf(editingClient?.ilce ?: "Yunusemre") }
    var mahalle by remember { mutableStateOf(editingClient?.mahalle ?: "") }
    var price by remember { mutableStateOf(editingClient?.preferredPriceMax ?: "") }
    var rooms by remember { mutableStateOf(editingClient?.preferredRooms ?: "") }
    var note by remember { mutableStateOf(editingClient?.note ?: "") }
    var reminderDateTime by remember { mutableStateOf("") }
    var reminderNote by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf(editingClient?.reminders ?: emptyList()) }
    var sharedLink by remember { mutableStateOf("") }
    var sharedLinkNote by remember { mutableStateOf("") }
    var sharedLinks by remember { mutableStateOf(editingClient?.sharedPortfolioLinks ?: emptyList()) }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(scrollState).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
            Text(if (editingClient == null) "Yeni Müşteri" else "Müşteri Düzenle", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(ClientType.BUYER to "Alıcı", ClientType.SELLER to "Satıcı").forEach { (cType, label) ->
                val selected = type == cType
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f)), RoundedCornerShape(8.dp))
                        .clickable { type = cType }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground.copy(0.7f), fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("İŞLEM TÜRÜ", color = MaterialTheme.colorScheme.onBackground.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Satılık", "Kiralık").forEach { opt ->
                val selected = dealType == opt
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f)), RoundedCornerShape(8.dp))
                        .clickable { dealType = opt }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opt, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground.copy(0.7f), fontWeight = FontWeight.Bold)
                }
            }
        }

        CustomInputField("AD SOYAD", name) { name = it }
        CustomInputField("TELEFON", phone) { phone = it }
        var budgetField by remember {
            mutableStateOf(
                TextFieldValue(
                    text = price,
                    selection = TextRange(price.length)
                )
            )
        }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "BÜTÇE",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            TextField(
                value = budgetField,
                onValueChange = { newValue ->
                    val digits = newValue.text.filter { it.isDigit() }

                    val formatted = if (digits.isEmpty()) {
                        ""
                    } else {
                        digits.reversed()
                            .chunked(3)
                            .joinToString(".")
                            .reversed()
                    }

                    price = formatted

                    budgetField = TextFieldValue(
                        text = formatted,
                        selection = TextRange(formatted.length)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Text("EMLAK TÜRÜ", color = MaterialTheme.colorScheme.onBackground.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Daire", "Arsa", "Tarla", "Villa", "İşyeri").forEach { opt ->
                val selected = propertyType == opt
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f)), RoundedCornerShape(8.dp))
                        .clickable { propertyType = opt }.padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opt, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        CustomInputField("İL", il) { il = it }
        CustomInputField("İLÇE", ilce) { ilce = it }
        CustomInputField("MAHALLE", mahalle) { mahalle = it }

        if (propertyType == "Daire" || propertyType == "Villa") {
            CustomInputField("ODA SAYISI (ÖRN: 3+1)", rooms) { rooms = it }
        }

        CustomInputField("NOTLAR", note) { note = it }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "HATIRLATMALAR",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        reminders.forEachIndexed { index, reminder ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(reminder.dateTime, fontWeight = FontWeight.Bold)
                    if (reminder.note.isNotBlank()) Text(reminder.note)
                    Text(
                        "Kaldır",
                        color = Color.Red,
                        modifier = Modifier.padding(top = 6.dp).clickable {
                            reminders = reminders.filterIndexed { i, _ -> i != index }
                        }
                    )
                }
            }
        }

        CustomInputField("TARİH / SAAT (ÖRN: 25.09.2026 14:30)", reminderDateTime) {
            reminderDateTime = it
        }
        CustomInputField("HATIRLATMA NOTU", reminderNote) {
            reminderNote = it
        }

        OutlinedButton(
            onClick = {
                if (reminderDateTime.isNotBlank() || reminderNote.isNotBlank()) {
                    reminders = reminders + CRMReminder(
                        dateTime = reminderDateTime.trim(),
                        note = reminderNote.trim()
                    )
                    reminderDateTime = ""
                    reminderNote = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Hatırlatma Ekle")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "MÜŞTERİYLE PAYLAŞILAN PORTFÖYLER",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        sharedLinks.forEachIndexed { index, item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (item.note.isNotBlank()) {
                        Text(item.note, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        item.url,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Text(
                        "Kaldır",
                        color = Color.Red,
                        modifier = Modifier.padding(top = 6.dp).clickable {
                            sharedLinks = sharedLinks.filterIndexed { i, _ -> i != index }
                        }
                    )
                }
            }
        }

        CustomInputField("PORTFÖY LİNKİ", sharedLink) {
            sharedLink = it
        }
        CustomInputField("AÇIKLAMA (İSTEĞE BAĞLI)", sharedLinkNote) {
            sharedLinkNote = it
        }

        OutlinedButton(
            onClick = {
                if (sharedLink.isNotBlank()) {
                    sharedLinks = sharedLinks + SharedPortfolioLink(
                        url = sharedLink.trim(),
                        note = sharedLinkNote.trim()
                    )
                    sharedLink = ""
                    sharedLinkNote = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Link Ekle")
        }

        Spacer(modifier = Modifier.height(40.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editingClient != null) {
                val crmManager = LocalCRMManagerProvider.current
                Button(
                    onClick = { 
                        crmManager.deleteClient(editingClient.id)
                        onCancel()
                    },
                    modifier = Modifier.weight(0.5f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red, modifier = Modifier.size(20.dp))
                }
            }
            
            Button(
    onClick = onCancel,
    modifier = Modifier.weight(1f).height(56.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B36))
) {
    Text(
        "Vazgeç",
        color = Color.White,
        fontWeight = FontWeight.Bold
    )
}

            Button(
                onClick = {
                    if (name.isNotEmpty()) {
                        onSave(Client(
                            id = editingClient?.id ?: "",
                            name = name,
                            phone = phone,
                            type = type,
                            dealType = dealType,
                            propertyType = propertyType,
                            il = il,
                            ilce = ilce,
                            mahalle = mahalle,
                            preferredPriceMax = price,
                            preferredRooms = rooms,
                            note = note,
                            reminders = reminders,
                            sharedPortfolioLinks = sharedLinks,
                            createdAt = editingClient?.createdAt ?: Clock.now()
                        ))
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2B36))
            ) {
                Text("Kaydet", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun ClientDetailScreen(
    client: Client,
    allPortfolios: List<Portfolio>,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val platformUtils = LocalPlatformUtils.current
    val matchedPortfolios = remember(client, allPortfolios) {
        MatchingEngine.findMatches(client, allPortfolios)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Column {
                        Text(
                            "Müşteri Detayı",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            client.name,
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Düzenle",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text(
                "MÜŞTERİ BİLGİLERİ",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        client.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            client.phone,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )

                        if (client.phone.isNotBlank()) {
                            Spacer(Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    platformUtils.openUri(
                                        "tel:${client.phone.filter { it.isDigit() }}"
                                    )
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        Color(0xFF2196F3).copy(alpha = 0.15f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Ara",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    platformUtils.openUri(
                                        "https://wa.me/${client.phone.filter { it.isDigit() }}"
                                    )
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        Color(0xFF25D366).copy(alpha = 0.15f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Message,
                                    contentDescription = "WhatsApp",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "${client.dealType} • ${client.propertyType}",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )

                    val locationParts = listOf(
                        client.il,
                        client.ilce,
                        client.mahalle
                    ).filter { it.isNotBlank() }

                    if (locationParts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Konum: ${locationParts.joinToString(" / ")}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    }

                    if (client.preferredRooms.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Oda: ${client.preferredRooms}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    }

                    if (client.preferredPriceMax.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Maksimum bütçe: ${client.preferredPriceMax}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    }

                    if (client.note.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Notlar",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            client.note,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                "HATIRLATMALAR (${client.reminders.size})",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            if (client.reminders.isEmpty()) {
                Text(
                    "Bu müşteri için hatırlatma bulunmuyor.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                client.reminders.forEach { reminder ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (reminder.dateTime.isNotBlank()) {
                                Text(
                                    reminder.dateTime,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            if (reminder.note.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    reminder.note,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "SUNULAN PORTFÖYLER (${client.sharedPortfolioLinks.size})",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            if (client.sharedPortfolioLinks.isEmpty()) {
                Text(
                    "Bu müşteriye henüz portföy linki eklenmemiş.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                client.sharedPortfolioLinks.forEach { link ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                if (link.url.isNotBlank()) {
                                    platformUtils.openUri(link.url)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (link.note.isNotBlank()) {
                                Text(
                                    link.note,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                            Text(
                                link.url,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (client.type == ClientType.BUYER) {
            item {
                Text(
                    "EŞLEŞEN PORTFÖYLER (${matchedPortfolios.size})",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )

                if (matchedPortfolios.isEmpty()) {
                    Text(
                        "Şu anda uygun portföy bulunamadı.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            items(matchedPortfolios) { portfolio ->
                PortfolioItem(
                    portfolio,
                    onDelete = null,
                    onEdit = null
                )
            }
        }
    }
}
