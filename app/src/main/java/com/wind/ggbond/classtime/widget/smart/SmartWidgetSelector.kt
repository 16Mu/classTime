package com.wind.ggbond.classtime.widget.smart

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.widget.WidgetPinHelper
import com.wind.ggbond.classtime.widget.WidgetPinHelper.WidgetType
import com.wind.ggbond.classtime.widget.WidgetPinHelper.CompatibilityLevel
import com.wind.ggbond.classtime.widget.WidgetPinHelper.CompatibilityReport
import com.wind.ggbond.classtime.widget.preferences.WidgetPreferencesManager
import kotlinx.coroutines.launch

data class SmartWidgetOption(
    val type: WidgetType,
    val title: String,
    val description: String,
    val isRecommended: Boolean = false,
    val isFavorite: Boolean = false,
    val icon: String = "\uD83D\uDCF1"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartWidgetSelector(
    onWidgetSelected: (WidgetType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefsManager = remember { WidgetPreferencesManager.getInstance(context) }

    var recommendedType by remember { mutableStateOf<WidgetType?>(null) }
    var favoriteTypes by remember { mutableStateOf<List<String>>(emptyList()) }

    val favoriteTypesState by prefsManager.getFavoriteWidgetTypes()
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        launch {
            recommendedType = prefsManager.getRecommendedWidgetType()
        }
        launch { favoriteTypes = favoriteTypesState }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 标题区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "选择小组件",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                val type = recommendedType
                if (type != null) {
                    Text(
                        text = "推荐：${type.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "根据您的使用习惯智能推荐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 推荐的 Widget 选项列表
        val widgetOptions = getWidgetOptions(recommendedType, favoriteTypes)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(widgetOptions) { option ->
                WidgetOptionCard(
                    option = option,
                    onSelect = {
                        onWidgetSelected(option.type)
                    },
                    onToggleFavorite = {
                        // TODO: handle favorite toggle
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 快速操作区
        QuickActionsSection(
            context = context,
            onQuickAddRecommended = {
                recommendedType?.let { type ->
                    onWidgetSelected(type)
                }
            },
            onOpenSettings = {
                // 打开详细设置页面
            }
        )
    }
}

@Composable
private fun WidgetOptionCard(
    option: SmartWidgetOption,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (option.isRecommended) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (option.isRecommended) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标和收藏按钮行
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                // 收藏图标
                Icon(
                    imageVector = if (option.isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (option.isFavorite) "取消收藏" else "添加收藏",
                    tint = if (option.isFavorite) {
                        Color.Red
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onToggleFavorite() }
                )
            }

            // Widget 图标/预览
            Text(
                text = option.icon,
                fontSize = 40.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 标题
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 描述
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            // 推荐标签
            if (option.isRecommended) {
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "\u2728 推荐",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 添加按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "添加到桌面",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    context: Context,
    onQuickAddRecommended: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val compatibilityInfo = remember {
        WidgetPinHelper.getDeviceCompatibilityReport()
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "快捷操作",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 快速添加推荐 Widget
            QuickActionButton(
                title = "一键添加",
                subtitle = "使用推荐的小组件",
                icon = "\u26A1",
                onClick = onQuickAddRecommended,
                modifier = Modifier.weight(1f)
            )

            // 手动添加引导
            QuickActionButton(
                title = "手动添加",
                subtitle = "按步骤操作",
                icon = "\uD83D\uDCCB",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )

            // 设备兼容性信息
            QuickActionButton(
                title = "设备信息",
                subtitle = "${compatibilityInfo.compatibilityLevel}",
                icon = when (compatibilityInfo.compatibilityLevel) {
                    WidgetPinHelper.CompatibilityLevel.HIGH -> "\u2705"
                    WidgetPinHelper.CompatibilityLevel.MEDIUM -> "\u26A0\uFE0F"
                    WidgetPinHelper.CompatibilityLevel.LOW -> "\u274C"
                },
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

 // 获取 Widget 选项列表
private fun getWidgetOptions(
    recommendedType: WidgetType?,
    favoriteTypes: List<String>
): List<SmartWidgetOption> {
    return listOf(
        SmartWidgetOption(
            type = WidgetType.NEXT_CLASS,
            title = "下节课提醒",
            description = "显示下一节课程信息及倒计时",
            isRecommended = recommendedType == WidgetType.NEXT_CLASS,
            isFavorite = favoriteTypes.contains("next_class"),
            icon = "\u23F0"
        ),
        SmartWidgetOption(
            type = WidgetType.TODAY_COURSE,
            title = "今日课程表",
            description = "查看今天所有课程的完整安排",
            isRecommended = recommendedType == WidgetType.TODAY_COURSE,
            isFavorite = favoriteTypes.contains("today_course"),
            icon = "\uD83D\uDCC5"
        ),
        SmartWidgetOption(
            type = WidgetType.COMPACT_LIST,
            title = "紧凑列表",
            description = "节省空间显示课程列表，适合小屏",
            isRecommended = recommendedType == WidgetType.COMPACT_LIST,
            isFavorite = favoriteTypes.contains("compact_list"),
            icon = "\uD83D\uDCCB"
        ),
        SmartWidgetOption(
            type = WidgetType.WEEK_OVERVIEW,
            title = "周视图概览",
            description = "一周课程总览，方便规划复习",
            isRecommended = recommendedType == WidgetType.WEEK_OVERVIEW,
            isFavorite = favoriteTypes.contains("week_overview"),
            icon = "\uD83D\uDCCA"
        ),
        SmartWidgetOption(
            type = WidgetType.LARGE_TODAY_COURSE,
            title = "大尺寸课程表",
            description = "4x4大尺寸显示更多课程详情",
            isRecommended = recommendedType == WidgetType.LARGE_TODAY_COURSE,
            isFavorite = favoriteTypes.contains("large_today_course"),
            icon = "\uD83D\uDCD6"
        )
    ).sortedByDescending { it.isRecommended || it.isFavorite }
}
