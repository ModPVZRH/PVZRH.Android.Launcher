package com.bepinex.android.bridge

import android.app.Activity
import com.bepinex.android.BepInExLog
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Loopback TCP server that accepts plugin UI sessions and hosts them in [UiHost]. */
object BridgeServer {

    @Volatile
    var isRunning: Boolean = false
        private set

    private val sessions = ConcurrentHashMap<String, BridgeSession>()
    private val lock = Any()

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var packageName: String? = null
    private var token: String? = null

    private val listener = object : BridgeSession.Listener {
        override fun onHello(session: BridgeSession, pluginId: String, name: String, version: String) {
            BepInExLog.i("Bridge hello: $name ($pluginId) v$version")
            UiHost.upsertPlugin(
                session.sessionId,
                name,
                UiNode(id = "root", type = "column", props = emptyMap(), children = emptyList()),
                session::sendEvent
            )
        }

        override fun onSetTree(session: BridgeSession, tree: UiNode) {
            UiHost.upsertPlugin(session.sessionId, session.pluginName, tree, session::sendEvent)
        }

        override fun onUpdate(session: BridgeSession, widgetId: String, props: Map<String, Any?>) {
            UiHost.updateWidget(session.sessionId, widgetId, props)
        }

        override fun onToast(session: BridgeSession, text: String) {
            UiHost.showToast(text)
        }

        override fun onClose(session: BridgeSession, reason: String?) {
            dropSession(session)
        }

        override fun onDisconnected(session: BridgeSession) {
            dropSession(session)
        }
    }

    /**
     * Binds `127.0.0.1` with an ephemeral port and publishes [BridgeEndpoint].
     * If already running, re-attaches [UiHost] to [activity] and returns.
     */
    fun start(activity: Activity, packageName: String) {
        synchronized(lock) {
            if (isRunning) {
                UiHost.attach(activity)
                return
            }
            try {
                val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                val generatedToken = BridgeDiscovery.generateToken()
                serverSocket = socket
                token = generatedToken
                this.packageName = packageName
                BridgeDiscovery.write(
                    packageName,
                    BridgeEndpoint(
                        protocol = BridgeProtocol.VERSION,
                        host = BridgeDiscovery.HOST_LOOPBACK,
                        port = socket.localPort,
                        token = generatedToken
                    )
                )
                UiHost.attach(activity)
                isRunning = true
                acceptThread = Thread({ acceptLoop(socket) }, "bridge-server").apply {
                    isDaemon = true
                    start()
                }
                BepInExLog.i(
                    "Bridge listening on ${BridgeDiscovery.HOST_LOOPBACK}:${socket.localPort}"
                )
            } catch (t: Throwable) {
                isRunning = false
                val pkg = this.packageName
                token = null
                this.packageName = null
                try {
                    serverSocket?.close()
                } catch (_: Exception) {
                }
                serverSocket = null
                acceptThread = null
                pkg?.let { BridgeDiscovery.clear(it) }
                BepInExLog.e("Bridge server failed to start", t)
            }
        }
    }

    /**
     * Closes the listener and all sessions, detaches [UiHost], and clears discovery.
     */
    fun stop() {
        val open: List<BridgeSession>
        val pkg: String?
        synchronized(lock) {
            isRunning = false
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
            serverSocket = null
            acceptThread = null
            open = sessions.values.toList()
            sessions.clear()
            pkg = packageName
            packageName = null
            token = null
        }
        for (session in open) {
            try {
                session.close()
            } catch (_: Exception) {
            }
            UiHost.removePlugin(session.sessionId)
        }
        UiHost.detach()
        pkg?.let { BridgeDiscovery.clear(it) }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (isRunning) {
            try {
                val client = socket.accept()
                acceptClient(client)
            } catch (e: Exception) {
                if (!isRunning || socket.isClosed) break
                BepInExLog.w("Bridge accept failed: ${e.message}")
            }
        }
    }

    private fun acceptClient(client: Socket) {
        val session: BridgeSession
        synchronized(lock) {
            val expectedToken = token
            if (!isRunning || expectedToken == null) {
                try {
                    client.close()
                } catch (_: Exception) {
                }
                return
            }
            val sessionId = UUID.randomUUID().toString()
            session = BridgeSession(sessionId, client, expectedToken, listener)
            sessions[sessionId] = session
        }
        session.start()
    }

    private fun dropSession(session: BridgeSession) {
        if (sessions.remove(session.sessionId) == null) return
        try {
            session.close()
        } catch (_: Exception) {
        }
        UiHost.removePlugin(session.sessionId)
    }
}
