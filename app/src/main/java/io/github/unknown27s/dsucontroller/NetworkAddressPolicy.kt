package io.github.unknown27s.dsucontroller

import java.net.Inet4Address
import java.net.InetAddress

/** Only IPv4 address changes invalidate this IPv4-only listener's subscriptions. */
internal fun listenerAddresses(addresses: Iterable<InetAddress>): Set<String> =
    addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.toSet()
