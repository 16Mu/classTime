package com.wind.ggbond.classtime.ui.screen.reminder

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 提醒管理界面 - 重构版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReminderManagementViewModel = hiltViewModel()
) {
    // 收集ViewModel状态
    val reminders by viewModel.reminders.collectAsState()
    val courseMap by viewModel.courseMap.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showClearAllDialog by viewModel.showClearAllDialog.collectAsState()
    val selectedReminder by viewModel.selectedReminder.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("提醒管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 清理过期按钮
                    IconButton(onClick = { viewModel.cleanExpiredReminders() }) {
                        Icon(Icons.Default.CleaningServices, "清理过期")
                    }
                    // 清除全部按钮（弹出确认对话框）
                    IconButton(onClick = { viewModel.showClearAllDialog() }) {
                        Icon(Icons.Default.DeleteSweep, "清除全部")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 紧凑统计摘要
            stats?.let { ReminderStatsSummary(stats = it) }
            
            // 筛选标签栏
            ReminderFilterBar(
                currentFilter = filterType,
                stats = stats,
                onFilterSelected = { viewModel.setFilter(it) }
            )
            
            // 提醒列表（按日期分组）
            if (reminders.isEmpty()) {
                EmptyReminderView(filterType = filterType)
            } else {
                ReminderGroupedList(
                    reminders = reminders,
                    courseMap = courseMap,
                    onDelete = { viewModel.showDeleteDialog(it) },
                    onToggle = { viewModel.toggleReminder(it) }
                )
            }
        }
    }
    
    // 删除单条确认对话框
    if (showDeleteDialog && selectedReminder != null) {
        val courseName = courseMap[selectedReminder?.courseId]?.courseName ?: "未知课程"
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除提醒") },
            text = { Text("确定要删除「${courseName}」的这条提醒吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedReminder?.let { viewModel.deleteReminder(it) }
                        viewModel.hideDeleteDialog()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 清除全部确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearAllDialog() },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("清除全部提醒") },
            text = { Text("确定要清除所有提醒吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmClearAll() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("全部清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearAllDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 紧凑统计摘要 - 单行展示核心数据
 */
@Composable
private fun ReminderStatsSummary(stats: ReminderStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 总数指示器
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "共 ${stats.totalReminders} 条",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // 活跃指示器
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "活跃 ${stats.activeReminders}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 今日指示器
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "今天 ${stats.todayReminders}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 筛选标签栏
 */
@Composable
private fun ReminderFilterBar(
    currentFilter: ReminderFilterType,
    stats: ReminderStats?,
    onFilterSelected: (ReminderFilterType) -> Unit
) {
    // 筛选标签配置
    val filters = listOf(
        ReminderFilterType.ALL to "全部",
        ReminderFilterType.TODAY to "今天",
        ReminderFilterType.THIS_WEEK to "本周",
        ReminderFilterType.EXPIRED to "已过期"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (type, label) ->
            val isSelected = currentFilter == type
            // 根据筛选类型获取对应数量
            val count = when (type) {
                ReminderFilterType.ALL -> stats?.totalReminders
                ReminderFilterType.TODAY -> stats?.todayReminders
                ReminderFilterType.THIS_WEEK -> stats?.upcomingReminders
                ReminderFilterType.EXPIRED -> null
            }
            
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(type) },
                label = {
                    Text(
                        text = if (count != null) "$label($count)" else label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/**
 * 按日期分组的提醒列表
 */
@Composable
private fun ReminderGroupedList(
    reminders: List<Reminder>,
    courseMap: Map<Long, Course>,
    onDelete: (Reminder) -> Unit,
    onToggle: (Reminder) -> Unit
) {
    // 按触发日期分组
    val grouped = reminders.groupBy { reminder ->
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(reminder.triggerTime),
            ZoneId.systemDefault()
        ).toLocalDate()
    }.toSortedMap()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        grouped.forEach { (date, dateReminders) ->
            // 日期分组头
            item(key = "header_$date") {
                DateGroupHeader(date = date)
            }
            // 该日期下的提醒卡片
            items(dateReminders, key = { it.id }) { reminder ->
                ReminderCompactCard(
                    reminder = reminder,
                    course = courseMap[reminder.courseId],
                    onDelete = { onDelete(reminder) },
                    onToggle = { onToggle(reminder) }
                )
            }
        }
    }
}

/**
 * 日期分组头
 */
@Composable
private fun DateGroupHeader(date: LocalDate) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    
    // 根据日期生成友好文本
    val dateText = when (date) {
        today -> "今天"
        tomorrow -> "明天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日 ")) + getDayName(date.dayOfWeek.value)
    }
    
    // 判断是否为过期日期
    val isExpired = date.isBefore(today)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isExpired) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 分隔线
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * 紧凑型提醒卡片 - 显示课程名称、教室、时间信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCompactCard(
    reminder: Reminder,
    course: Course?,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    // 提取触发时间
    val triggerTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(reminder.triggerTime),
        ZoneId.systemDefault()
    )
    // 判断过期状态
    val isExpired = reminder.triggerTime < System.currentTimeMillis()
    // 解析课程颜色
    val courseColor = try {
        Color(android.graphics.Color.parseColor(course?.color ?: "#D4A574"))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    // 根据状态计算整体透明度
    val contentAlpha = if (isExpired) 0.5f else if (!reminder.isEnabled) 0.65f else 1f
    
    // 滑动删除容器
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // 滑动时露出的红色删除背景
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Transparent
                },
                label = "swipe_bg_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        // 卡片主体
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isExpired) 0.dp else 1.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧彩色指示条
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(courseColor.copy(alpha = contentAlpha))
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 中间信息区域
                Column(modifier = Modifier.weight(1f)) {
                    // 第一行：课程名称 + 过期/禁用标签
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = course?.courseName ?: "未知课程",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isExpired) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "已过期",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (!reminder.isEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "已暂停",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 第二行：教室 + 教师（如果有）
                    val detailParts = mutableListOf<String>()
                    if (!course?.classroom.isNullOrBlank()) detailParts.add(course!!.classroom)
                    if (!course?.teacher.isNullOrBlank()) detailParts.add(course!!.teacher)
                    if (detailParts.isNotEmpty()) {
                        Text(
                            text = detailParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    
                    // 第三行：时间信息（周次 + 星期 + 提醒时间 + 提前量）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 周次与星期
                        Text(
                            text = "第${reminder.weekNumber}周 ${getDayName(reminder.dayOfWeek)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                        // 分隔点
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha * 0.5f)
                        )
                        // 提醒触发时间
                        Text(
                            text = triggerTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                        // 分隔点
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha * 0.5f)
                        )
                        // 提前时间
                        Text(
                            text = "提前${reminder.minutesBefore}分钟",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 右侧开关（仅非过期状态显示）
                if (!isExpired) {
                    Switch(
                        checked = reminder.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 空状态视图 - 根据筛选类型显示不同提示
 */
@Composable
private fun EmptyReminderView(filterType: ReminderFilterType) {
    // 根据筛选类型确定提示文案
    val (title, subtitle) = when (filterType) {
        ReminderFilterType.ALL -> "暂无提醒" to "为课程启用提醒后，会在这里显示"
        ReminderFilterType.TODAY -> "今天没有提醒" to "今天暂无课程提醒安排"
        ReminderFilterType.THIS_WEEK -> "本周没有提醒" to "本周暂无课程提醒安排"
        ReminderFilterType.EXPIRED -> "没有过期提醒" to "所有提醒都在有效期内"
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = when (filterType) {
                    ReminderFilterType.ALL -> Icons.Default.NotificationsOff
                    ReminderFilterType.TODAY -> Icons.Default.Today
                    ReminderFilterType.THIS_WEEK -> Icons.Default.CalendarMonth
                    ReminderFilterType.EXPIRED -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 获取星期名称
 */
private fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "未知"
    }
}

/**
 * 提醒统计数据类
 */
data class ReminderStats(
    val totalReminders: Int,
    val activeReminders: Int,
    val todayReminders: Int,
    val upcomingReminders: Int
)


