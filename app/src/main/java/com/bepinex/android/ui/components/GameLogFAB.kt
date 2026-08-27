package com.bepinex.android.ui.components

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
import com.bepinex.android.log.BepInExLogReader
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * In-game log overlay: a small draggable FAB that snaps to screen edges.
 * Tap to expand a landscape semi-transparent log panel; tap again to collapse.
 */
@Composable
fun GameLogFAB(
    logLines: List<BepInExLogReader.LogLine>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Draggable offset in pixels
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // Expanded log panel (landscape, semi-transparent, right side)
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
                        IconButton(onClick = { expanded = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "Close", Modifier.size(16.dp),
                                tint = Color(0xFFF38BA8))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    if (logLines.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Waiting for logs...", color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(logLines.size) {
                            if (logLines.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(logLines.size - 1) }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(logLines.takeLast(200).size) { idx ->
                                val line = logLines.takeLast(200)[idx]
                                val lineColor = when (line.level) {
                                    BepInExLogReader.LogLevel.FATAL,
                                    BepInExLogReader.LogLevel.ERROR -> Color(0xFFF38BA8)
                                    BepInExLogReader.LogLevel.WARNING -> Color(0xFFFAB387)
                                    BepInExLogReader.LogLevel.DEBUG -> Color(0xFF6C7086)
                                    else -> Color(0xFFCDD6F4)
                                }
                                Text(
                                    "[${line.level.label[0]}] ${line.source.ifEmpty { "" }} ${line.message}",
                                    color = lineColor, fontSize = 10.sp,
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

        // Draggable FAB (tap = toggle panel, drag = move)
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
                Icons.Filled.Terminal,
                contentDescription = "Log",
                tint = Color(0xFF89B4FA),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
