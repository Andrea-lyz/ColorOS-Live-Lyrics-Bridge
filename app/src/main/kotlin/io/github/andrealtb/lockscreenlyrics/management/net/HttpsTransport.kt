package io.github.andrealtb.lockscreenlyrics.management.net

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** A response body; [contentLength] is -1 when the server did not send one. */
internal class HttpsBody(val stream: InputStream, val contentLength: Long, private val onClose: () -> Unit) : AutoCloseable {
    override fun close() {
        try {
            stream.close()
        } finally {
            onClose()
        }
    }
}

/** Opens an HTTPS GET; implementations must enforce [allowedHosts] on every hop. */
internal fun interface HttpsTransport {
    @Throws(IOException::class)
    fun get(url: String, allowedHosts: Set<String>): HttpsBody
}

internal class UntrustedHostException(message: String) : IOException(message)

/** True for plain `https://host[:443]/...` URLs whose host is in [allowedHosts]. */
internal fun isTrustedHttpsUrl(url: String, allowedHosts: Set<String>): Boolean {
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return false
    }
    return uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null &&
        (uri.port == -1 || uri.port == 443) && uri.host != null && uri.host.lowercase() in allowedHosts
}

/**
 * Plan §6.2: HTTPS only, redirects followed by hand and only into [allowedHosts], no cookies,
 * no credentials, no user-supplied sources. Integrity is never decided here: callers verify the
 * catalog signature or the APK digest/identity afterwards.
 */
internal object UrlConnectionHttpsTransport : HttpsTransport {
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    override fun get(url: String, allowedHosts: Set<String>): HttpsBody {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            // The host is reported, never the path or query (they may carry signed tokens).
            if (!isTrustedHttpsUrl(current, allowedHosts)) {
                throw UntrustedHostException("untrusted host ${hostOf(current)}")
            }
            val connection = URL(current).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept-Encoding", "identity")
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                connection.disconnect()
                throw e
            }
            when (code) {
                HttpURLConnection.HTTP_OK -> return HttpsBody(
                    connection.inputStream,
                    connection.getHeaderFieldLong("Content-Length", -1L),
                    connection::disconnect,
                )
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    location ?: throw IOException("redirect without location from ${hostOf(current)}")
                    current = try {
                        URI(current).resolve(location).toString()
                    } catch (e: Exception) {
                        // URISyntaxException / IllegalArgumentException: treat like a bad response.
                        throw IOException("malformed redirect from ${hostOf(current)}", e)
                    }
                }
                else -> {
                    connection.disconnect()
                    throw IOException("HTTP $code from ${hostOf(current)}")
                }
            }
        }
        throw IOException("too many redirects")
    }

    private fun hostOf(url: String): String = try {
        URI(url).host ?: "?"
    } catch (_: Exception) {
        "?"
    }
}
