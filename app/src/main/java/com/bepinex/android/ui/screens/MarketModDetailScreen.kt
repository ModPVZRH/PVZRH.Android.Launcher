package com.bepinex.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.bepinex.android.BepInExLog
import com.bepinex.android.BepInExPaths
import com.bepinex.android.R
import com.bepinex.android.market.MarketApi
import com.bepinex.android.market.MarketMod
import com.bepinex.android.modpack.ModpackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketModDetailScreen(
    modId: String,
    packageName: String,
    onBack: () -> Unit,
    onModpackSelected: (modId: String, modpackName: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mod by remember { mutableStateOf<MarketMod?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showModpackPicker by remember { mutableStateOf(false) }
    var availableModpacks by remember { mutableStateOf<List<String>>(emptyList()) }

    val df = remember { DecimalFormat("#,###") }

    LaunchedEffect(modId) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            MarketApi.fetchModDetail(context, modId)
        }
        mod = result
        isLoading = false
    }

    fun downloadFile(url: String, outputFile: File, onComplete: () -> Unit) {
        scope.launch {
            isDownloading = true
            downloadProgress = 0f
            try {
                withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 30000
                    conn.readTimeout = 300000
                    conn.instanceFollowRedirects = true

                    val totalSize = conn.contentLength.toLong()
                    outputFile.parentFile?.mkdirs()

                    conn.inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (totalSize > 0) {
                                    downloadProgress = totalRead.toFloat() / totalSize
                                }
                            }
                        }
                    }
                }
                onComplete()
            } catch (e: Exception) {
                BepInExLog.e("Download failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.market_download_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                isDownloading = false
                downloadProgress = 0f
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mod?.modName ?: stringResource(R.string.market_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                    Text(
                        text = stringResource(R.string.market_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                val m = mod!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = if (m.isPreposition)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (m.isPreposition)
                                stringResource(R.string.market_type_modpack)
                            else
                                stringResource(R.string.market_type_mod),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (m.isPreposition)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary
                        )
                        if (m.isFeatured) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("★ Featured", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = m.modName.ifEmpty { m.englishName },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (m.englishName.isNotEmpty() && m.modName.isNotEmpty() && m.englishName != m.modName) {
                        Text(
                            text = m.englishName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            DetailRow(stringResource(R.string.market_detail_version), m.version)
                            DetailRow(stringResource(R.string.market_detail_size), formatFileSize(m.fileSize, df))
                            DetailRow(stringResource(R.string.market_detail_downloads), m.downloadCount)
                            if (m.gameName.isNotEmpty()) {
                                DetailRow(stringResource(R.string.market_detail_game), m.gameName)
                            }
                            if (m.supportedVersions.isNotEmpty()) {
                                DetailRow(stringResource(R.string.market_detail_compatible), m.supportedVersions)
                            }
                        }
                    }

                    if (m.modDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.market_detail_description),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = m.modDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (m.videoUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.market_detail_video),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = m.videoUrl,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isDownloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(progress = { downloadProgress })
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        if (m.downloadDirectUrl.isNotEmpty() && m.showDirectUrl) {
                            Button(
                                onClick = {
                                    if (m.isPreposition) {
                                        val marketDir = BepInExPaths.getMarketDir()
                                        val fileName = "${m.id}_${m.modName.ifEmpty { m.englishName }}.zip"
                                        val outputFile = File(marketDir, fileName)
                                        downloadFile(m.downloadDirectUrl, outputFile) {
                                            onModpackSelected(m.id, "")
                                        }
                                    } else {
                                        val modpackDir = File(BepInExPaths.getGameRootDir(packageName), "modpacks")
                                        availableModpacks = modpackDir.listFiles()
                                            ?.filter { it.isDirectory && File(it, "modpack.json").exists() }
                                            ?.map { it.name }
                                            ?: emptyList()
                                        showModpackPicker = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.market_download))
                            }
                        }

                        if (m.downloadCloudUrl.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(m.downloadCloudUrl)
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.market_cloud_download))
                            }
                        }

                        if (m.downloadDirectUrl.isEmpty() && m.downloadCloudUrl.isEmpty()) {
                            Text(
                                text = stringResource(R.string.market_no_download),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showModpackPicker && mod != null) {
        val m = mod!!
        AlertDialog(
            onDismissRequest = { showModpackPicker = false },
            title = { Text(stringResource(R.string.market_select_modpack)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (availableModpacks.isEmpty()) {
                        Text(stringResource(R.string.market_no_modpacks))
                    } else {
                        availableModpacks.forEach { name ->
                            ListItem(
                                headlineContent = { Text(name) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showModpackPicker = false
                                        val marketDir = BepInExPaths.getMarketDir()
                                        val fileName = "${m.id}_${m.modName.ifEmpty { m.englishName }}.dll"
                                        val outputFile = File(marketDir, fileName)
                                        downloadFile(m.downloadDirectUrl, outputFile) {
                                            onModpackSelected(m.id, name)
                                        }
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModpackPicker = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
