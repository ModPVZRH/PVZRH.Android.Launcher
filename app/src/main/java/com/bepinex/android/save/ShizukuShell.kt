package com.bepinex.android.save

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object ShizukuShell {

    private const val TAG = "ShizukuShell"
    private const val BIND_TIMEOUT_SEC = 10L

    private val cachedBinder = AtomicReference<IBinder?>(null)
    private val cachedArgs = AtomicReference<Shizuku.UserServiceArgs?>(null)
    private val cachedConnection = AtomicReference<ServiceConnection?>(null)
    private var bindLatch = CountDownLatch(1)
    @Volatile private var bound = false

    private fun ensureBound(packageName: String): IBinder? {
        if (bound && cachedBinder.get() != null) return cachedBinder.get()

        synchronized(this) {
            if (bound && cachedBinder.get() != null) return cachedBinder.get()

            unbindLocked()

            val componentName = ComponentName(packageName, ShizukuService::class.java.name)
            val args = Shizuku.UserServiceArgs(componentName)
                .daemon(false)
                .tag("shizuku-shell")
                .processNameSuffix("shizuku-shell")
                .version(1)

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    cachedBinder.set(service)
                    bindLatch.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    cachedBinder.set(null)
                    bound = false
                }
            }

            cachedArgs.set(args)
            cachedConnection.set(connection)

            try {
                Shizuku.bindUserService(args, connection)
                if (!bindLatch.await(BIND_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    Log.e(TAG, "Timeout binding Shizuku service")
                    unbindLocked()
                    return null
                }
                bound = true
                return cachedBinder.get()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind Shizuku service", e)
                unbindLocked()
                return null
            }
        }
    }

    private fun unbindLocked() {
        val args = cachedArgs.getAndSet(null)
        val conn = cachedConnection.getAndSet(null)
        if (args != null && conn != null) {
            try { Shizuku.unbindUserService(args, conn, true) } catch (_: Exception) {}
        }
        cachedBinder.set(null)
        bound = false
        bindLatch = CountDownLatch(1)
    }

    fun exec(command: String, packageName: String = "com.pvzrh.android.launcher"): Pair<Boolean, String> =
        synchronized(this) {
            val binder = ensureBound(packageName)
                ?: return@synchronized Pair(false, "Failed to bind Shizuku service")
            try {
                val data = android.os.Parcel.obtain()
                val reply = android.os.Parcel.obtain()
                try {
                    data.writeInterfaceToken("com.bepinex.android.save.ShizukuService")
                    data.writeString(command)
                    if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) {
                        throw android.os.RemoteException("Shizuku service rejected transaction")
                    }
                    reply.readException()
                    val success = reply.readInt() == 1
                    val output = reply.readString() ?: ""
                    if (!success) Log.w(TAG, "Command failed: $command, output: ${output.take(200)}")
                    Pair(success, output)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transact failed: $command", e)
                cachedBinder.set(null)
                bound = false
                Pair(false, e.message ?: "Unknown error")
            }
        }

    fun execOrThrow(command: String, packageName: String = "com.pvzrh.android.launcher"): String {
        val (success, output) = exec(command, packageName)
        if (!success) throw RuntimeException("Command failed: $command\n$output")
        return output
    }

    fun release() {
        synchronized(this) { unbindLocked() }
    }
}
