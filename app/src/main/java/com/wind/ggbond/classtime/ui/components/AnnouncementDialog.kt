// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wind.ggbond.classtime.util.AnnouncementInfo

@Composable
fun AnnouncementDialog(
    announcement: AnnouncementInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onUrlClick: ((String) -> Unit)? = null
) {
    val isForce = announcement.type == AnnouncementInfo.AnnouncementType.FORCE
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { if (!isForce) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isForce,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (announcement.type) {
                            AnnouncementInfo.AnnouncementType.FORCE -> "重要公告"
                            AnnouncementInfo.AnnouncementType.IMPORTANT -> "公告"
                            else -> "公告"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isForce) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.primary
                    )

                    if (!isForce) {
                        IconButton(onClick = onDismiss, Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", Modifier.size(18.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(28.dp))
                    }
                }

                if (announcement.publishTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = announcement.publishTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .heightIn(min = 80.dp, max = 300.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = announcement.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }

                if (announcement.url.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "查看详情 >",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(enabled = onUrlClick != null) {
                            onUrlClick?.invoke(announcement.url)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isForce) {
                        TextButton(onClick = onDismiss) {
                            Text("知道了")
                        }
                    } else {
                        Button(onClick = onDismiss) {
                            Text("我已了解")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementListDialog(
    announcements: List<AnnouncementInfo>,
    currentVersion: String,
    onDismiss: () -> Unit,
    onUrlClick: ((String) -> Unit)? = null
) {
    var currentIndex by remember { mutableStateOf(0) }

    if (currentIndex < announcements.size) {
        AnnouncementDialog(
            announcement = announcements[currentIndex],
            currentVersion = currentVersion,
            onDismiss = {
                currentIndex++
                if (currentIndex >= announcements.size) onDismiss()
            },
            onUrlClick = onUrlClick
        )
    } else {
        onDismiss()
    }
}
