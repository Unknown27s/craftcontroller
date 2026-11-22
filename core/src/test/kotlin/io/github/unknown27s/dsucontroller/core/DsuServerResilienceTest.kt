package io.github.unknown27s.dsucontroller.core

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DsuServerResilienceTest {
    private fun DatagramSocket.request(port: Int, type: Int, payload: ByteArray = byteArrayOf()) {
        val bytes = DsuCodecTest.request(type, payload)
        send(DatagramPacket(bytes, bytes.size, InetAddress.getLoopbackAddress(), port))
    }

    private fun DatagramSocket.receiveBytes(): ByteArray {
        soTimeout = 2000
        val packet = DatagramPacket(ByteArray(200), 200)
        receive(packet)
        return packet.data.copyOf(packet.length)
    }

    @Test fun failedReplyLeavesListenerAvailable() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failNext = AtomicBoolean(true)
        val server = DsuServer(DsuCodec(1, ByteArray(6))) {
            object : DatagramSocket(null as SocketAddress?) {
                override fun send(packet: DatagramPacket) {
                    if (failNext.getAndSet(false)) throw IOException("Simulated reply failure")
                    super.send(packet)
                }
            }
        }
        val port = DatagramSocket(0).use { it.localPort }
        try {
            DatagramSocket().use { client ->
                server.start(scope, port)
                withTimeout(5000) { server.status.first { it.running } }
                client.request(port, DsuCodec.VERSION_MESSAGE)
                val status = withTimeout(5000) { server.status.first { it.sendFailures == 1L } }
                assertTrue(status.running)
                assertEquals("Simulated reply failure", status.lastSendError)
                client.request(port, DsuCodec.VERSION_MESSAGE)
                assertEquals(22, client.receiveBytes().size)
            }
        } finally { server.stop()?.join(); scope.cancel() }
    }

    @Test fun failedStreamPeerIsIsolatedAndCanResubscribe() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failPeer = AtomicBoolean(false)
        DatagramSocket().use { affected ->
            DatagramSocket().use { healthy ->
                val server = DsuServer(DsuCodec(1, ByteArray(6))) {
                    object : DatagramSocket(null as SocketAddress?) {
                        override fun send(packet: DatagramPacket) {
                            if (failPeer.get() && packet.port == affected.localPort) throw IOException("Simulated peer route failure")
                            super.send(packet)
                        }
                    }
                }
                val port = DatagramSocket(0).use { it.localPort }
                try {
                    server.start(scope, port)
                    withTimeout(5000) { server.status.first { it.running } }
                    affected.request(port, DsuCodec.DATA_MESSAGE, ByteArray(8))
                    assertEquals(100, affected.receiveBytes().size)
                    healthy.request(port, DsuCodec.DATA_MESSAGE, ByteArray(8))
                    assertEquals(100, healthy.receiveBytes().size)
                    withTimeout(5000) { server.status.first { it.peers == 2 } }
                    failPeer.set(true)
                    val failed = withTimeout(5000) { server.status.first { it.sendFailures > 0 } }
                    assertTrue(failed.running)
                    assertEquals(1, failed.peers)
                    assertEquals(100, healthy.receiveBytes().size)
                    failPeer.set(false)
                    affected.request(port, DsuCodec.DATA_MESSAGE, ByteArray(8))
                    val reconnected = withTimeout(5000) { server.status.first { it.peers == 2 } }
                    assertTrue(reconnected.running)
                    assertEquals(1L, reconnected.sendFailures)
                } finally { server.stop()?.join(); scope.cancel() }
            }
        }
    }
}
