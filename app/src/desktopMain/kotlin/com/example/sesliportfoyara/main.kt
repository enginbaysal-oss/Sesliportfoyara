package com.example.sesliportfoyara

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.net.URI
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

fun createIcon(): BufferedImage {
    val size = 512
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()
    
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    
    val gold = AwtColor(201, 161, 90)
    val darkBg = AwtColor(15, 20, 30)
    
    // 1. Dairesel Arka Plan
    g2d.color = darkBg
    g2d.fillOval(10, 10, size - 20, size - 20)
    
    // 2. Kalkan (Shield) - Dolgulu ve Çerçeveli
    g2d.color = gold
    val shield = Path2D.Float()
    shield.moveTo(size * 0.2f, size * 0.3f)
    shield.lineTo(size * 0.5f, size * 0.22f)
    shield.lineTo(size * 0.8f, size * 0.3f)
    shield.lineTo(size * 0.8f, size * 0.6f)
    shield.quadTo(size * 0.8f, size * 0.85f, size * 0.5f, size * 0.92f)
    shield.quadTo(size * 0.2f, size * 0.85f, size * 0.2f, size * 0.6f)
    shield.closePath()
    
    g2d.setStroke(BasicStroke(15f))
    g2d.draw(shield)
    
    // 3. Ses Dalgası
    g2d.setStroke(BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
    val wave = Path2D.Float()
    wave.moveTo(size * 0.4f, size * 0.38f)
    wave.lineTo(size * 0.45f, size * 0.38f)
    wave.lineTo(size * 0.48f, size * 0.32f)
    wave.lineTo(size * 0.52f, size * 0.44f)
    wave.lineTo(size * 0.55f, size * 0.38f)
    wave.lineTo(size * 0.6f, size * 0.38f)
    g2d.draw(wave)

    // 4. SP Monogramı
    g2d.font = Font("Georgia", Font.BOLD, 150)
    g2d.drawString("S", (size * 0.31f).toInt(), (size * 0.62f).toInt())
    g2d.drawString("P", (size * 0.55f).toInt(), (size * 0.68f).toInt())
    
    // 5. CRM Yükseliş Çubukları
    g2d.fillRect((size * 0.42f).toInt(), (size * 0.78f).toInt(), 18, 32)
    g2d.fillRect((size * 0.49f).toInt(), (size * 0.72f).toInt(), 18, 48)
    g2d.fillRect((size * 0.56f).toInt(), (size * 0.67f).toInt(), 18, 64)
    
    // Yükseliş Oku
    val arrow = Path2D.Float()
    arrow.moveTo(size * 0.36f, size * 0.84f)
    arrow.lineTo(size * 0.66f, size * 0.64f)
    // Ok ucu
    arrow.lineTo(size * 0.58f, size * 0.64f)
    arrow.moveTo(size * 0.66f, size * 0.64f)
    arrow.lineTo(size * 0.66f, size * 0.72f)
    g2d.setStroke(BasicStroke(6f))
    g2d.draw(arrow)
    
    g2d.dispose()
    return image
}

fun main() {
    val settingsFile = File(System.getProperty("user.home"), ".sesliportfoy_settings.properties")
    if (!settingsFile.exists()) {
        try { settingsFile.createNewFile() } catch (e: Exception) {}
    }
    
    val props = Properties()
    try {
        if (settingsFile.exists()) {
            props.load(settingsFile.inputStream())
        }
    } catch (e: Exception) { e.printStackTrace() }
    
    val desktopSettings = PropertiesSettings(props) {
        try {
            props.store(settingsFile.outputStream(), "Sesli Portfoy Settings")
        } catch (e: Exception) { e.printStackTrace() }
    }

    try {
        val oldSettings = Settings() 
        val oldPortfolioKey = "local_portfolios_v1"
        val oldCrmKey = "crm_clients_v1"
        if (oldSettings.hasKey(oldPortfolioKey) && !desktopSettings.hasKey("local_portfolios_v2")) {
            val oldData = oldSettings.getString(oldPortfolioKey, "[]")
            if (oldData != "[]") { desktopSettings.putString("local_portfolios_v2", oldData) }
        }
        if (oldSettings.hasKey(oldCrmKey) && !desktopSettings.hasKey(oldCrmKey)) {
            val oldCrmData = oldSettings.getString(oldCrmKey, "[]")
            if (oldCrmData != "[]") { desktopSettings.putString(oldCrmKey, oldCrmData) }
        }
    } catch (e: Exception) { e.printStackTrace() }

    application {
        val platformUtils = object : PlatformUtils {
            override fun openUri(uri: String) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(uri))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun startVoiceRecognition(onResult: (String) -> Unit, onError: (String) -> Unit) {
                onError("Desktop sesli aramayı henüz desteklemiyor")
            }
            override fun stopVoiceRecognition() {}

            override fun saveFile(fileName: String, content: String, onResult: (Boolean) -> Unit) {
                try {
                    val fileDialog = FileDialog(null as Frame?, "Yedekle", FileDialog.SAVE)
                    fileDialog.file = fileName
                    fileDialog.isVisible = true
                    val directory = fileDialog.directory
                    val file = fileDialog.file
                    if (directory != null && file != null) {
                        File(directory, file).writeText(content)
                        onResult(true)
                    } else { onResult(false) }
                } catch (e: Exception) { onResult(false) }
            }

            override fun pickFile(onResult: (String?) -> Unit) {
                try {
                    val fileDialog = FileDialog(null as Frame?, "Yedek Dosyası Seç", FileDialog.LOAD)
                    fileDialog.isVisible = true
                    val directory = fileDialog.directory
                    val file = fileDialog.file
                    if (directory != null && file != null) {
                        onResult(File(directory, file).readText())
                    } else { onResult(null) }
                } catch (e: Exception) { onResult(null) }
            }
        }

        val iconImage = remember { createIcon() }
        val iconPainter = remember(iconImage) { iconImage.toPainter() }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Sesli Portföy CRM Asistanı",
            icon = iconPainter
        ) {
            val crmManager = remember { LocalCRMManager(desktopSettings) }
            val localPortfolioManager = remember { LocalPortfolioManager(desktopSettings) }

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
