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

        val root = JPanel(BorderLayout(16, 16)).apply {
            border = BorderFactory.createEmptyBorder(22, 24, 18, 24)
            background = Color(0x13, 0x15, 0x1A)
        }
        val header = JPanel(BorderLayout(12, 4)).apply {
            isOpaque = false
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JLabel("Chumpy").apply { font = Font("Segoe UI", Font.BOLD, 30); foreground = Color(0xF4, 0xF5, 0xF7) })
                add(JLabel("Твой интернет. Твои правила.").apply { foreground = Color(0x9A, 0xA0, 0xAA); font = Font("Segoe UI", Font.PLAIN, 12) })
            }, BorderLayout.WEST)
            add(JButton(if (happSpoof.enabled()) "Happ Spoof · ВКЛ" else "Happ Spoof · ВЫКЛ").apply {
                addActionListener { editHappSpoof(); text = if (happSpoof.enabled()) "Happ Spoof · ВКЛ" else "Happ Spoof · ВЫКЛ" }
            }, BorderLayout.EAST)
        }
        root.add(header, BorderLayout.NORTH)

        val tabs = JTabbedPane()
        val homeTab = JPanel(BorderLayout(12, 12)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(18, 4, 12, 4)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(JLabel("Добро пожаловать в Chumpy").apply {
                    font = Font("Segoe UI", Font.BOLD, 24)
                    foreground = Color(0xF4, 0xF5, 0xF7)
                })
                add(Box.createVerticalStrut(8))
                add(JLabel("Подключайся к выбранному серверу одним нажатием.").apply {
                    foreground = Color(0x9A, 0xA0, 0xAA)
                })
                add(Box.createVerticalStrut(24))
                add(JPanel(BorderLayout(8, 8)).apply {
                    background = Color(0x1D, 0x20, 0x27)
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(0x32, 0x36, 0x40), 1, true),
                        BorderFactory.createEmptyBorder(18, 18, 18, 18)
                    )
                    add(JLabel("СОСТОЯНИЕ ПОДКЛЮЧЕНИЯ").apply {
                        foreground = Color(0x9A, 0xA0, 0xAA); font = Font("Segoe UI", Font.BOLD, 11)
                    }, BorderLayout.NORTH)
                    add(status.apply {
                        horizontalAlignment = SwingConstants.LEFT
                        foreground = Color(0xF4, 0xF5, 0xF7)
                        font = Font("Segoe UI", Font.BOLD, 20)
                    }, BorderLayout.CENTER)
                })
                add(Box.createVerticalStrut(16))
                add(JLabel("Режим подключения").apply { foreground = Color(0xC8, 0xCC, 0xD3) })
                add(mode)
                add(Box.createVerticalStrut(16))
                add(JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
                    isOpaque = false
                    add(JButton("Подключить").apply { addActionListener { connect() } })
                    add(JButton("Отключить").apply { addActionListener { disconnect() } })
                    add(JButton("Выбрать сервер").apply { addActionListener { tabs.selectedIndex = 1 } })
                })
            }, BorderLayout.CENTER)
        }

        val profilePanel = JPanel(BorderLayout(8, 8)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(14, 4, 12, 4)
            add(JScrollPane(profileList).apply {
                border = BorderFactory.createLineBorder(Color(0x32, 0x36, 0x40), 1, true)
                preferredSize = Dimension(760, 360)
            }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
                isOpaque = false
                add(JButton("Вставить ссылку").apply { addActionListener { pasteLink() } })
                add(JButton("Импортировать файлы…").apply { addActionListener { importLinks() } })
                add(JButton("Импортировать QR…").apply { addActionListener { importQrImage() } })
                add(JButton("Добавить подписку").apply { addActionListener { addSubscription() } })
                add(JButton("Обновить подписки").apply { addActionListener { refreshSubscriptions() } })
                add(JButton("Удалить").apply { addActionListener {
                    profileList.selectedIndices.sortedDescending().forEach(profiles::remove)
                    saveProfiles()
                } })
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                isOpaque = false
                add(JButton("Создать Xray-конфиг").apply { addActionListener { generateConfig() } })
                add(JButton("Создать TUN-конфиг").apply { addActionListener { generateTunConfig() } })
            }, BorderLayout.SOUTH)
        }

        val settingsTab = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = BorderFactory.createEmptyBorder(18, 8, 12, 8)
            add(JLabel("Подключение").apply { font = Font("Segoe UI", Font.BOLD, 20); foreground = Color(0xF4, 0xF5, 0xF7) })
            add(Box.createVerticalStrut(12))
            add(JLabel("Режим").apply { foreground = Color(0xC8, 0xCC, 0xD3) })
            add(JLabel("Выбранный режим меняется на главной странице.").apply { foreground = Color(0x9A, 0xA0, 0xAA) })
            add(Box.createVerticalStrut(14))
            add(JLabel("Исполняемый файл движка — Xray встроен автоматически").apply { foreground = Color(0xC8, 0xCC, 0xD3) })
            add(pathRow(enginePath, "exe"))
            add(Box.createVerticalStrut(12))
            add(JLabel("JSON-конфигурация выбранного движка").apply { foreground = Color(0xC8, 0xCC, 0xD3) })
            add(pathRow(configPath, "json"))
            add(Box.createVerticalStrut(16))
            add(JLabel("TUN требует Wintun и запуска приложения от имени администратора.").apply { foreground = Color(0x9A, 0xA0, 0xAA) })
            add(Box.createVerticalStrut(22))
            add(JLabel("Подписки").apply { font = Font("Segoe UI", Font.BOLD, 20); foreground = Color(0xF4, 0xF5, 0xF7) })
            add(Box.createVerticalStrut(8))
            add(JButton("Настроить имитацию Happ").apply { addActionListener { editHappSpoof() } })
        }

        tabs.addTab("Главная", homeTab)
        tabs.addTab("Серверы", profilePanel)
        tabs.addTab("Настройки", settingsTab)
        root.add(tabs, BorderLayout.CENTER)
        root.add(JLabel("Windows · Xray Core").apply {
            foreground = Color(0x72, 0x78, 0x83)
            border = BorderFactory.createEmptyBorder(8, 2, 0, 2)
        }, BorderLayout.SOUTH)
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
        val enabled = JCheckBox("Использовать Happ User-Agent и x-заголовки", happSpoof.enabled())
        val appVersion = JTextField("3.21.1", 18)
        val os = JTextField("Android", 18)
        val osVersion = JTextField("16", 18)
        val deviceModel = JTextField("Pixel 7 Pro", 18)
        val locale = JTextField("en", 18)
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
