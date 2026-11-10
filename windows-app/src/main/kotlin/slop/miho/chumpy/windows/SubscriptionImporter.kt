package slop.miho.chumpy.windows

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

internal class SubscriptionImporter(private val happSpoof: HappSpoof) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetch(url: String): List<String> {
        val uri = URI(url.trim())
        require(uri.scheme == "https" || uri.scheme == "http") { "Подписка должна быть HTTP(S)-ссылкой." }
        val requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(45))
            .GET()

        val fingerprint = happSpoof.fingerprint()
        if (fingerprint != null) {
            // Same Happ identity and headers as the Android source app.
            fingerprint.headers().forEach { (name, value) -> requestBuilder.header(name, value) }
        } else {
            // The source app's generic fallback UA asks panels for ordinary links/raw configs,
            // not Clash YAML or sing-box JSON.
            requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "*/*")
        }

        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() in 200..299) {
            "Сервер подписки ответил HTTP ${response.statusCode()}."
        }

        val rawBytes = response.body()
        val bytes = if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", true)) {
            GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readNBytes(MAX_BODY_BYTES + 1) }
        } else rawBytes
        require(bytes.size <= MAX_BODY_BYTES) { "Ответ подписки больше 8 МиБ." }

        val body = String(bytes, Charsets.UTF_8).trim().removePrefix("\uFEFF")
        require(body.isNotBlank()) { "Сервер вернул пустую подписку." }
        val decoded = LinkUtils.decodeBase64(body)
        val content = if (decoded != null && decoded.contains("://")) decoded else body
        val links = content.lineSequence()
            .map(String::trim)
            .filter { LinkParsers.parse(it) != null }
            .distinct()
            .toList()

        require(links.isNotEmpty()) {
            "Ответ получен, но поддерживаемые ссылки не найдены. Возможно, панель вернула формат конфига, который пока не умеет разбирать Windows-версия."
        }
        return links
    }

    private companion object {
        const val DEFAULT_USER_AGENT = "Chumpy/1.0 (Android) v2rayNG/1.10"
        const val MAX_BODY_BYTES = 8 * 1024 * 1024
    }
}
