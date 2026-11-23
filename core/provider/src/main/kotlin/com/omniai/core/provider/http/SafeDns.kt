package com.omniai.core.provider.http

import com.omniai.core.common.EndpointValidator
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class BlockedAddressException(message: String) : UnknownHostException(message)

/**
 * DNS resolver that re-checks resolved addresses at connection time, so a public hostname
 * cannot be pointed at a private address (DNS rebinding / SSRF) unless local endpoints are allowed.
 */
class SafeDns(
    private val allowLocalNetwork: () -> Boolean,
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (EndpointValidator.isMetadataHost(hostname)) throw BlockedAddressException("Blocked metadata host")
        val addresses = delegate.lookup(hostname)
        if (allowLocalNetwork()) return addresses.filterNot { EndpointValidator.isMetadataHost(it.hostAddress ?: "") }
        val allowed = addresses.filterNot { EndpointValidator.isRestrictedAddress(it) }
        if (allowed.isEmpty()) {
            throw BlockedAddressException("$hostname resolves to a private or local address. Enable local-network endpoints in Settings to allow it.")
        }
        return allowed
    }
}
