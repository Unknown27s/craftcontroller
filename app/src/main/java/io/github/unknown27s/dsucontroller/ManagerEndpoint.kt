package io.github.unknown27s.dsucontroller

import android.content.SharedPreferences
import java.net.URI
import java.net.URLDecoder

/** Parsed address only. Reading or saving it never makes a network connection. */
data class ManagerEndpoint(val ip: String, val port: Int) {
    companion object {
        fun fromQr(payload: String): ManagerEndpoint {
            require(payload.length <= 2048) { "QR payload is too large." }
            val uri = try { URI(payload.trim()) } catch (_: Exception) {
                throw IllegalArgumentException("Not a supported DSU Manager QR code.")
            }
            require(uri.scheme.equals("dsu-controller", ignoreCase = true) &&
                uri.rawAuthority.equals("connect", ignoreCase = true) &&
                uri.rawPath.isNullOrEmpty() && uri.rawFragment == null) {
                "Expected a DSU Manager connection QR, not a layout QR or website."
            }
            val fields = linkedMapOf<String, String>()
            for (part in (uri.rawQuery ?: "").split('&')) {
                val pair = part.split('=', limit = 2)
                require(pair.size == 2) { "Invalid QR query fields." }
                val key: String
                val value: String
                try {
                    key = URLDecoder.decode(pair[0], "UTF-8")
                    value = URLDecoder.decode(pair[1], "UTF-8")
                } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid QR text encoding.")
                }
                require(key in setOf("ip", "port") && key !in fields) {
                    "Unsupported or duplicate QR fields. Nothing was saved."
                }
                fields[key] = value
            }
            require(fields.keys == setOf("ip", "port")) { "QR must contain one IP and one port." }
            return fromManual(fields.getValue("ip"), fields.getValue("port"))
        }

        fun fromManual(ip: String, port: String): ManagerEndpoint {
            require(ip.length <= 15 && port.length <= 5) { "Address or port is too long." }
            val parts = ip.trim().split('.')
            require(parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
                    (part.toIntOrNull()?.let { it in 0..255 } == true)
            }) { "Enter a valid PC IPv4 address." }
            val numbers = parts.map { it.toInt() }
            require(numbers[0] in 1..223 && numbers[0] != 127) {
                "Use the PC's unicast LAN address, not loopback, multicast or an unspecified address."
            }
            require(port.isNotEmpty() && port.all { it in '0'..'9' }) { "Port must be a decimal number." }
            val number = port.toIntOrNull()
            require(number != null && number in 1..65535) { "Port must be between 1 and 65535." }
            return ManagerEndpoint(numbers.joinToString("."), number)
        }

        fun load(prefs: SharedPreferences): ManagerEndpoint? = runCatching {
            fromManual(prefs.getString("manager_ip", "") ?: "", prefs.getInt("manager_port", 0).toString())
        }.getOrNull()
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit().putString("manager_ip", ip).putInt("manager_port", port).apply()
    }

    fun forget(prefs: SharedPreferences) {
        prefs.edit().remove("manager_ip").remove("manager_port").apply()
    }
}
