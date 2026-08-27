package com.bepinex.android.log

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * Transparent overlay activity showing a draggable log FAB on top of the game.
 * Reads BepInEx LogOutput.log directly from external storage.
 */
class LogOverlayActivity : ComponentActivity() {

    companion object {
        var activePackageName: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        val pkg = intent?.getStringExtra("packageName")
            ?: activePackageName
            ?: finish().let { return }
        activePackageName = pkg

        val logFile = File(
            "/storage/emulated/0/PVZRH_Launcher/$pkg/BepInEx/LogOutput.log"
        )

        setContent {
            LogOverlayContent(logFile = logFile, onClose = { finish() })
        }
    }
}

@Composable
private fun LogOverlayContent(logFile: File, onClose: () -> Unit) {
    val lines = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(800f) }
    var lastSize by remember { mutableLongStateOf(0L) }

    // Poll log file
    LaunchedEffect(logFile) {
        while (isActive) {
            try {
                if (logFile.exists() && logFile.length() > lastSize) {
                    RandomAccessFile(logFile, "r").use { raf ->
                        raf.seek(lastSize)
                        val buf = ByteArray((raf.length() - lastSize).toInt())
                        raf.readFully(buf)
                        val newLines = String(buf, Charsets.UTF_8)
                            .lines()
                            .filter { it.isNotBlank() }
                        lines.addAll(newLines)
                        // Keep last 300 lines
                        while (lines.size > 300) lines.removeFirstOrNull()
                        lastSize = logFile.length()
                    }
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Expanded log panel (right side, landscape-ish)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .fillMaxWidth(0.55f)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E2E).copy(alpha = 0.88f)
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Terminal, null, Modifier.size(16.dp),
                            tint = Color(0xFF89B4FA))
                        Spacer(Modifier.width(6.dp))
                        Text("BepInEx Log", color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "Close", Modifier.size(16.dp),
                                tint = Color(0xFFF38BA8))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    if (lines.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Waiting for logs...", color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(lines.size) {
                            if (lines.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(lines.size - 1) }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(lines.size) { idx ->
                                val line = lines[idx]
                                val color = when {
                                    line.contains("[Error") || line.contains("[Fatal") -> Color(0xFFF38BA8)
                                    line.contains("[Warning") -> Color(0xFFFAB387)
                                    line.contains("[Debug") -> Color(0xFF6C7086)
                                    else -> Color(0xFFCDD6F4)
                                }
                                Text(
                                    line, color = color, fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Draggable FAB
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E2E).copy(alpha = 0.75f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { expanded = !expanded })
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Terminal, "Log",
                tint = Color(0xFF89B4FA),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
