package com.yunfie.illustia.data

private const val HTTPS_PORT = 443

/**
 * Split of an absolute URL into the pieces the login redirect checks need.
 *
 * `java.net.URI` cannot be used any more (it is JVM-only) and `io.ktor.http.Url` normalises too
 * much — it rejects or rewrites the non-http `pixiv://` scheme and percent-decodes paths. This
 * parser therefore only splits, keeps the path and query exactly as written, and returns null for
 * anything ambiguous: no scheme, an opaque form (`mailto:`, `javascript:`), a malformed authority,
 * or a fragment. Rejecting is the safe direction here, since every caller is validating a redirect
 * target.
 */
internal data class ParsedUrl(
    val scheme: String,
    /** Null when the authority is absent or cannot be parsed. */
    val host: String?,
    /** -1 when no port was given. */
    val port: Int,
    val path: String,
    val query: String?,
    val userInfo: String?,
    val fragment: String?,
) {
    val isOpaque: Boolean get() = host == null && userInfo == null && port == -1 && !path.startsWith("/")
}

internal fun parseUrl(raw: String): ParsedUrl? {
    val schemeEnd = raw.indexOf(':')
    if (schemeEnd <= 0) return null
    val scheme = raw.substring(0, schemeEnd)
    if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
    if (!scheme.first().isLetter()) return null

    var rest = raw.substring(schemeEnd + 1)

    // Fragment is rejected by callers anyway, but it must still be split off before the query so a
    // `#` inside the query cannot smuggle a second `code=` through.
    var fragment: String? = null
    rest.indexOf('#').takeIf { it >= 0 }?.let {
        fragment = rest.substring(it + 1)
        rest = rest.substring(0, it)
    }

    var query: String? = null
    rest.indexOf('?').takeIf { it >= 0 }?.let {
        query = rest.substring(it + 1)
        rest = rest.substring(0, it)
    }

    val hasAuthority = rest.startsWith("//")
    if (!hasAuthority) {
        return ParsedUrl(scheme, null, -1, rest, query, null, fragment)
    }

    val authorityAndPath = rest.substring(2)
    val authorityEnd = authorityAndPath.indexOfFirst { it == '/' }.let { if (it < 0) authorityAndPath.length else it }
    val authority = authorityAndPath.substring(0, authorityEnd)
    val path = authorityAndPath.substring(authorityEnd)

    if (authority.isEmpty()) return null

    var userInfo: String? = null
    var hostPort = authority
    authority.lastIndexOf('@').takeIf { it >= 0 }?.let {
        userInfo = authority.substring(0, it)
        hostPort = authority.substring(it + 1)
    }

    var host = hostPort
    var port = -1
    val colon = hostPort.lastIndexOf(':')
    if (colon >= 0) {
        val portText = hostPort.substring(colon + 1)
        // A colon in the host position without digits is malformed, not a default port.
        if (portText.isEmpty() || portText.any { !it.isDigit() }) return null
        port = portText.toIntOrNull() ?: return null
        host = hostPort.substring(0, colon)
    }

    if (host.isEmpty() || host.any { !isHostChar(it) }) return null

    return ParsedUrl(scheme, host, port, path, query, userInfo, fragment)
}

private fun isHostChar(c: Char): Boolean = c.isLetterOrDigit() || c == '.' || c == '-' || c == '_'

/** `java.net.URLDecoder.decode(value, "UTF-8")`, including its rejection of malformed escapes. */
private fun formDecode(value: String): String? {
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        when (val c = value[i]) {
            '+' -> {
                bytes.add(' '.code.toByte())
                i += 1
            }

            '%' -> {
                if (i + 2 >= value.length) return null
                val high = hexDigit(value[i + 1]) ?: return null
                val low = hexDigit(value[i + 2]) ?: return null
                bytes.add(((high shl 4) or low).toByte())
                i += 3
            }

            else -> {
                bytes.addAll(c.toString().encodeToByteArray().toList())
                i += 1
            }
        }
    }
    val decoded = bytes.toByteArray().decodeToString()
    // decodeToString replaces invalid sequences with U+FFFD instead of failing; a malformed escape
    // is the only way to get here with broken UTF-8, so treat that as a rejection.
    if (decoded.contains('\uFFFD') && !value.contains("%EF%BF%BD")) return null
    return decoded
}

private fun hexDigit(c: Char): Int? =
    when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }

fun pixivLoginCodeOrNull(url: String): String? {
    val uri = parseUrl(url) ?: return null
    val isAppRedirect =
        uri.scheme.equals("pixiv", ignoreCase = true) &&
            uri.host.equals("account", ignoreCase = true) &&
            uri.port == -1 && uri.path == "/login"
    val isWebRedirect =
        uri.isSecureOrigin("app-api.pixiv.net") &&
            uri.path == "/web/v1/users/auth/pixiv/callback"
    val isCallbackRoute = isAppRedirect || isWebRedirect
    val hasInvalidAuthority = uri.isOpaque || uri.userInfo != null || uri.fragment != null
    if (!isCallbackRoute || hasInvalidAuthority) return null

    return uri.query
        ?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.filter { params -> formDecode(params[0]) == "code" }
        ?.singleOrNull()
        ?.getOrNull(1)
        ?.let { formDecode(it) }
        ?.takeIf { it.isNotBlank() }
}

fun isDiscordAppUrl(url: String?): Boolean {
    val uri = url?.let { parseUrl(it) } ?: return false
    return uri.isSecureOrigin("discord.com") &&
        (uri.path == "/app" || uri.path == "/channels/@me" || uri.path.startsWith("/channels/@me/"))
}

private fun ParsedUrl.isSecureOrigin(expectedHost: String): Boolean =
    !isOpaque && scheme.equals("https", ignoreCase = true) &&
        host.equals(expectedHost, ignoreCase = true) &&
        (port == -1 || port == HTTPS_PORT) && userInfo == null
