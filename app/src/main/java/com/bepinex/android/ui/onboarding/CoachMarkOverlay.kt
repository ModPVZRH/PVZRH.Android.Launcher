package com.bepinex.android.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bepinex.android.R

data class CoachMarkStep(
    val rect: Rect?,
    val title: String,
    val body: String
)

@Composable
fun CoachMarkOverlay(
    steps: List<CoachMarkStep>,
    onFinished: () -> Unit,
    onSkip: () -> Unit = onFinished,
    lastStepContinues: Boolean = false
) {
    val visibleSteps = steps.filter { step ->
        val rect = step.rect
        rect != null && rect.width > 8f && rect.height > 8f
    }
    if (visibleSteps.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, visibleSteps.lastIndex)
    val step = visibleSteps[safeIndex]
    val last = safeIndex == visibleSteps.lastIndex
    val view = LocalView.current
    val hole = step.rect?.let { windowRect ->
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        Rect(
            windowRect.left - loc[0],
            windowRect.top - loc[1],
            windowRect.right - loc[0],
            windowRect.bottom - loc[1]
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
    ) {
        val density = LocalDensity.current
        val pad = with(density) { 8.dp.toPx() }
        val inflated = hole?.inflate(pad)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.62f))
            if (inflated != null) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(inflated.left, inflated.top),
                    size = Size(inflated.width, inflated.height),
                    cornerRadius = CornerRadius(28f, 28f),
                    blendMode = BlendMode.Clear
                )
            }
        }

        val holeInLowerHalf = inflated != null && inflated.center.y > constraints.maxHeight / 2f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .align(if (holeInLowerHalf) Alignment.TopCenter else Alignment.BottomCenter)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.coach_skip))
                        }
                        Button(
                            onClick = {
                                if (last) onFinished() else index = safeIndex + 1
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (last && !lastStepContinues) {
                                        R.string.coach_done
                                    } else {
                                        R.string.coach_next
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
