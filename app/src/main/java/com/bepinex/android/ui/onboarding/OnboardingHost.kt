package com.bepinex.android.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bepinex.android.GameDetector
import com.bepinex.android.R
import com.bepinex.android.update.MarkdownText
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 6

@Composable
fun OnboardingHost(
    detectedGames: List<GameDetector.DetectedGame>,
    isScanning: Boolean,
    permissionGranted: Boolean,
    appListPermissionGranted: Boolean,
    onRescan: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestAppListPermission: () -> Unit,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val lastPage = page == PAGE_COUNT - 1
    val showLater = page == 1 || page == 2
    val canGoNext = when (page) {
        2 -> permissionGranted
        else -> true
    }

    fun goTo(target: Int) {
        scope.launch { pagerState.scrollToPage(target.coerceIn(0, PAGE_COUNT - 1)) }
    }

    BackHandler {
        if (page > 0) goTo(page - 1) else onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step_of, page + 1, PAGE_COUNT),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onFinished) {
                    Text(
                        stringResource(
                            if (showLater) R.string.onboarding_later else R.string.onboarding_skip
                        )
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { current ->
                when (current) {
                    0 -> OnboardingImagePage(
                        imageRes = R.drawable.onboarding_welcome,
                        title = stringResource(R.string.onboarding_welcome_title),
                        body = stringResource(R.string.onboarding_welcome_body)
                    )
                    1 -> OnboardingGamePage(
                        detectedGames = detectedGames,
                        isScanning = isScanning,
                        onRescan = onRescan
                    )
                    2 -> OnboardingPermissionPage(
                        permissionGranted = permissionGranted,
                        appListPermissionGranted = appListPermissionGranted,
                        onRequestPermission = onRequestPermission,
                        onRequestAppListPermission = onRequestAppListPermission
                    )
                    3 -> OnboardingImagePage(
                        imageRes = R.drawable.onboarding_first_launch,
                        title = stringResource(R.string.onboarding_first_launch_title),
                        body = stringResource(R.string.onboarding_first_launch_body)
                    )
                    4 -> OnboardingImagePage(
                        imageRes = R.drawable.onboarding_crash_logs,
                        title = stringResource(R.string.onboarding_crash_logs_title),
                        body = stringResource(R.string.onboarding_crash_logs_body)
                    )
                    else -> OnboardingImagePage(
                        imageRes = R.drawable.onboarding_troubleshooting,
                        title = stringResource(R.string.onboarding_troubleshooting_title),
                        body = stringResource(R.string.onboarding_troubleshooting_body),
                        markdown = true
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PAGE_COUNT) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == page) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { goTo(page - 1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Button(
                    onClick = {
                        if (lastPage) onFinished() else goTo(page + 1)
                    },
                    enabled = canGoNext,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            if (lastPage) R.string.onboarding_done else R.string.onboarding_next
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingImagePage(
    imageRes: Int,
    title: String,
    body: String,
    markdown: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        if (markdown) {
            MarkdownText(
                rawText = body,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OnboardingGamePage(
    detectedGames: List<GameDetector.DetectedGame>,
    isScanning: Boolean,
    onRescan: () -> Unit
) {
    val found = detectedGames.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_game),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_game_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        when {
            isScanning -> {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_game_scanning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            found != null -> {
                Text(
                    text = stringResource(R.string.onboarding_game_body_found, found.label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.onboarding_game_body_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = onRescan,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionPage(
    permissionGranted: Boolean,
    appListPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onRequestAppListPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_permission),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PermissionGrantControl(
            granted = permissionGranted,
            grantedText = stringResource(R.string.onboarding_permission_granted),
            grantButtonText = stringResource(R.string.storage_permission_grant),
            onGrant = onRequestPermission
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_applist_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PermissionGrantControl(
            granted = appListPermissionGranted,
            grantedText = stringResource(R.string.onboarding_applist_permission_granted),
            grantButtonText = stringResource(R.string.onboarding_applist_permission_grant),
            onGrant = onRequestAppListPermission
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionGrantControl(
    granted: Boolean,
    grantedText: String,
    grantButtonText: String,
    onGrant: () -> Unit
) {
    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = grantedText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        Button(
            onClick = onGrant,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(grantButtonText)
        }
    }
}
