package com.example.sesliportfoyara.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sesliportfoyara.*
import kotlinx.coroutines.launch

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

    // Yeni eşleşme bildirimi kontrolü
    LaunchedEffect(allPortfolios.size) {
        if (allPortfolios.isNotEmpty()) {
            val anyNewMatch = clients.any { client -> 
                MatchingEngine.findMatches(client, allPortfolios).isNotEmpty()
            }
            if (anyNewMatch && clients.isNotEmpty()) {
                snackbarHostState.showSnackbar("🎯 Kriterlere uygun yeni portföy eşleşmeleri mevcut!")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentColor = Color.White
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Müşterilerim", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                
                Button(
                    onClick = onAddClient,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("Ekle", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp)).padding(4.dp)
            ) {
                listOf("Alıcılar", "Satıcılar").forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF2C2C2C) else Color.Transparent)
                            .clickable { selectedTab = index }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, color = if (selected) Color(0xFFFFC107) else Color.Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick(client) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)), // Açık renk kart
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(client.name, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (client.phone.isNotEmpty()) {
                        Text(client.phone, color = Color.Gray, fontSize = 14.sp)
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

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(if (editingClient == null) "Yeni Müşteri" else "Müşteri Düzenle", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(ClientType.BUYER to "Alıcı", ClientType.SELLER to "Satıcı").forEach { (cType, label) ->
                val selected = type == cType
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFFE8F5E9) else Color(0xFF2C2C2C))
                        .clickable { type = cType }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) Color(0xFF2E7D32) else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("İŞLEM TÜRÜ", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Satılık", "Kiralık").forEach { opt ->
                val selected = dealType == opt
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFFE8F5E9) else Color(0xFF2C2C2C))
                        .clickable { dealType = opt }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opt, color = if (selected) Color(0xFF2E7D32) else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        CustomInputField("AD SOYAD", name) { name = it }
        CustomInputField("TELEFON", phone) { phone = it }
        CustomInputField("BÜTÇE", price) { price = it }

        Text("EMLAK TÜRÜ", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Daire", "Arsa", "Tarla", "Villa", "İşyeri").forEach { opt ->
                val selected = propertyType == opt
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFFFFC107) else Color(0xFF2C2C2C))
                        .clickable { propertyType = opt }.padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opt, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    Text("Sil", color = Color.Red)
                }
            }
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = borderStroke(1.dp, Color.Gray)
            ) {
                Text("Vazgeç", color = Color.White)
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
    val matchedPortfolios = remember(client, allPortfolios) {
        MatchingEngine.findMatches(client, allPortfolios)
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Müşteri Detayı", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFFC107)) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2126)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(client.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(client.phone, color = Color(0xFFFFC107), fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.background(Color(0xFFFFC107).copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("${client.dealType} ${client.propertyType}", color = Color(0xFFFFC107), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                val locationParts = listOf(client.il, client.ilce, client.mahalle).filter { it.isNotEmpty() }
                if (locationParts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(locationParts.joinToString(" / "), color = Color.Gray, fontSize = 13.sp)
                    }
                }

                if (client.note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Not: ${client.note}", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (client.type == ClientType.BUYER) {
            Text("Otomatik Eşleşen Portföyler (${matchedPortfolios.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            if (matchedPortfolios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Uygun portföy bulunamadı.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(matchedPortfolios) { portfolio ->
                        PortfolioItem(portfolio, onDelete = null, onEdit = null)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Satıcı müşteriler için portföy eşleşmesi yakında eklenecek.", color = Color.Gray)
            }
        }
    }
}
