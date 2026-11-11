package slop.miho.chumpy.windows

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Color
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private enum class ConnectionMode(val label: String) {
    SYSTEM_PROXY("Системный прокси"), TUN("TUN (виртуальный адаптер)")
}
fun main() = SwingUtilities.invokeLater {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    UIManager.put("Panel.background", Color(0x13, 0x15, 0x1A))
    UIManager.put("TabbedPane.background", Color(0x19, 0x1C, 0x23))
    UIManager.put("TabbedPane.foreground", Color(0xE8, 0xEA, 0xEF))
    UIManager.put("Button.background", Color(0x2A, 0x2E, 0x37))
    UIManager.put("Button.foreground", Color(0xF4, 0xF5, 0xF7))
    UIManager.put("ComboBox.background", Color(0x1D, 0x20, 0x27))
    UIManager.put("ComboBox.foreground", Color(0xF4, 0xF5, 0xF7))
    UIManager.put("TextField.background", Color(0x1D, 0x20, 0x27))
    UIManager.put("TextField.foreground", Color(0xF4, 0xF5, 0xF7))
    UIManager.put("List.background", Color(0x1D, 0x20, 0x27))
    UIManager.put("List.foreground", Color(0xF4, 0xF5, 0xF7))
    UIManager.put("List.selectionBackground", Color(0x3C, 0x50, 0x78))
    UIManager.put("List.selectionForeground", Color(0xFF, 0xFF, 0xFF))
    UIManager.put("ScrollPane.background", Color(0x13, 0x15, 0x1A))
    UIManager.put("Viewport.background", Color(0x1D, 0x20, 0x27))
    ChumpyWindow().isVisible = true
}
private class ChumpyWindow : JFrame("Chumpy — Windows") {
    private val home = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Chumpy")
    private val profilesFile = File(home, "profiles.txt")
    private val subscriptionsFile = File(home, "subscriptions.txt")
    private val subscriptions = mutableListOf<String>()
    private val happSpoof = HappSpoof(home)
    private val subscriptionImporter = SubscriptionImporter(happSpoof)
    private val profiles = DefaultListModel<String>()
    private val profileList = JList(profiles).apply {
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val raw = value as? String
                val profile = raw?.let { LinkParsers.parse(it) }
                label.text = if (profile == null) raw.orEmpty() else
                    "${profile.remarks.ifBlank { profile.server }}  •  ${profile.server}:${profile.serverPort}  •  ${profile.type.name}"
                return label
            }
        }
    }
    private val mode = JComboBox(ConnectionMode.entries.toTypedArray())
    private val enginePath = JTextField(30)
    private val configPath = JTextField(30)
    private val status = JLabel("Отключено", SwingConstants.CENTER)
    private val engine = ManagedEngine()
    private val proxy = WindowsProxyController(home)

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) { disconnect(); dispose() }
        })
        minimumSize = Dimension(760, 540)
        size = Dimension(900, 650)
        setLocationRelativeTo(null)
        home.mkdirs()
        enginePath.text = runCatching { BundledXray.install(home)?.absolutePath.orEmpty() }.getOrDefault("")
        if (profilesFile.isFile) profilesFile.readLines().filter { it.isNotBlank() }.forEach(profiles::addElement)
        if (subscriptionsFile.isFile) subscriptions.addAll(subscriptionsFile.readLines().filter { it.isNotBlank() })

        val bg = Color(0x16, 0x16, 0x18)
        val surface = Color(0x2D, 0x2D, 0x30)
        val muted = Color(0xA5, 0xA5, 0xAD)
        val blue = Color(0x37, 0x78, 0xF6)
        fun label(text: String, size: Int, color: Color, bold: Boolean = false) =
            JLabel(text).apply { foreground = color; font = Font("Segoe UI", if (bold) Font.BOLD else Font.PLAIN, size) }
        fun actionButton(text: String, color: Color = surface, foreground: Color = Color.WHITE, height: Int = 48) =
            JButton(text).apply {
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = true
                background = color
                this.foreground = foreground
                font = Font("Segoe UI", Font.BOLD, 15)
                preferredSize = Dimension(180, height)
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            }

        val root = JPanel(BorderLayout()).apply {
            background = bg
            border = BorderFactory.createEmptyBorder(28, 34, 18, 34)
        }
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(label("Chumpy", 28, Color.WHITE, true))
                add(label("Твой интернет. Твои правила.", 12, muted))
            }, BorderLayout.WEST)
            add(actionButton("⋯", surface, Color.WHITE, 42).apply {
                preferredSize = Dimension(48, 42)
                addActionListener { tabs.selectedIndex = 3 }
            }, BorderLayout.EAST)
        }
        root.add(header, BorderLayout.NORTH)

        val tabs = JTabbedPane()
        tabs.setUI(object : javax.swing.plaf.basic.BasicTabbedPaneUI() {
            override fun calculateTabAreaHeight(tabPlacement: Int, runCount: Int, maxTabHeight: Int): Int = 0
            override fun paintTabArea(g: java.awt.Graphics, tabPlacement: Int, rects: Array<out java.awt.Rectangle>, selectedIndex: Int) {}
        })

        val homeTab = JPanel(BorderLayout()).apply {
            background = bg
            border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
        }
        val homeContent = JPanel().apply {
            background = bg
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(0, 0, 12, 0)
        }
        val topSpace = JPanel().apply { isOpaque = false; preferredSize = Dimension(1, 110); maximumSize = Dimension(Int.MAX_VALUE, 110) }
        homeContent.add(topSpace)
        homeContent.add(label("Chumpy", 22, blue, true).apply { alignmentX = Component.LEFT_ALIGNMENT })
        homeContent.add(Box.createVerticalStrut(8))
        val hour = java.time.LocalTime.now().hour
        val greeting = when (hour) { in 5..11 -> "Доброе утро"; in 12..16 -> "Добрый день"; in 17..22 -> "Добрый вечер"; else -> "Доброй ночи" }
        homeContent.add(label(greeting, 54, Color.WHITE, true).apply { alignmentX = Component.LEFT_ALIGNMENT })
        homeContent.add(Box.createVerticalStrut(28))
        val chips = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(actionButton("●  Отключено", Color(0x3B, 0x3B, 0x42), Color(0xE7, 0xE7, 0xEF), 42))
        }
        homeContent.add(chips)
        homeContent.add(Box.createVerticalStrut(22))
        val serverCard = JPanel(BorderLayout(18, 0)).apply {
            background = surface
            border = BorderFactory.createEmptyBorder(22, 24, 22, 24)
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(900, 118)
            maximumSize = Dimension(Int.MAX_VALUE, 118)
            val flag = label("🇩🇪", 30, Color.WHITE, true)
            add(flag, BorderLayout.WEST)
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                val selectedRaw = profiles.elements().toList().firstOrNull()
                val selectedProfile = selectedRaw?.let { LinkParsers.parse(it) }
                val serverName = selectedProfile?.remarks?.ifBlank { selectedProfile.server } ?: "Сервер не выбран"
                add(label("Подписка / сервер →", 13, muted))
                add(Box.createVerticalStrut(4))
                add(label(serverName, 23, Color.WHITE, true))
            }, BorderLayout.CENTER)
            add(label("›", 36, Color.WHITE, true), BorderLayout.EAST)
        }
        serverCard.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        serverCard.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) { tabs.selectedIndex = 1 }
        })
        homeContent.add(serverCard)
        homeContent.add(Box.createVerticalStrut(20))
        val connectButton = actionButton("〰〰〰〰〰〰〰〰〰〰〰", blue, Color.WHITE, 112).apply {
            font = Font("Segoe UI", Font.BOLD, 30)
            maximumSize = Dimension(Int.MAX_VALUE, 112)
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener { if (status.text.contains("Подключено")) disconnect() else connect() }
        }
        homeContent.add(connectButton)
        homeTab.add(homeContent, BorderLayout.CENTER)

        val profilePanel = JPanel(BorderLayout(12, 12)).apply {
            background = bg
            border = BorderFactory.createEmptyBorder(18, 8, 12, 8)
            add(JScrollPane(profileList).apply {
                border = BorderFactory.createEmptyBorder()
                viewport.background = surface
                preferredSize = Dimension(760, 360)
            }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
                isOpaque = false
                add(actionButton("Вставить ссылку").apply { addActionListener { pasteLink() } })
                add(actionButton("Импортировать файлы").apply { addActionListener { importLinks() } })
                add(actionButton("Импортировать QR").apply { addActionListener { importQrImage() } })
                add(actionButton("Добавить подписку").apply { addActionListener { addSubscription() } })
                add(actionButton("Обновить подписки").apply { addActionListener { refreshSubscriptions() } })
                add(actionButton("Удалить").apply { addActionListener {
                    profileList.selectedIndices.sortedDescending().forEach(profiles::remove)
                    saveProfiles()
                } })
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                isOpaque = false
                add(actionButton("Создать Xray-конфиг").apply { addActionListener { generateConfig() } })
                add(actionButton("Создать TUN-конфиг").apply { addActionListener { generateTunConfig() } })
            }, BorderLayout.SOUTH)
        }

        val statsTab = JPanel(BorderLayout()).apply {
            background = bg
            border = BorderFactory.createEmptyBorder(30, 12, 12, 12)
            add(label("Статистика", 32, Color.WHITE, true), BorderLayout.NORTH)
            add(label("Графики трафика и состояние подключения появятся здесь.", 15, muted), BorderLayout.CENTER)
        }
        val settingsTab = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = bg
            border = BorderFactory.createEmptyBorder(24, 12, 12, 12)
            add(label("Настройки", 32, Color.WHITE, true))
            add(Box.createVerticalStrut(22))
            add(label("Режим подключения", 15, Color.WHITE, true))
            add(mode)
            add(Box.createVerticalStrut(18))
            add(label("Исполняемый файл движка — Xray", 14, muted))
            add(pathRow(enginePath, "exe"))
            add(Box.createVerticalStrut(12))
            add(label("JSON-конфигурация", 14, muted))
            add(pathRow(configPath, "json"))
            add(Box.createVerticalStrut(18))
            add(label("TUN требует Wintun и запуска приложения от имени администратора.", 12, muted))
            add(Box.createVerticalStrut(24))
            add(actionButton("Настроить имитацию Happ").apply { addActionListener { editHappSpoof() } })
        }

        tabs.addTab("Главная", homeTab)
        tabs.addTab("Серверы", profilePanel)
        tabs.addTab("Статистика", statsTab)
        tabs.addTab("Настройки", settingsTab)

        val navigation = JPanel(FlowLayout(FlowLayout.CENTER, 42, 12)).apply {
            background = bg
            border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            listOf("⌂", "◎", "▥", "⚙").forEachIndexed { index, glyph ->
                add(actionButton(glyph, bg, if (index == 0) blue else Color.WHITE, 54).apply {
                    preferredSize = Dimension(76, 54)
                    font = Font("Segoe UI Symbol", Font.BOLD, 28)
                    addActionListener { tabs.selectedIndex = index }
                })
            }
        }
        root.add(tabs, BorderLayout.CENTER)
        root.add(navigation, BorderLayout.SOUTH)
        contentPane = root

    }

    private fun pathRow(field: JTextField, ext: String) = JPanel(BorderLayout(6, 0)).apply {
        add(field, BorderLayout.CENTER)
        add(JButton("Выбрать…").apply {
            addActionListener {
                val chooser = JFileChooser()
                chooser.fileFilter = FileNameExtensionFilter("$ext files", ext)
                if (chooser.showOpenDialog(this@ChumpyWindow) == JFileChooser.APPROVE_OPTION) field.text = chooser.selectedFile.absolutePath
            }
        }, BorderLayout.EAST)
    }
    private fun editHappSpoof() {
        val saved = happSpoof.currentSettings()
        val enabled = JCheckBox("Использовать Happ User-Agent и x-заголовки", happSpoof.enabled())
        val appVersion = JTextField(saved.appVersion, 18)
        val os = JTextField(saved.os, 18)
        val osVersion = JTextField(saved.osVersion, 18)
        val deviceModel = JTextField(saved.deviceModel, 18)
        val locale = JTextField(saved.locale, 18)
        val fields = JPanel(java.awt.GridLayout(0, 2, 8, 8)).apply {
            add(enabled); add(JLabel("")); add(JLabel("Версия Happ")); add(appVersion)
            add(JLabel("ОС")); add(os); add(JLabel("Версия ОС")); add(osVersion)
            add(JLabel("Модель устройства")); add(deviceModel); add(JLabel("Язык")); add(locale)
        }
        val result = JOptionPane.showConfirmDialog(this, fields, "Имитация клиента Happ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result != JOptionPane.OK_OPTION) return
        happSpoof.setEnabled(enabled.isSelected)
        happSpoof.update(appVersion.text, os.text, osVersion.text, deviceModel.text, locale.text)
        status.text = if (enabled.isSelected) "Подписки будут загружаться как Happ" else "Happ Spoof отключён; используется Chumpy/v2rayNG User-Agent"
    }

    private fun addSubscription() {
        val url = JOptionPane.showInputDialog(this, "URL подписки:")?.trim().orEmpty()
        if (url.isBlank()) return
        if (!url.startsWith("https://") && !url.startsWith("http://")) { JOptionPane.showMessageDialog(this, "Нужна ссылка HTTP(S)."); return }
        if (url !in subscriptions) subscriptions.add(url)
        subscriptionsFile.writeText(subscriptions.joinToString("\n"))
        refreshSubscriptions()
    }
    private fun refreshSubscriptions() {
        if (subscriptions.isEmpty()) { JOptionPane.showMessageDialog(this, "Сначала добавьте URL подписки."); return }
        status.text = "Обновление подписок…"
        Thread({
            var imported = 0
            val errors = mutableListOf<String>()
            subscriptions.forEach { url ->
                try {
                    val links = subscriptionImporter.fetch(url)
                    SwingUtilities.invokeLater {
                        links.forEach { link -> if ((0 until profiles.size()).none { profiles.getElementAt(it) == link }) profiles.addElement(link) }
                    }
                    imported += links.size
                } catch (e: Exception) { errors.add(url + ": " + (e.message ?: "ошибка")) }
            }
            SwingUtilities.invokeLater {
                saveProfiles()
                status.text = "Подписки обновлены; найдено ссылок: " + imported
                if (errors.isNotEmpty()) JOptionPane.showMessageDialog(this, errors.joinToString("\n"), "Ошибки подписок", JOptionPane.WARNING_MESSAGE)
            }
        }, "chumpy-subscriptions").start()
    }
    private fun pasteLink() {
        val raw = JOptionPane.showInputDialog(this, "Вставь ссылку VMess / VLESS / Trojan / SS / SOCKS / Hy2 / WireGuard:")?.trim().orEmpty()
        if (raw.isBlank()) return
        val profile = LinkParsers.parse(raw)
        if (profile == null) { JOptionPane.showMessageDialog(this, "Не удалось распознать ссылку или в ней не хватает обязательных данных."); return }
        if ((0 until profiles.size()).none { profiles.getElementAt(it) == raw }) profiles.addElement(raw)
        saveProfiles()
        status.text = "Добавлен профиль: " + profile.server
    }
    private fun importQrImage() {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("QR images", "png", "jpg", "jpeg", "bmp") }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        try {
            val decoded = QrImageImporter.read(chooser.selectedFile)
            val links = decoded.lineSequence().map(String::trim).filter { LinkParsers.parse(it) != null }.toList()
            if (links.isEmpty()) error("QR-код не содержит поддерживаемых ссылок Chumpy.")
            links.forEach { link -> if ((0 until profiles.size()).none { profiles.getElementAt(it) == link }) profiles.addElement(link) }
            saveProfiles()
            status.text = "QR импортирован: " + links.size + " ссылок"
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, e.message ?: e.javaClass.simpleName, "QR-импорт", JOptionPane.ERROR_MESSAGE)
        }
    }
    private fun importLinks() {
        val chooser = JFileChooser().apply { isMultiSelectionEnabled = true; fileFilter = FileNameExtensionFilter("Текстовые файлы", "txt", "conf") }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        chooser.selectedFiles.forEach { file ->
            file.readLines().map(String::trim).filter { LinkParsers.parse(it) != null }.forEach { line ->
                if ((0 until profiles.size()).none { profiles.getElementAt(it) == line }) profiles.addElement(line)
            }
        }
        saveProfiles()
        status.text = "Импортировано поддерживаемых ссылок: ${profiles.size()}"
    }
    private fun saveProfiles() { profilesFile.writeText((0 until profiles.size()).joinToString("\n") { profiles.getElementAt(it) }) }
    private fun generateTunConfig() {
        val raw = profileList.selectedValue
        if (raw.isNullOrBlank()) { JOptionPane.showMessageDialog(this, "Сначала выберите импортированную ссылку."); return }
        try {
            val profile = LinkParsers.parse(raw) ?: error("Ссылка больше не распознаётся.")
            val generated = WindowsSingBoxConfigBuilder.write(profile, home)
            configPath.text = generated.absolutePath
            mode.selectedItem = ConnectionMode.TUN
            status.text = "TUN-конфигурация создана для " + profile.type.name
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, e.message ?: e.javaClass.simpleName, "Генерация TUN", JOptionPane.ERROR_MESSAGE)
        }
    }
    private fun generateConfig() {
        val raw = profileList.selectedValue
        if (raw.isNullOrBlank()) { JOptionPane.showMessageDialog(this, "Сначала выберите импортированную ссылку."); return }
        try {
            val profile = LinkParsers.parse(raw) ?: error("Ссылка больше не распознаётся.")
            val generated = WindowsXrayConfigBuilder.write(profile, home)
            configPath.text = generated.absolutePath
            mode.selectedItem = ConnectionMode.SYSTEM_PROXY
            status.text = "Xray-конфигурация создана для " + profile.type.name
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, e.message ?: e.javaClass.simpleName, "Импорт профиля", JOptionPane.ERROR_MESSAGE)
        }
    }
    private fun connect() {
        val selected = mode.selectedItem as ConnectionMode
        val exe = File(enginePath.text.trim()); val config = File(configPath.text.trim())
        if (!exe.isFile || !config.isFile) {
            JOptionPane.showMessageDialog(this, "Укажите существующие файлы движка и JSON-конфигурации.")
            return
        }
        try {
            engine.start(exe, config, selected == ConnectionMode.TUN)
            if (selected == ConnectionMode.SYSTEM_PROXY) proxy.enable("127.0.0.1:10809")
            status.text = "Запущено: ${selected.label}"
        } catch (e: Exception) {
            runCatching { engine.stop() }; runCatching { proxy.restore() }
            JOptionPane.showMessageDialog(this, e.message ?: e.javaClass.simpleName, "Chumpy", JOptionPane.ERROR_MESSAGE)
            status.text = "Ошибка запуска"
        }
    }
    private fun disconnect() {
        runCatching { proxy.restore() }; runCatching { engine.stop() }
        status.text = "Отключено"
    }
}
