package com.bepinex.android.save

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuService : android.os.Binder() {

    companion object {
        private const val TAG = "ShizukuService"
        private const val TRANSACTION_EXEC = IBinder.FIRST_CALL_TRANSACTION
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_EXEC) {
            data.enforceInterface("com.bepinex.android.save.ShizukuService")
            val command = data.readString() ?: ""
            val result = execCommand(command)
            reply?.writeNoException()
            reply?.writeInt(if (result.first) 1 else 0)
            reply?.writeString(result.second)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun execCommand(command: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "Executing: $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            Log.d(TAG, "Exit: $exitCode, stdout: ${stdout.take(200)}, stderr: ${stderr.take(200)}")
            if (exitCode == 0) Pair(true, stdout) else Pair(false, stderr.ifEmpty { stdout })
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $command", e)
            Pair(false, e.message ?: "Unknown error")
        }
    }
}
