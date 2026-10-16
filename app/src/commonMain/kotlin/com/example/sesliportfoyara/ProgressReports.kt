package com.example.sesliportfoyara

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProgressActivity(val id: String, val date: Long, val kind: String, val note: String)
@Serializable
data class ProgressMetric(val site: String, val views: Int? = null, val favorites: Int? = null)
@Serializable
data class ProgressArchive(val id: String, val start: Long, val end: Long, val metrics: List<ProgressMetric>, val html: String, val evaluation: String = "")

private fun buildReportWhatsAppText(portfolio: Portfolio, report: ProgressArchive, advisor: ProgressAdvisor): String {
    val metricsStr = report.metrics.joinToString("\n") { m ->
        "• ${m.site}: Görüntülenme: ${m.views ?: 0}, Favori: ${m.favorites ?: 0}"
    }
    return "*HAFTALIK İLERLEME RAPORU*\n\n" +
        "🏠 *İlan:* ${portfolio.title}\n" +
        "📅 *Dönem:* ${formatTurkishDateMillis(report.start)} – ${formatTurkishDateMillis(report.end)}\n\n" +
        "📊 *İLAN İSTATİSTİKLERİ:*\n$metricsStr\n\n" +
        (if (report.evaluation.length > 0) "📝 *DEĞERLENDİRME:*\n${report.evaluation}\n\n" else "") +
        "👤 *Danışman:* ${advisor.name} (${advisor.phone})\n" +
        "🏢 *Ofis:* ${advisor.office}"
}
@Serializable
data class ProgressData(
    val trackingStart: Long = 0,
    val activities: List<ProgressActivity> = emptyList(),
    val reports: List<ProgressArchive> = emptyList(),
    val draftMetrics: List<ProgressMetric> = emptyList(),
    val draftEvaluation: String = ""
)
@Serializable
data class ProgressAdvisor(val name: String = "", val phone: String = "", val office: String = "")

private val progressJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val progressSites = listOf("sahibinden.com", "remax.com", "hepsiemlak.com")
private fun progressDay(): Long = (Clock.now() + 3 * 60 * 60 * 1000L) / DAY_MS * DAY_MS
private fun progressKey(phone: String, id: String) = "progress_v1_${phone.filter { it.isDigit() }}_$id"
private fun progressLoad(settings: Settings, key: String): ProgressData =
    progressJson.decodeFromString<ProgressData>(settings.getString(key, "{}"))
private fun progressEscape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
private fun progressDue(data: ProgressData): Boolean {
    if (data.trackingStart <= 0 || progressDay() < data.trackingStart + 7 * DAY_MS) return false
    val last = data.reports.maxOfOrNull { it.end } ?: return true
    val weekday = ((last / DAY_MS + 3) % 7).toInt() // Monday = 0
    return progressDay() >= last + (7 - weekday) * DAY_MS
}

@Composable
fun PortfolioProgressButton(portfolio: Portfolio, name: String, phone: String, office: String) {
    val settings = remember { Settings() }
    val dbManager = LocalDatabaseManager.current
    val scope = rememberCoroutineScope()
    val key = progressKey(phone, portfolio.id)
    var open by remember(key) { mutableStateOf(false) }
    var data by remember(key) { mutableStateOf<ProgressData?>(try { progressLoad(settings, key) } catch (_: Exception) { null }) }

    LaunchedEffect(portfolio.id) {
        try {
            val remote = dbManager.getProgressData(portfolio.id)
            if (remote != null) {
                settings.putString(key, progressJson.encodeToString(remote))
                data = remote
            }
        } catch (_: Exception) {}
    }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(if (data?.let { progressDue(it) } == true) "Haftalık rapor zamanı • Hareketler / Rapor" else "Hareketler / Haftalık Rapor")
    }
    if (open) {
        PortfolioProgressDialog(portfolio, name, phone, office, settings, dbManager, scope, key, onChanged = { data = it }, onClose = { open = false })
    }
}

@Composable
private fun PortfolioProgressDialog(
    portfolio: Portfolio, name: String, phone: String, office: String,
    settings: Settings, dbManager: DatabaseManager, scope: kotlinx.coroutines.CoroutineScope,
    key: String, onChanged: (ProgressData) -> Unit, onClose: () -> Unit
) {
    val platform = LocalPlatformUtils.current
    var loadError by remember(key) { mutableStateOf("") }
    var data by remember(key) { mutableStateOf(try { progressLoad(settings, key) } catch (_: Exception) {
        loadError = "Kayıtlar okunamadı. Mevcut veriler korunuyor; yeni kayıt yapılamaz."
        ProgressData()
    }) }
    var notice by remember { mutableStateOf("") }
    fun persist(next: ProgressData): Boolean {
        if (loadError.isNotBlank()) return false
        return try {
            settings.putString(key, progressJson.encodeToString(next))
            data = next; onChanged(next); notice = "Kaydedildi."
            scope.launch {
                try { dbManager.saveProgressData(portfolio.id, next) } catch (_: Exception) {}
            }
            true
        } catch (_: Exception) { notice = "Kayıt yapılamadı. Depolama alanını kontrol edin; mevcut kayıt korundu."; false }
    }
    val advisorKey = "progress_advisor_v1_${phone.filter { it.isDigit() }}"
    var advisor by remember(advisorKey) { mutableStateOf(try {
        progressJson.decodeFromString<ProgressAdvisor>(settings.getString(advisorKey, "{}"))
    } catch (_: Exception) { ProgressAdvisor() }.let { it.copy(name = it.name.ifBlank { name }, phone = it.phone.ifBlank { phone }, office = it.office.ifBlank { office }) }) }
    var tab by remember { mutableStateOf(0) }
    var startText by remember { mutableStateOf(formatTurkishDateMillis(data.trackingStart.takeIf { it > 0 } ?: progressDay())) }
    var activityDate by remember { mutableStateOf(formatTurkishDateMillis(progressDay())) }
    var kind by remember { mutableStateOf("Telefon görüşmesi") }
    var note by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf(formatTurkishDateMillis(data.reports.maxOfOrNull { it.end + DAY_MS } ?: data.trackingStart.takeIf { it > 0 } ?: progressDay() - 6 * DAY_MS)) }
    var periodEnd by remember { mutableStateOf(formatTurkishDateMillis(progressDay())) }
    var answers by remember { mutableStateOf(progressSites.flatMap { site ->
        val metric = data.draftMetrics.find { it.site == site }
        listOf("$site:v" to (metric?.views?.toString() ?: ""), "$site:f" to (metric?.favorites?.toString() ?: ""))
    }.toMap()) }
    var evaluation by remember { mutableStateOf(data.draftEvaluation) }
    fun metrics(): List<ProgressMetric>? {
        if (answers.values.any { it.isNotBlank() && (it.toIntOrNull() == null || it.toInt() < 0) }) {
            notice = "Sayılar sıfır veya pozitif tam sayı olmalı. Bilinmeyen değerleri boş bırakın."; return null
        }
        return progressSites.map { ProgressMetric(it, answers["$it:v"]?.toIntOrNull(), answers["$it:f"]?.toIntOrNull()) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("İlerleme Raporu", style = MaterialTheme.typography.titleLarge)
                Text(portfolio.title, maxLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Hareketler", "Rapor", "Geçmiş", "Danışman").forEachIndexed { index, title ->
                        TextButton(onClick = { tab = index }, modifier = Modifier.weight(1f)) { Text(title) }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (loadError.isNotBlank()) Text(loadError, color = MaterialTheme.colorScheme.error)
                    when (tab) {
                        0 -> {
                            OutlinedTextField(startText, { startText = it }, label = { Text("Takip başlangıcı (gg.aa.yyyy)") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                val date = parseTurkishDateMillis(startText)
                                if (date <= 0 || date > progressDay()) notice = "Geçerli bir başlangıç tarihi girin." else persist(data.copy(trackingStart = date))
                            }) { Text("Takip başlangıcını kaydet") }
                            Text("İlk rapor 7 gün sonra; sonraki raporlar pazartesi hazırlanır. Hatırlatma portföy kartında görünür.")
                            OutlinedTextField(activityDate, { activityDate = it }, label = { Text("Hareket tarihi (gg.aa.yyyy)") }, modifier = Modifier.fillMaxWidth())
                            listOf("Telefon görüşmesi", "Mesajlaşma", "Yer gösterme", "Teklif", "Sosyal medya", "Diğer").chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { item -> FilterChip(selected = kind == item, onClick = { kind = item }, label = { Text(item) }) }
                                }
                            }
                            OutlinedTextField(note, { note = it }, label = { Text("Kısa not / sonuç") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                val date = parseTurkishDateMillis(activityDate)
                                if (date <= 0 || date > progressDay() || note.isBlank()) notice = "Geçerli tarih ve hareket notu girin."
                                else {
                                    val next = data.copy(trackingStart = data.trackingStart.takeIf { it > 0 } ?: date,
                                        activities = data.activities + ProgressActivity(Clock.now().toString() + "_" + data.activities.size, date, kind, note.trim()))
                                    if (persist(next)) note = ""
                                }
                            }) { Text("Hareketi kaydet") }
                            data.activities.sortedByDescending { it.date }.forEach { activity ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("${formatTurkishDateMillis(activity.date)} • ${activity.kind}")
                                        Text(activity.note)
                                        TextButton(onClick = { persist(data.copy(activities = data.activities.filterNot { it.id == activity.id })) }) { Text("Hareketi sil") }
                                    }
                                }
                            }
                        }
                        1 -> {
                            OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Dönem başlangıcı (gg.aa.yyyy)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(periodEnd, { periodEnd = it }, label = { Text("Dönem bitişi (gg.aa.yyyy)") }, modifier = Modifier.fillMaxWidth())
                            Text("Sitelerde görünen toplam sayıları girin. Bilgi yoksa boş bırakın. Önceki raporla fark otomatik hesaplanır.")
                            progressSites.forEach { site ->
                                Text("$site ilanınız kaç kez görüntülendi ve kaç favorisi var?", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(answers["$site:v"] ?: "", { answers = answers + ("$site:v" to it) }, label = { Text("Görüntülenme") }, modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(answers["$site:f"] ?: "", { answers = answers + ("$site:f" to it) }, label = { Text("Favori") }, modifier = Modifier.weight(1f), singleLine = true)
                                }
                            }
                            OutlinedTextField(evaluation, { evaluation = it }, label = { Text("Genel değerlendirme ve gelecek hafta planı") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { metrics()?.let { persist(data.copy(draftMetrics = it, draftEvaluation = evaluation)) } }) { Text("Taslağı kaydet") }
                            Button(onClick = {
                                val from = parseTurkishDateMillis(periodStart)
                                val until = parseTurkishDateMillis(periodEnd)
                                val numbers = metrics()
                                when {
                                    from <= 0 || until < from || until > progressDay() -> notice = "Rapor tarih aralığını kontrol edin."
                                    evaluation.isBlank() -> notice = "Genel değerlendirme yazın."
                                    advisor.name.isBlank() || advisor.phone.isBlank() -> { tab = 3; notice = "Önce danışman adı ve telefonunu kaydedin." }
                                    numbers != null -> {
                                        val previous = data.reports.filter { it.end < from }.maxByOrNull { it.end }
                                        val html = progressHtml(portfolio, advisor, from, until, numbers, previous?.metrics, data.activities.filter { it.date in from..until }, evaluation)
                                        val report = ProgressArchive(Clock.now().toString(), from, until, numbers, html, evaluation)
                                        if (persist(data.copy(reports = data.reports + report, draftMetrics = numbers, draftEvaluation = evaluation))) {
                                            platform.showProgressReport(html, "İlerleme Raporu") { ok, message -> notice = if (ok) "Rapor arşivlendi. Önizlemeden PDF olarak kaydedebilirsiniz." else message }
                                        }
                                    }
                                }
                            }) { Text("Hazırla • Önizle / PDF") }
                            if (data.reports.isNotEmpty()) {
                                val latest = data.reports.maxByOrNull { it.end }!!
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            val text = buildReportWhatsAppText(portfolio, latest, advisor)
                                            platform.openUri("https://wa.me/?text=" + text)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Text("📱 Son Raporu WhatsApp'ta Paylaş", color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            platform.shareReport("İlerleme Raporu", latest.html)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E))
                                    ) {
                                        Text("📎 Rapor Dosyasını Paylaş (WhatsApp / Dosya)", color = Color.White)
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (data.reports.isEmpty()) Text("Henüz hazırlanmış rapor yok.")
                            data.reports.sortedByDescending { it.end }.forEach { report ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { platform.showProgressReport(report.html, "İlerleme Raporu") { _, message -> notice = message } }, modifier = Modifier.fillMaxWidth()) {
                                        Text("${formatTurkishDateMillis(report.start)} – ${formatTurkishDateMillis(report.end)} • Aç / PDF")
                                    }
                                    Button(
                                        onClick = {
                                            val text = buildReportWhatsAppText(portfolio, report, advisor)
                                            platform.openUri("https://wa.me/?text=" + text)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Text("📱 Raporu WhatsApp'ta Paylaş", color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            platform.shareReport("İlerleme Raporu", report.html)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E))
                                    ) {
                                        Text("📎 Rapor Dosyasını Paylaş (WhatsApp / Dosya)", color = Color.White)
                                    }
                                }
                            }
                        }
                        3 -> {
                            OutlinedTextField(advisor.name, { advisor = advisor.copy(name = it) }, label = { Text("Danışman adı") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(advisor.phone, { advisor = advisor.copy(phone = it) }, label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(advisor.office, { advisor = advisor.copy(office = it) }, label = { Text("Ofis") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                try { settings.putString(advisorKey, progressJson.encodeToString(advisor)); notice = "Danışman bilgileri kaydedildi; diğer raporlarda otomatik kullanılacak." }
                                catch (_: Exception) { notice = "Danışman bilgileri kaydedilemedi." }
                            }) { Text("Danışman bilgilerini kaydet") }
                        }
                    }
                }
                if (notice.isNotBlank()) Text(notice, modifier = Modifier.padding(vertical = 6.dp))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Kapat") }
            }
        }
    }
}

internal fun progressHtml(p: Portfolio, advisor: ProgressAdvisor, start: Long, end: Long,
    metrics: List<ProgressMetric>, previous: List<ProgressMetric>?, activities: List<ProgressActivity>, evaluation: String): String {
    fun esc(s: String) = progressEscape(s)
    fun count(n: Int?) = n?.toString() ?: "—"
    fun change(now: Int?, old: Int?): String = if (now == null || old == null) "—" else if (now >= old) "+${now - old}" else "${now - old} (sayaç azaldı)"
    val rows = metrics.joinToString("") { m ->
        val old = previous?.find { it.site == m.site }
        "<tr><td>${esc(m.site)}</td><td>${count(m.views)}</td><td>${count(m.favorites)}</td><td>${change(m.views, old?.views)}</td><td>${change(m.favorites, old?.favorites)}</td></tr>"
    }
    val movements = if (activities.isEmpty()) "<p>Bu döneme ait kayıtlı hareket bulunmuyor.</p>" else activities.sortedBy { it.date }.joinToString("") {
        "<div class='movement'><b>${formatTurkishDateMillis(it.date)} • ${esc(it.kind)}</b><p>${esc(it.note).replace("\n", "<br>")}</p></div>"
    }
    return """<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>İlerleme Raporu</title>
<style>@page{size:A4;margin:15mm}*{box-sizing:border-box}body{font-family:Arial,sans-serif;color:#17213a;background:#fff;margin:0;padding:24px;line-height:1.5}main{max-width:850px;margin:auto}h1{font-size:38px;text-align:center;color:#cf2036;margin:0 0 16px}h1 span{color:#103e8c}.hero{background:#103e8c;color:white;padding:20px;border-radius:6px}.hero h2{color:#ffdf50;margin:5px 0;font-size:23px}.price{font-size:22px;color:#ffdf50;font-weight:bold}h3{color:#103e8c;border-bottom:3px solid #cf2036;padding-bottom:7px;margin-top:25px}table{width:100%;border-collapse:collapse;font-size:13px}th{background:#103e8c;color:white}td,th{padding:9px;border:1px solid #d5ddeb;text-align:left}.movement{break-inside:avoid;border-bottom:1px solid #ddd;padding:8px 0}.movement p{margin:5px 0}.evaluation{white-space:pre-wrap}.footer{border-top:3px solid #103e8c;margin-top:25px;padding-top:12px}button{padding:12px;background:#103e8c;color:white;border:0;border-radius:6px;cursor:pointer}nav{text-align:center;margin:15px}@media print{nav{display:none}body{padding:0}.hero,th{print-color-adjust:exact;-webkit-print-color-adjust:exact}tr{break-inside:avoid}}@media(max-width:500px){body{padding:12px}h1{font-size:28px}td,th{padding:5px;font-size:11px}}</style></head>
<body><nav><button onclick="window.print()">PDF olarak kaydet / Yazdır</button></nav><main><h1>İLERLEME <span>RAPORU</span></h1>
<div class="hero"><div>Rapor dönemi: ${formatTurkishDateMillis(start)} – ${formatTurkishDateMillis(end)}</div><h2>${esc(p.title)}</h2><div>${esc(p.location)} • ${esc(p.type)} • ${esc(p.rooms)}</div><div class="price">${esc(formatPrice(p.price))}</div><div>Sayın ${esc(p.ownerName.ifBlank { "Portföy sahibimiz" })}</div></div>
<h3>İLAN SİTELERİ • GÖRÜNTÜLENME VE FAVORİ</h3><table><thead><tr><th>Platform</th><th>Toplam görüntülenme</th><th>Toplam favori</th><th>Görüntülenme farkı</th><th>Favori farkı</th></tr></thead><tbody>$rows</tbody></table><p style="font-size:11px">Veriler danışman tarafından girilmiştir. Farklar önceki raporla karşılaştırılır; ilk raporda ve bilgi olmayan alanlarda — gösterilir.</p>
<h3>BU DÖNEMDE YAPILAN ÇALIŞMALAR</h3>$movements<h3>GENEL DEĞERLENDİRME</h3><div class="evaluation">${esc(evaluation)}</div>
<div class="footer"><b>${esc(advisor.name)}</b><br>${esc(advisor.office)}<br>Telefon: ${esc(advisor.phone)}</div></main></body></html>"""
}
