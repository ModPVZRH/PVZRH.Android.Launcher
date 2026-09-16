package com.bepinex.android.bridge

import com.bepinex.android.BepInExLog
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Authenticated framed-JSON TCP session with a single in-game plugin.
 */
class BridgeSession(
    val sessionId: String,
    private val socket: Socket,
    private val expectedToken: String,
    private val listener: Listener
) {
    /** Inbound protocol and disconnect callbacks. */
    interface Listener {
        fun onHello(session: BridgeSession, pluginId: String, name: String, version: String)
        fun onSetTree(session: BridgeSession, tree: UiNode)
        fun onUpdate(session: BridgeSession, widgetId: String, props: Map<String, Any?>)
        fun onToast(session: BridgeSession, text: String)
        fun onClose(session: BridgeSession, reason: String?)
        fun onDisconnected(session: BridgeSession)
    }

    @Volatile var pluginId: String = ""
    @Volatile var pluginName: String = ""
    @Volatile var pluginVersion: String = ""
    @Volatile var authenticated: Boolean = false
        private set

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val disconnected = AtomicBoolean(false)

    @Volatile
    private var readThread: Thread? = null

    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bridge-session-write-$sessionId").apply { isDaemon = true }
    }

    /** Starts a daemon thread that authenticates, then dispatches inbound frames. */
    fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        readThread = Thread({ readLoop() }, "bridge-session-$sessionId").apply {
            isDaemon = true
            start()
        }
    }

    /** Queues a widget event. Safe to call from the Android main thread. */
    fun sendEvent(widgetId: String, name: String, value: Any?) {
        if (!authenticated || closed.get()) return
        enqueueWrite(BridgeProtocol.encodeOutbound(BridgeOutbound.Event(widgetId, name, value)))
    }

    /** Queues a lifecycle state. Safe to call from the Android main thread. */
    fun sendLifecycle(state: String) {
        if (!authenticated || closed.get()) return
        enqueueWrite(BridgeProtocol.encodeOutbound(BridgeOutbound.Lifecycle(state)))
    }

    /** Best-effort "destroyed" lifecycle, then closes the socket and interrupts the reader. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        enqueueWrite(BridgeProtocol.encodeOutbound(BridgeOutbound.Lifecycle("destroyed")))
        writeExecutor.execute {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
        writeExecutor.shutdown()
        readThread?.interrupt()
    }

    private fun readLoop() {
        try {
            val first = readInbound()
            if (first == null) {
                BepInExLog.w("Bridge session $sessionId: closed before hello")
                return
            }
            if (first !is BridgeInbound.Hello || !tokenMatches(first.token)) {
                BepInExLog.w("Bridge session $sessionId: hello rejected")
                return
            }

            pluginId = first.pluginId
            pluginName = first.name
            pluginVersion = first.version
            authenticated = true
            writeJson(BridgeProtocol.encodeOutbound(BridgeOutbound.HelloAck(sessionId)))
            emit { onHello(this@BridgeSession, first.pluginId, first.name, first.version) }

            while (!closed.get() && !socket.isClosed) {
                val msg = readInbound() ?: break
                if (!dispatch(msg)) break
            }
        } catch (_: IOException) {
            if (!closed.get()) {
                BepInExLog.w("Bridge session $sessionId: connection lost")
            }
        } catch (t: Throwable) {
            BepInExLog.e("Bridge session $sessionId: read loop failed", t)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            notifyDisconnected()
        }
    }

    private fun dispatch(msg: BridgeInbound): Boolean {
        when (msg) {
            is BridgeInbound.Hello ->
                BepInExLog.w("Bridge session $sessionId: unexpected hello ignored")
            is BridgeInbound.SetTree ->
                emit { onSetTree(this@BridgeSession, UiNode.fromJson(msg.tree)) }
            is BridgeInbound.Update ->
                emit { onUpdate(this@BridgeSession, msg.widgetId, UiNode.propsFromJson(msg.props)) }
            is BridgeInbound.Toast ->
                emit { onToast(this@BridgeSession, msg.text) }
            is BridgeInbound.Close -> {
                emit { onClose(this@BridgeSession, msg.reason) }
                return false
            }
            is BridgeInbound.Unknown ->
                BepInExLog.w("Bridge session $sessionId: unknown inbound ignored: $msg")
        }
        return true
    }

    private fun readInbound(): BridgeInbound? {
        val bytes = BridgeProtocol.readFrame(socket.getInputStream()) ?: return null
        val json = String(bytes, Charsets.UTF_8)
        return BridgeProtocol.parseInbound(json)
    }

    private fun enqueueWrite(json: String) {
        try {
            writeExecutor.execute {
                try {
                    writeJson(json)
                } catch (e: IOException) {
                    if (!closed.get()) {
                        BepInExLog.w("Bridge session $sessionId: write failed: ${e.message}")
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    @Synchronized
    private fun writeJson(json: String) {
        if (socket.isClosed) return
        BridgeProtocol.writeFrame(socket.getOutputStream(), json.toByteArray(Charsets.UTF_8))
    }

    private fun tokenMatches(token: String): Boolean =
        MessageDigest.isEqual(
            expectedToken.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8)
        )

    private inline fun emit(block: Listener.() -> Unit) {
        if (closed.get()) return
        try {
            listener.block()
        } catch (t: Throwable) {
            BepInExLog.e("Bridge session $sessionId: listener error", t)
        }
    }

    private fun notifyDisconnected() {
        if (!disconnected.compareAndSet(false, true)) return
        try {
            listener.onDisconnected(this)
        } catch (t: Throwable) {
            BepInExLog.e("Bridge session $sessionId: onDisconnected error", t)
        }
    }
}
