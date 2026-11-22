package io.github.unknown27s.dsucontroller.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test

class DsuCodecTest {
    private val codec = DsuCodec(0x12345678, byteArrayOf(2, 0, 0, 0, 0, 1))

    @Test fun fixedVersionFixturesMatchPythonStructAndZlib() {
        assertEquals(DsuCodec.Request.Version, codec.decode(hex("44535543e9030400c9bc7bb77856341200001000")))
        assertArrayEquals(hex("44535553e90306005231611b7856341200001000e903"), codec.version())
    }

    @Test fun framingAndNeutralData() {
        val bytes = codec.data(ControllerState(), 0x1020304)
        assertEquals(100, bytes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(84, b.getShort(6).toInt())
        assertEquals(0x100002, b.getInt(16))
        assertEquals(2, bytes[21].toInt())
        assertEquals(2, bytes[22].toInt())
        assertEquals(1, bytes[31].toInt())
        assertEquals(0x1020304, b.getInt(32))
        for (i in 40..43) assertEquals(128, bytes[i].toInt() and 255)
        for (i in 56..99) assertEquals(0, bytes[i].toInt())
        val expected = b.getInt(8)
        bytes.fill(0, 8, 12)
        assertEquals(expected, CRC32().apply { update(bytes) }.value.toInt())
    }

    @Test fun everyButtonHasCorrectDigitalAndPressureFields() {
        for (button in PadButton.entries) {
            val bytes = codec.data(ControllerState(setOf(button)), 0)
            assertEquals(button.mask, bytes[36 + button.bank].toInt() and 255)
            button.analogOffset?.let { assertEquals(255, bytes[20 + it].toInt() and 255) }
        }
        val clamped = codec.data(ControllerState(leftX = -1, rightY = 999), 0)
        assertEquals(0, clamped[40].toInt())
        assertEquals(255, clamped[43].toInt() and 255)
    }

    @Test fun validateInfoAndSubscriptions() {
        assertEquals(DsuCodec.Request.Info(listOf(0, 3)), codec.decode(request(0x100001, byteArrayOf(2, 0, 0, 0, 0, 3))))
        assertNull(codec.decode(request(0x100001, byteArrayOf(-1, -1, -1, -1))))
        assertNull(codec.decode(request(0x100001, byteArrayOf(1, 0, 0, 0, 4))))
        assertNull(codec.decode(request(0x100002, ByteArray(7))))
        assertNull(codec.decode(request(0x100002, byteArrayOf(4, 0, 0, 0, 0, 0, 0, 0))))
        assertTrue(codec.matches(DsuCodec.Request.Subscribe(0, 0, List(6) { 0 })))
        assertFalse(codec.matches(DsuCodec.Request.Subscribe(1, 0, List(6) { 0 })))
        assertTrue(codec.matches(DsuCodec.Request.Subscribe(1, 1, List(6) { 0 })))
        assertTrue(codec.matches(DsuCodec.Request.Subscribe(2, 3, listOf<Byte>(2, 0, 0, 0, 0, 1))))
        assertEquals(3, codec.info(3)[20].toInt())
        assertEquals(0, codec.info(3)[21].toInt())
        assertEquals(DsuCodec.SLOT, codec.info(1)[20].toInt())
        assertEquals(2, codec.info(1)[21].toInt())
    }

    @Test fun malformedTrafficIsRejectedWithoutThrowing() {
        val valid = request(0x100000)
        for (length in 0 until valid.size) assertNull(codec.decode(valid.copyOf(length)))
        for (offset in valid.indices) {
            val bad = valid.copyOf()
            bad[offset] = (bad[offset].toInt() xor 0x80).toByte()
            assertNull(codec.decode(bad))
        }
        assertNull(codec.decode(request(0x123456)))
        val random = java.util.Random(42)
        repeat(10_000) { val b = ByteArray(random.nextInt(1100)); random.nextBytes(b); assertNull(codec.decode(b)) }
    }

    companion object {
        fun request(type: Int, payload: ByteArray = byteArrayOf()): ByteArray {
            val b = ByteBuffer.allocate(20 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            b.put("DSUC".toByteArray()).putShort(1001).putShort((4 + payload.size).toShort())
            b.putInt(0).putInt(42).putInt(type).put(payload)
            b.putInt(8, CRC32().apply { update(b.array()) }.value.toInt())
            return b.array()
        }

        private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
