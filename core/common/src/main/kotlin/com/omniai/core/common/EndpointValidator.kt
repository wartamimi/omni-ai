package com.omniai.core.common

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Validates user-supplied endpoints. Goals:
 *  - TLS by default (cleartext only for local endpoints when the user opted in),
 *  - block private / loopback / link-local / metadata addresses unless the user explicitly
 *    allows local-network endpoints (e.g. a self-hosted model server),
 *  - reject credentials embedded in URLs.
 * DNS-based checks are re-applied at connection time by the network layer (DNS rebinding).
 */
object EndpointValidator {

    data class Policy(val allowLocalNetwork: Boolean, val allowCleartextLocal: Boolean)

    sealed interface Result {
        data class Valid(val normalizedBaseUrl: String, val isLocal: Boolean) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validateBaseUrl(raw: String, policy: Policy): Result {
        val input = raw.trim().trimEnd('/')
        if (input.isEmpty()) return Result.Invalid("Base URL is required.")
        val uri = try {
            URI(input)
        } catch (e: Exception) {
            return Result.Invalid("Not a valid URL.")
        }
        val scheme = uri.scheme?.lowercase() ?: return Result.Invalid("URL must start with https://")
        if (scheme != "https" && scheme != "http") return Result.Invalid("Only http(s) URLs are allowed.")
        if (uri.rawUserInfo != null) return Result.Invalid("Do not put credentials in the URL. Use the API key field.")
        if (uri.rawQuery != null || uri.rawFragment != null) return Result.Invalid("Base URL must not contain a query or fragment.")
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]")
            ?: return Result.Invalid("URL has no host.")
        val local = isLocalHostName(host) || literalAddress(host)?.let { isRestrictedAddress(it) } == true
        if (isMetadataHost(host)) return Result.Invalid("Cloud metadata endpoints are blocked.")
        if (local && !policy.allowLocalNetwork) {
            return Result.Invalid("Local-network endpoints are disabled. Enable them in Settings › Network if you run a local model server.")
        }
        if (scheme == "http") {
            if (!local) return Result.Invalid("Cleartext http:// is only allowed for local endpoints. Use https://")
            if (!policy.allowCleartextLocal) return Result.Invalid("Cleartext http:// for local endpoints is disabled in Settings › Network.")
        }
        return Result.Valid(input, local)
    }

    fun isLocalHostName(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") ||
            h.endsWith(".lan") || h.endsWith(".home.arpa") || !h.contains('.') && literalAddress(h) == null
    }

    fun isMetadataHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "169.254.169.254" || h == "metadata.google.internal" || h == "fd00:ec2::254" || h == "100.100.100.200"
    }

    /** Parses IPv4/IPv6 literals without DNS lookups. */
    fun literalAddress(host: String): InetAddress? {
        val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        val looksV6 = host.contains(':')
        if (!ipv4.matches(host) && !looksV6) return null
        return try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            null
        }
    }

    /** Loopback, private, link-local, CGNAT, unique-local, multicast and unspecified addresses. */
    fun isRestrictedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isAnyLocalAddress || address.isMulticastAddress
        ) return true
        return when (address) {
            is Inet4Address -> {
                val b = address.address.map { it.toInt() and 0xFF }
                (b[0] == 100 && b[1] in 64..127) || // CGNAT
                    b[0] == 0 || (b[0] == 192 && b[1] == 0 && b[2] == 0) || (b[0] == 198 && b[1] in 18..19)
            }
            is Inet6Address -> {
                val first = address.address[0].toInt() and 0xFF
                (first and 0xFE) == 0xFC // fc00::/7 unique local
            }
            else -> false
        }
    }

    /** Joins base URL and path without double slashes. */
    fun join(baseUrl: String, path: String?): String {
        if (path.isNullOrBlank()) return baseUrl.trimEnd('/')
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
