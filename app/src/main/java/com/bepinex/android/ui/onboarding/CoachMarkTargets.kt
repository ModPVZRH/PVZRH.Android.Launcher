package com.bepinex.android.ui.onboarding

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

class CoachMarkTargets {
    var launchRect by mutableStateOf<Rect?>(null)
    var savesRect by mutableStateOf<Rect?>(null)
    var modpacksRect by mutableStateOf<Rect?>(null)
    var actionsFabRect by mutableStateOf<Rect?>(null)
    var vanillaRect by mutableStateOf<Rect?>(null)
    var firstModpackRect by mutableStateOf<Rect?>(null)
    var exportLogsRect by mutableStateOf<Rect?>(null)

    fun updateLaunch(coordinates: LayoutCoordinates) {
        launchRect = coordinates.boundsInWindow()
    }

    fun updateSaves(coordinates: LayoutCoordinates) {
        savesRect = coordinates.boundsInWindow()
    }

    fun updateModpacks(coordinates: LayoutCoordinates) {
        modpacksRect = coordinates.boundsInWindow()
    }

    fun updateActionsFab(coordinates: LayoutCoordinates) {
        actionsFabRect = coordinates.boundsInWindow()
    }

    fun updateVanilla(coordinates: LayoutCoordinates) {
        vanillaRect = coordinates.boundsInWindow()
    }

    fun updateFirstModpack(coordinates: LayoutCoordinates) {
        firstModpackRect = coordinates.boundsInWindow()
    }

    fun updateExportLogs(coordinates: LayoutCoordinates) {
        exportLogsRect = coordinates.boundsInWindow()
    }
}

val LocalCoachMarkTargets = compositionLocalOf<CoachMarkTargets?> { null }
