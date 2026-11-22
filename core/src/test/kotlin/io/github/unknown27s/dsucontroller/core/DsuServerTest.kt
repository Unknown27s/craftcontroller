package io.github.unknown27s.dsucontroller.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DsuServerTest {
    @Test fun liveUdpHandshakeStreamExpiryAndStop() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = DsuServer(DsuCodec(123, byteArrayOf(2, 0, 0, 0, 0, 1)))
        val port = DatagramSocket(0).use { it.localPort }
        DatagramSocket().use { client ->
            client.soTimeout = 2000
            try {
                server.start(scope, port)
                withTimeout(5000) { server.status.first { it.running } }
                fun send(bytes: ByteArray) = client.send(DatagramPacket(bytes, bytes.size, InetAddress.getLoopbackAddress(), port))
                fun receive(): ByteArray {
                    val packet = DatagramPacket(ByteArray(200), 200)
                    client.receive(packet)
                    return packet.data.copyOf(packet.length)
                }
                send(DsuCodecTest.request(0x100000))
                assertEquals(22, receive().size)
                send(DsuCodecTest.request(0x100001, byteArrayOf(1, 0, 0, 0, 0)))
                assertEquals(32, receive().size)
                server.input.set(ControllerState(setOf(PadButton.A), leftX = 255))
                send(DsuCodecTest.request(0x100002, ByteArray(8)))
                val first = receive()
                val second = receive()
                assertEquals(100, first.size)
                assertEquals(64, first[37].toInt())
                assertEquals(255, first[40].toInt() and 255)
                fun seq(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(32)
                assertEquals(seq(first) + 1, seq(second))
                withTimeout(7000) { server.status.first { it.peers == 0 } }
                server.stop()?.join()
                assertFalse(server.status.value.running)
                assertEquals(ControllerState(), server.input.get())
                DatagramSocket(port).use { assertTrue(it.isBound) }
            } finally { server.stop(); scope.cancel() }
        }
    }

    @Test fun occupiedPortReportsFailure() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = DsuServer(DsuCodec(1, ByteArray(6)))
        try {
            DatagramSocket(0).use { occupied ->
                server.start(scope, occupied.localPort)
                val result = withTimeout(5000) { server.status.first { it.message.startsWith("Network error") } }
                assertFalse(result.running)
            }
        } finally { server.stop(); scope.cancel() }
    }

    @Test fun immediateRestartWaitsForOldSocketCleanup() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = DsuServer(DsuCodec(1, ByteArray(6)))
        val port = DatagramSocket(0).use { it.localPort }
        try {
            repeat(5) {
                server.start(scope, port)
                withTimeout(5000) { server.status.first { it.running } }
                server.stop()
            }
            server.stop()?.join()
        } finally { server.stop(); scope.cancel() }
    }
}
