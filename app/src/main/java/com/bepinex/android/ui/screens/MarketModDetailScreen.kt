package com.bepinex.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import com.bepinex.android.market.MarketApi
import com.bepinex.android.market.MarketMod
import com.bepinex.android.ui.components.MarkdownContent
import com.bepinex.android.ui.components.plainTextFromMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MarketDetailTab { Info, Download, Source }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketModDetailScreen(
    modId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mod by remember { mutableStateOf(MarketApi.findCachedMod(modId)) }
    var isLoading by remember { mutableStateOf(mod == null) }
    var tab by remember { mutableStateOf(MarketDetailTab.Info) }

    LaunchedEffect(modId) {
        val cached = MarketApi.findCachedMod(modId)
        if (cached != null) {
            mod = cached
            isLoading = false
        } else {
            isLoading = true
            mod = withContext(Dispatchers.IO) { MarketApi.loadModDetail(context, modId) }
            isLoading = false
        }
        if (mod != null) {
            withContext(Dispatchers.IO) { MarketApi.recordView(modId) }
            MarketApi.findCachedMod(modId)?.let { mod = it }
        }
    }

    fun openUrl(url: String): Boolean {
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }.getOrElse {
            Toast.makeText(context, context.getString(R.string.market_open_failed), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openDownload(url: String) {
        if (!openUrl(url)) return
        scope.launch {
            withContext(Dispatchers.IO) { MarketApi.recordDownload(modId) }
            MarketApi.findCachedMod(modId)?.let { mod = it }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            mod == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.market_error),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }
            else -> {
                val item = mod!!
                val primaryUrl = item.downloadDirectUrl.ifBlank { item.downloadCloudUrl }
                val hasSource = item.videoUrl.isNotBlank()
                val summary = remember(item.modDescription) {
                    plainTextFromMarkdown(item.modDescription)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    MarketDetailHeroCard(
                        item = item,
                        summary = summary,
                        primaryUrl = primaryUrl,
                        onInstall = { url -> openDownload(url) }
                    )

                    Spacer(Modifier.height(12.dp))

                    MarketDetailTabBar(
                        selected = tab,
                        onSelect = { tab = it }
                    )

                    if (hasSource) {
                        Spacer(Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                onClick = { tab = MarketDetailTab.Source },
                                shape = RoundedCornerShape(50),
                                color = if (tab == MarketDetailTab.Source) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (tab == MarketDetailTab.Source) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.market_tab_source),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (tab == MarketDetailTab.Source) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    when (tab) {
                        MarketDetailTab.Info -> MarketInfoTab(item)
                        MarketDetailTab.Download -> MarketDownloadTab(item, onOpenUrl = ::openDownload)
                        MarketDetailTab.Source -> MarketSourceTab(item, onOpenUrl = ::openUrl)
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MarketDetailHeroCard(
    item: MarketMod,
    summary: String,
    primaryUrl: String,
    onInstall: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ModIcon(
                    url = item.iconUrl,
                    name = item.displayName,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.displayAuthor.ifBlank {
                                stringResource(R.string.market_unknown_author)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        MarketTagChip(item)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { if (primaryUrl.isNotBlank()) onInstall(primaryUrl) },
                enabled = primaryUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (item.version.isNotBlank()) {
                        stringResource(R.string.market_install, item.version)
                    } else {
                        stringResource(R.string.market_install_no_version)
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        R.string.market_downloads_count,
                        formatDownloadCount(item.downloadCount)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = relativeTimeLabel(item.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MarketDetailTabBar(
    selected: MarketDetailTab,
    onSelect: (MarketDetailTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarketTabIcon(
                selected = selected == MarketDetailTab.Info,
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.market_tab_info),
                onClick = { onSelect(MarketDetailTab.Info) },
                modifier = Modifier.weight(1f)
            )
            MarketTabIcon(
                selected = selected == MarketDetailTab.Download,
                icon = Icons.Outlined.Download,
                label = stringResource(R.string.market_tab_download),
                onClick = { onSelect(MarketDetailTab.Download) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MarketTabIcon(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(2.dp)
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    shape = RoundedCornerShape(1.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {}
            }
        }
    }
}

@Composable
private fun MarketInfoTab(item: MarketMod) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            if (item.modDescription.isBlank()) {
                Text(
                    text = stringResource(R.string.market_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownContent(markdown = item.modDescription)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailRow(
                stringResource(R.string.market_latest_version),
                item.version.ifBlank { "—" }
            )
            DetailRow(
                stringResource(R.string.market_mod_id),
                item.englishName.ifBlank { item.id }
            )
            DetailRow(
                stringResource(R.string.market_created),
                relativeTimeLabel(item.createdAt.ifBlank { item.timestamp })
            )
            if (item.gameName.isNotBlank()) {
                DetailRow(stringResource(R.string.market_detail_game), item.gameName)
            }
            if (item.supportedVersions.isNotBlank()) {
                DetailRow(stringResource(R.string.market_detail_compatible), item.supportedVersions)
            }
        }
    }
}

@Composable
private fun MarketDownloadTab(
    item: MarketMod,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val directUrl = item.downloadDirectUrl
            val cloudUrl = item.downloadCloudUrl
            if (directUrl.isBlank() && cloudUrl.isBlank()) {
                Text(
                    text = stringResource(R.string.market_no_download),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                if (directUrl.isNotBlank()) {
                    Button(
                        onClick = { onOpenUrl(directUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.market_download))
                    }
                }
                if (cloudUrl.isNotBlank() && cloudUrl != directUrl) {
                    if (directUrl.isNotBlank()) Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onOpenUrl(cloudUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.market_cloud_download))
                    }
                }
                Spacer(Modifier.height(16.dp))
                DetailRow(
                    stringResource(R.string.market_detail_version),
                    item.version.ifBlank { "—" }
                )
                Spacer(Modifier.height(8.dp))
                DetailRow(
                    stringResource(R.string.market_detail_size),
                    if (item.fileSize > 0L) formatFileSize(item.fileSize) else "—"
                )
            }
        }
    }
}

@Composable
private fun MarketSourceTab(
    item: MarketMod,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (item.videoUrl.isBlank()) {
                Text(
                    text = stringResource(R.string.market_no_source),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                OutlinedButton(
                    onClick = { onOpenUrl(item.videoUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.market_open_video))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
