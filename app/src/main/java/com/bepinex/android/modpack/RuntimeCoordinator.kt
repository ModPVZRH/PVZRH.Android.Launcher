package com.bepinex.android.modpack

import android.content.Context
import com.bepinex.android.BepInExLog
import com.bepinex.android.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class RuntimePhase { Loading, Ready, Switching, Saving, Failed }

data class RuntimeState(
    val appliedModpack: String? = null,
    val requestedModpack: String? = null,
    val phase: RuntimePhase = RuntimePhase.Loading,
    val error: String? = null
) {
    val isBusy: Boolean get() = phase in setOf(RuntimePhase.Loading, RuntimePhase.Switching, RuntimePhase.Saving)
}

/** Owns runtime operations independently of Activity/Compose lifetime. Disk remains authoritative. */
object RuntimeCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Mutex()
    private val states = ConcurrentHashMap<String, MutableStateFlow<RuntimeState>>()
    private val manager = ModpackManager()

    fun state(packageName: String): StateFlow<RuntimeState> = mutableState(packageName)

    private fun mutableState(packageName: String) =
        states.getOrPut(packageName) { MutableStateFlow(RuntimeState()) }

    suspend fun refresh(context: Context, packageName: String) = operations.withLock {
        withContext(Dispatchers.IO) {
            try {
                publishOwner(context.applicationContext, packageName)
            } catch (error: Exception) {
                BepInExLog.e("Cannot read runtime state", error)
                mutableState(packageName).value = mutableState(packageName).value.copy(
                    phase = RuntimePhase.Failed, error = error.message)
            }
        }
    }

    fun requestSwitch(context: Context, packageName: String, target: String?) {
        val current = mutableState(packageName).value
        if (current.phase == RuntimePhase.Ready && current.appliedModpack == target) return
        val app = context.applicationContext
        scope.launch { switch(app, packageName, target) }
    }

    fun onRenamed(context: Context, packageName: String, old: String, new: String) {
        val app = context.applicationContext
        if (AppSettings.getActiveModpack(app, packageName) == old) {
            AppSettings.setActiveModpack(app, packageName, new)
        }
        scope.launch { refresh(app, packageName) }
    }

    suspend fun switch(context: Context, packageName: String, target: String?): Boolean = operations.withLock {
        val app = context.applicationContext
        val state = mutableState(packageName)
        state.value = state.value.copy(requestedModpack = target, phase = RuntimePhase.Switching, error = null)
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val previous = manager.activeRuntimeModpack(packageName, AppSettings.getActiveModpack(app, packageName))
                state.value = state.value.copy(appliedModpack = previous)
                manager.switchRuntimeBlocking(packageName, previous, target?.ifEmpty { null })
                publishOwner(app, packageName)
                true
            } catch (error: Exception) {
                BepInExLog.e("Runtime switch failed", error)
                state.value = state.value.copy(requestedModpack = state.value.appliedModpack,
                    phase = RuntimePhase.Failed, error = error.message)
                false
            }
        }
    }

    fun saveAsync(context: Context, packageName: String, expectedOwner: String? = null, checkOwner: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            operations.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        val owner = manager.activeRuntimeModpack(packageName, AppSettings.getActiveModpack(app, packageName))
                        if (checkOwner && owner != expectedOwner) return@withContext
                        val state = mutableState(packageName)
                        state.value = state.value.copy(phase = RuntimePhase.Saving)
                        manager.persistRuntimeState(packageName, owner)
                        publishOwner(app, packageName)
                    } catch (error: Exception) {
                        BepInExLog.e("Runtime save failed", error)
                        mutableState(packageName).value = mutableState(packageName).value.copy(
                            phase = RuntimePhase.Failed, error = error.message)
                    }
                }
            }
        }
    }

    suspend fun delete(context: Context, packageName: String, name: String): Boolean = operations.withLock {
        mutableState(packageName).value = mutableState(packageName).value.copy(phase = RuntimePhase.Switching)
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val previous = manager.activeRuntimeModpack(packageName, AppSettings.getActiveModpack(context, packageName))
                if (previous == name) manager.switchRuntimeBlocking(packageName, previous, null)
                val deleted = manager.deleteModpack(packageName, name)
                publishOwner(context, packageName)
                deleted
            } catch (error: Exception) {
                BepInExLog.e("Cannot delete modpack", error)
                mutableState(packageName).value = mutableState(packageName).value.copy(
                    phase = RuntimePhase.Failed, error = error.message)
                false
            }
        }
    }

    private fun publishOwner(context: Context, packageName: String) {
        val owner = manager.activeRuntimeModpack(packageName, AppSettings.getActiveModpack(context, packageName))
        AppSettings.setActiveModpack(context, packageName, owner)
        mutableState(packageName).value = RuntimeState(owner, owner, RuntimePhase.Ready)
    }
}
