package com.wind.ggbond.classtime.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wind.ggbond.classtime.service.ApkDownloadManager
import com.wind.ggbond.classtime.ui.viewmodel.UpdateViewModel
import com.wind.ggbond.classtime.util.VersionInfo
import java.io.File

@Composable
fun UpdateDialog(
    versionInfo: VersionInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    downloadState: UpdateViewModel.DownloadState = UpdateViewModel.DownloadState.Idle,
    onDownloadApk: (String) -> Unit = {},
    onInstall: (File) -> Unit = {},
    onResetDownload: () -> Unit = {}
) {
    val forceUpdate = versionInfo.needForceUpdate(currentVersion)
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { if (!forceUpdate && downloadState !is UpdateViewModel.DownloadState.Downloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !forceUpdate && downloadState !is UpdateViewModel.DownloadState.Downloading,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (forceUpdate) "需要更新" else "发现新版本",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (forceUpdate) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.primary
                    )
                    
                    if (versionInfo.publishTime.isNotEmpty()) {
                        Text(
                            text = versionInfo.publishTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v${versionInfo.latestVersion}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "(当前: $currentVersion)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .heightIn(min = 80.dp, max = 200.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = versionInfo.updateLog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                when (downloadState) {
                    is UpdateViewModel.DownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "正在下载... ${downloadState.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is UpdateViewModel.DownloadState.Success -> {
                        Button(
                            onClick = { onInstall(downloadState.file) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("安装更新")
                        }
                    }
                    is UpdateViewModel.DownloadState.Error -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = downloadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onResetDownload) { Text("关闭") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { onDownloadApk(versionInfo.downloadUrl) }) { Text("重试下载") }
                            }
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!forceUpdate) {
                                TextButton(onClick = onDismiss) { Text("稍后提醒") }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(onClick = { onDownloadApk(versionInfo.downloadUrl) }) {
                                Text(if (forceUpdate) "立即更新" else "下载更新")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateCheckLoadingDialog(
    message: String = "正在检查更新..."
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
