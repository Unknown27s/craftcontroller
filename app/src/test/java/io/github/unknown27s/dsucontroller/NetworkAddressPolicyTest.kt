package io.github.unknown27s.dsucontroller

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class NetworkAddressPolicyTest {
    private fun addresses(vararg values: String) = values.map { InetAddress.getByName(it) }

    @Test fun ipv6RotationDoesNotChangeListenerAddresses() {
        val before = listenerAddresses(addresses("192.0.2.10", "2001:db8::1"))
        val after = listenerAddresses(addresses("2001:db8::2", "192.0.2.10", "fe80::1"))
        assertEquals(setOf("192.0.2.10"), before)
        assertEquals(before, after)
    }

    @Test fun ipv4ReplacementIsDetected() {
        assertNotEquals(listenerAddresses(addresses("192.0.2.10")), listenerAddresses(addresses("192.0.2.11")))
    }

    @Test fun ipv4LossIsDetected() {
        assertTrue(listenerAddresses(addresses("2001:db8::1")).isEmpty())
        assertNotEquals(listenerAddresses(addresses("192.0.2.10")), listenerAddresses(emptyList()))
    }
}
