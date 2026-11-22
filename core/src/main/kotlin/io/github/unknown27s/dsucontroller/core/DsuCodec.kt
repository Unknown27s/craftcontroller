package io.github.unknown27s.dsucontroller.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Independent implementation of the documented DSU 1001 core wire format. */
class DsuCodec(private val serverId: Int, private val mac: ByteArray) {
    init { require(mac.size == 6) }

    sealed interface Request {
        data object Version : Request
        data class Info(val slots: List<Int>) : Request
        data class Subscribe(val flags: Int, val slot: Int, val mac: List<Byte>) : Request
    }

    /** Reject malformed datagrams before reading message-specific fields. */
    fun decode(bytes: ByteArray): Request? {
        if (bytes.size !in 20..1024 || !bytes.copyOfRange(0, 4).contentEquals("DSUC".toByteArray())) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if ((b.getShort(4).toInt() and 65535) != VERSION) return null
        val size = 16 + (b.getShort(6).toInt() and 65535)
        if (size !in 20..bytes.size) return null
        val packet = bytes.copyOf(size)
        packet.fill(0, 8, 12)
        if (CRC32().apply { update(packet) }.value.toInt() != b.getInt(8)) return null
        return when (b.getInt(16)) {
            VERSION_MESSAGE -> if (size == 20) Request.Version else null
            INFO_MESSAGE -> {
                if (size < 25) return null
                val count = b.getInt(20)
                if (count !in 1..4 || size < 24 + count || size > 28) return null
                val slots = (0 until count).map { b.get(24 + it).toInt() and 255 }
                if (slots.any { it !in 0..3 }) null else Request.Info(slots)
            }
            DATA_MESSAGE -> {
                if (size != 28) return null
                val flags = b.get(20).toInt() and 255
                val slot = b.get(21).toInt() and 255
                if (flags > 3 || (flags and 1 != 0 && slot > 3)) return null
                Request.Subscribe(flags, slot, bytes.copyOfRange(22, 28).toList())
            }
            else -> null
        }
    }

    fun matches(request: Request.Subscribe): Boolean =
        request.flags == 0 ||
            (request.flags and 1 != 0 && request.slot == SLOT) ||
            (request.flags and 2 != 0 && request.mac == mac.toList())

    fun version(): ByteArray = frame(VERSION_MESSAGE, ByteBuffer.allocate(2).little().putShort(VERSION.toShort()).array())

    fun info(slot: Int): ByteArray {
        require(slot in 0..3)
        return frame(INFO_MESSAGE, ByteArray(12).also { identity(it, slot) })
    }

    fun data(state: ControllerState, sequence: Int): ByteArray {
        val p = ByteArray(80)
        identity(p, SLOT)
        p[11] = 1
        ByteBuffer.wrap(p).little().putInt(12, sequence)
        // The reference app sends epoch microseconds. Keep the field live
        // rather than zero so clients that use it for ordering see new frames.
        ByteBuffer.wrap(p).little().putLong(48, System.currentTimeMillis() * 1000L)
        for (button in state.buttons) {
            val offset = 16 + button.bank
            p[offset] = (p[offset].toInt() or button.mask).toByte()
            button.analogOffset?.let { p[it] = 255.toByte() }
        }
        listOf(state.leftX, state.leftY, state.rightX, state.rightY).forEachIndexed { i, value ->
            p[20 + i] = value.coerceIn(0, 255).toByte()
        }
        return frame(DATA_MESSAGE, p)
    }

    private fun identity(p: ByteArray, slot: Int) {
        p[0] = slot.toByte()
        if (slot != SLOT) return
        p[1] = 2 // Connected.
        p[2] = 2 // Reference app model; motion values below stay zero.
        p[3] = 1 // Matches the reference app's connection field.
        mac.copyInto(p, 4)
        p[10] = 5 // Compatibility constant from the reference app, not a phone reading.
    }

    private fun frame(type: Int, payload: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(20 + payload.size).little()
        b.put("DSUS".toByteArray()).putShort(VERSION.toShort()).putShort((4 + payload.size).toShort())
        b.putInt(0).putInt(serverId).putInt(type).put(payload)
        b.putInt(8, CRC32().apply { update(b.array()) }.value.toInt())
        return b.array()
    }

    companion object {
        const val VERSION = 1001

        /**
         * DSU Manager subscribes to slot 1 (the original app advertises DSU001
         * there). Slot 0 would be rejected and would receive no data.
         */
        const val SLOT = 1
        const val VERSION_MESSAGE = 0x100000
        const val INFO_MESSAGE = 0x100001
        const val DATA_MESSAGE = 0x100002
        private fun ByteBuffer.little(): ByteBuffer = order(ByteOrder.LITTLE_ENDIAN)
    }
}
