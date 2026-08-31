package com.bepinex.android.save

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuShell {

    private const val TAG = "ShizukuShell"

    fun exec(command: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "Executing: $command")
            val latch = CountDownLatch(1)
            var binder: IBinder? = null

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    binder = service
                    latch.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            }

            val componentName = ComponentName("com.pvzrh.android.launcher", ShizukuService::class.java.name)
            val args = Shizuku.UserServiceArgs(componentName)
                .daemon(false)
                .tag("shizuku-shell")
                .processNameSuffix("shizuku-shell")
                .version(1)

            Shizuku.bindUserService(args, connection)

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Shizuku.unbindUserService(args, connection, true)
                return Pair(false, "Timeout binding Shizuku service")
            }

            val service = binder ?: run {
                Shizuku.unbindUserService(args, connection, true)
                return Pair(false, "Service binder is null")
            }

            try {
                val data = android.os.Parcel.obtain()
                val reply = android.os.Parcel.obtain()
                try {
                    data.writeInterfaceToken("com.bepinex.android.save.ShizukuService")
                    data.writeString(command)
                    service.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
                    reply.readException()
                    val success = reply.readInt() == 1
                    val output = reply.readString() ?: ""
                    Pair(success, output)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } finally {
                try { Shizuku.unbindUserService(args, connection, true) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $command", e)
            Pair(false, e.message ?: "Unknown error")
        }
    }
}
