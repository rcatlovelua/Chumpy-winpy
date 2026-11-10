package slop.miho.chumpy.windows

import java.io.File
import java.security.SecureRandom
import java.util.Properties

/**
 * Windows port of the original Chumpy HappSpoof.
 *
 * A stable synthetic identity is stored locally so a subscription panel sees the same
 * Happ client on every refresh. No real Windows hardware identifiers are collected.
 */
internal class HappSpoof(private val home: File) {
    data class Fingerprint(
        val appVersion: String,
        val os: String,
        val osVersion: String,
        val deviceModel: String,
        val locale: String,
        val userId: String,
        val hwid: String,
    ) {
        fun headers(): Map<String, String> = mapOf(
            "User-Agent" to "Happ/$appVersion/$os/$userId",
            "x-device-locale" to locale,
            "x-hwid" to hwid,
            "x-device-os" to os,
            "x-ver-os" to osVersion,
            "x-device-model" to deviceModel,
            "accept-encoding" to "gzip",
        )
    }

    private val file = File(home, "happ-spoof.properties")
    private val random = SecureRandom()

    @Synchronized
    fun enabled(): Boolean = load().getProperty("enabled", "true").toBoolean()

    @Synchronized
    fun setEnabled(value: Boolean) {
        val props = load()
        props.setProperty("enabled", value.toString())
        save(props)
    }

    @Synchronized
    fun fingerprint(): Fingerprint? {
        val props = load()
        if (!props.getProperty("enabled", "true").toBoolean()) return null
        fun stable(key: String, create: () -> String): String {
            val old = props.getProperty(key)?.takeIf { it.isNotBlank() }
            if (old != null) return old
            return create().also { props.setProperty(key, it) }
        }
        val result = Fingerprint(
            appVersion = props.getProperty("appVersion").orEmpty().ifBlank { "3.21.1" },
            os = props.getProperty("os").orEmpty().ifBlank { "Android" },
            osVersion = props.getProperty("osVersion").orEmpty().ifBlank { "16" },
            deviceModel = props.getProperty("deviceModel").orEmpty().ifBlank { "Pixel 7 Pro" },
            locale = props.getProperty("locale").orEmpty().ifBlank { "en" },
            userId = stable("userId", ::randomUserId),
            hwid = stable("hwid", ::randomHwid),
        )
        save(props)
        return result
    }

    @Synchronized
    fun currentSettings(): Fingerprint {
        val props = load()
        fun stable(key: String, create: () -> String): String {
            val old = props.getProperty(key)?.takeIf { it.isNotBlank() }
            if (old != null) return old
            return create().also { props.setProperty(key, it) }
        }
        return Fingerprint(
            appVersion = props.getProperty("appVersion").orEmpty().ifBlank { "3.21.1" },
            os = props.getProperty("os").orEmpty().ifBlank { "Android" },
            osVersion = props.getProperty("osVersion").orEmpty().ifBlank { "16" },
            deviceModel = props.getProperty("deviceModel").orEmpty().ifBlank { "Pixel 7 Pro" },
            locale = props.getProperty("locale").orEmpty().ifBlank { "en" },
            userId = stable("userId", ::randomUserId),
            hwid = stable("hwid", ::randomHwid),
        ).also { save(props) }
    }

    @Synchronized
    fun update(
        appVersion: String,
        os: String,
        osVersion: String,
        deviceModel: String,
        locale: String,
    ) {
        val props = load()
        mapOf(
            "appVersion" to appVersion,
            "os" to os,
            "osVersion" to osVersion,
            "deviceModel" to deviceModel,
            "locale" to locale,
        ).forEach { (key, value) -> props.setProperty(key, value.trim()) }
        save(props)
    }

    private fun load(): Properties = Properties().also { props ->
        if (file.isFile) runCatching { file.inputStream().use(props::load) }
    }

    private fun save(props: Properties) {
        home.mkdirs()
        file.outputStream().use { props.store(it, "Chumpy subscription client identity") }
    }

    private fun randomUserId(): String = buildString(20) {
        append(random.nextInt(9) + 1)
        repeat(19) { append(random.nextInt(10)) }
    }

    private fun randomHwid(): String = ByteArray(8).also(random::nextBytes)
        .joinToString("") { "%02x".format(it) }
}
