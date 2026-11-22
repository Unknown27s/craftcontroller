package io.github.unknown27s.dsucontroller.core

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ServerStatus(
    val running: Boolean = false,
    val peers: Int = 0,
    val message: String = "Stopped",
    val received: Long = 0,
    val rejected: Long = 0,
    val sendFailures: Long = 0,
    val lastSendError: String? = null,
)

/** Foreground UDP server. All socket operations execute on Dispatchers.IO. */
class DsuServer internal constructor(private val codec: DsuCodec, private val socketFactory: () -> DatagramSocket) {
    constructor(codec: DsuCodec) : this(codec, { DatagramSocket(null) })
    val input = AtomicReference(ControllerState())
    private val mutableStatus = MutableStateFlow(ServerStatus())
    val status = mutableStatus.asStateFlow()
    private val announcements = ConcurrentLinkedQueue<Announcement>()
    private var job: Job? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var generation = 0

    /** Start explicitly; never bind automatically on app launch. Call on the UI thread. */
    @Synchronized fun start(scope: CoroutineScope, port: Int) {
        require(port in 1024..65535)
        if (job?.isActive == true) return
        val previous = job
        val token = ++generation
        mutableStatus.value = ServerStatus(message = "Starting…")
        job = scope.launch(Dispatchers.IO) {
            // A closed socket can retain its native bind until receive unwinds.
            previous?.join()
            serve(port, token)
        }
    }

    /** Close and cancel immediately; join the returned job to await native socket cleanup. */
    @Synchronized fun stop(reason: String = "Stopped"): Job? {
        generation++
        socket?.close()
        socket = null
        job?.cancel()
        announcements.clear()
        input.set(ControllerState())
        mutableStatus.value = ServerStatus(message = reason)
        return job
    }

    /**
     * Queue a DSU version announcement to a Manager endpoint.
     *
     * Sends from the bound listener socket, because the PC replies to the source
     * port it saw. Returns false when no listener is starting or running.
     */
    @Synchronized fun announce(host: String, port: Int, repeats: Int = 3, spacingMs: Long = 60): Boolean {
        require(port in 1..65535) { "Port must be between 1 and 65535." }
        require(repeats in 1..10) { "Repeat count must be between 1 and 10." }
        require(spacingMs in 0..1_000) { "Spacing must be between 0 and 1000 ms." }
        if (job?.isActive != true) return false
        val target = InetSocketAddress(host, port)
        val base = System.nanoTime()
        val token = generation
        repeat(repeats) { index -> announcements += Announcement(target, base + index * spacingMs * 1_000_000, token) }
        return true
    }

    @Synchronized private fun publish(token: Int, value: ServerStatus) {
        if (generation == token) mutableStatus.value = value
    }

    private suspend fun serve(port: Int, token: Int) {
        val local = try {
            socketFactory()
        } catch (e: IOException) {
            publish(token, ServerStatus(message = "Network error: ${e.message ?: e.javaClass.simpleName}"))
            return
        }
        try {
            synchronized(this) {
                if (generation != token) { local.close(); return }
                socket = local
            }
            local.bind(InetSocketAddress("0.0.0.0", port))
            local.soTimeout = 4
            val peers = linkedMapOf<InetSocketAddress, Peer>()
            val buffer = ByteArray(1025)
            var nextFrame = System.nanoTime()
            var window = nextFrame
            var requests = 0
            var received = 0L
            var rejected = 0L
            var sendFailures = 0L
            var lastSendError: String? = null
            fun sendTo(endpoint: InetSocketAddress, bytes: ByteArray): Boolean {
                return try {
                    send(local, endpoint, bytes)
                    true
                } catch (e: IOException) {
                    // A peer/route failure must not terminate the shared listener.
                    // A closed socket is terminal (including explicit cancellation).
                    if (local.isClosed) throw e
                    sendFailures++
                    lastSendError = e.message ?: e.javaClass.simpleName
                    false
                }
            }
            fun drainAnnouncements(now: Long) {
                while (true) {
                    val next = announcements.peek() ?: return
                    if (next.due > now) return
                    if (!announcements.remove(next)) continue
                    if (next.token != token) continue
                    sendTo(next.target, codec.version())
                }
            }
            drainAnnouncements(System.nanoTime())
            publish(token, ServerStatus(true, message = "Listening on UDP $port"))
            while (currentCoroutineContext().isActive) {
                val now = System.nanoTime()
                if (now - window >= 1_000_000_000L) { window = now; requests = 0 }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    local.receive(packet)
                    received++
                    // Global request ceiling also bounds replies and parser work under flooding.
                    if (++requests <= 100) {
                        val endpoint = packet.socketAddress as InetSocketAddress
                        when (val request = codec.decode(buffer.copyOf(packet.length))) {
                            DsuCodec.Request.Version -> sendTo(endpoint, codec.version())
                            is DsuCodec.Request.Info -> request.slots.distinct().forEach {
                                sendTo(endpoint, codec.info(it))
                            }
                            is DsuCodec.Request.Subscribe -> if (codec.matches(request)) {
                                if (endpoint in peers || peers.size < MAX_PEERS) {
                                    peers.getOrPut(endpoint) { Peer(now) }.refreshed = now
                                }
                            }
                            null -> rejected++
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // A short receive timeout keeps the send cadence independent of requests.
                }
                val tick = System.nanoTime()
                drainAnnouncements(tick)
                peers.entries.removeAll { tick - it.value.refreshed >= PEER_TIMEOUT_NS }
                if (tick >= nextFrame) {
                    val snapshot = input.get()
                    val iterator = peers.entries.iterator()
                    while (iterator.hasNext()) {
                        val (endpoint, peer) = iterator.next()
                        if (!sendTo(endpoint, codec.data(snapshot, peer.sequence++))) iterator.remove()
                    }
                    nextFrame = tick + FRAME_NS
                }
                publish(token, ServerStatus(true, peers.size, "Listening on UDP $port", received, rejected, sendFailures, lastSendError))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            input.set(ControllerState())
            publish(token, ServerStatus(message = "Network error: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            local.close()
            synchronized(this) { if (socket === local) socket = null }
        }
    }

    private fun send(socket: DatagramSocket, target: InetSocketAddress, bytes: ByteArray) {
        socket.send(DatagramPacket(bytes, bytes.size, target))
    }

    private data class Peer(var refreshed: Long, var sequence: Int = 0)

    private data class Announcement(val target: InetSocketAddress, val due: Long, val token: Int)

    companion object {
        const val MAX_PEERS = 8
        const val PEER_TIMEOUT_NS = 5_000_000_000L
        const val FRAME_NS = 16_666_667L // Target ceiling ~60 Hz, not a measured delivery rate.
    }
}
