package com.wind.ggbond.classtime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.data.local.entity.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 课表选择状态
 * 用于控制课表选择/创建流程
 */
sealed class ScheduleSelectionState {
    /** 无需选择（已有当前课表且未过期） */
    object Ready : ScheduleSelectionState()
    
    /** 需要创建课表（首次使用，没有任何课表） */
    object NeedCreate : ScheduleSelectionState()
    
    /** 当前课表已过期，需要确认是否继续使用 */
    data class Expired(val schedule: Schedule) : ScheduleSelectionState()
    
    /** 正在加载课表信息 */
    object Loading : ScheduleSelectionState()
}

/**
 * 课表过期提醒对话框
 * 当当前课表已过期时显示，让用户选择继续使用或创建新课表
 * 
 * @param schedule 过期的课表
 * @param onContinue 继续使用当前课表
 * @param onCreateNew 创建新课表
 * @param onDismiss 取消操作
 */
@Composable
fun ScheduleExpiredDialog(
    schedule: Schedule,
    onContinue: () -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    // 日期格式化
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { 
            Text("课表已过期") 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 课表信息
                Text(
                    text = "当前课表「${schedule.name}」已于 ${schedule.endDate.format(dateFormatter)} 结束。",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 提示信息
                Text(
                    text = "您可以选择：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                // 选项说明
                Text(
                    text = "• 继续使用：在旧课表中添加课程\n• 创建新课表：开始新学期的课表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreateNew()
                }
            ) {
                Text("创建新课表")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onContinue()
                }
            ) {
                Text("继续使用")
            }
        }
    )
}

/**
 * 创建课表对话框
 * 用于首次使用或需要创建新课表时
 * 
 * @param onConfirm 确认创建课表，参数为(课表名称, 开始日期, 总周数)
 * @param onDismiss 取消创建
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduleDialog(
    onConfirm: (name: String, startDate: LocalDate, totalWeeks: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    
    // 智能生成默认课表名称
    val currentDate = LocalDate.now()
    val month = currentDate.monthValue
    val year = currentDate.year
    val defaultScheduleName = if (month >= 2 && month <= 7) {
        // 春季学期（2-7月）
        "${year - 1}-${year}学年第二学期"
    } else {
        // 秋季学期（8-1月）
        "${year}-${year + 1}学年第一学期"
    }
    
    // 智能生成默认开始日期（9月第一个周一或3月第一个周一）
    val defaultStartDate = if (month >= 2 && month <= 7) {
        // 春季学期，默认3月第一周的周一
        val marchFirst = LocalDate.of(year, 3, 1)
        var firstMonday = marchFirst
        while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            firstMonday = firstMonday.plusDays(1)
        }
        firstMonday
    } else {
        // 秋季学期，默认9月第一周的周一
        val septFirst = LocalDate.of(year, 9, 1)
        var firstMonday = septFirst
        while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            firstMonday = firstMonday.plusDays(1)
        }
        firstMonday
    }
    
    // 状态
    var scheduleName by remember { mutableStateOf(defaultScheduleName) }
    var startDate by remember { mutableStateOf(defaultStartDate) }
    var totalWeeks by remember { mutableStateOf(20) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // 日期格式化
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { 
            Text("创建课表") 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 提示信息
                Text(
                    text = "请先设置课表信息，用于计算周次和管理课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 课表名称 - 提供快速选择
                var showSemesterOptions by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showSemesterOptions,
                    onExpandedChange = { showSemesterOptions = it }
                ) {
                    OutlinedTextField(
                        value = scheduleName,
                        onValueChange = { scheduleName = it },
                        label = { Text("课表名称") },
                        placeholder = { Text("例如: 大三下学期") },
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSemesterOptions) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = showSemesterOptions,
                        onDismissRequest = { showSemesterOptions = false }
                    ) {
                        val currentYear = LocalDate.now().year
                        listOf(
                            "${currentYear}-${currentYear + 1}学年第一学期",
                            "${currentYear}-${currentYear + 1}学年第二学期",
                            "${currentYear - 1}-${currentYear}学年第一学期",
                            "${currentYear - 1}-${currentYear}学年第二学期"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    scheduleName = option
                                    showSemesterOptions = false
                                }
                            )
                        }
                    }
                }
                
                // 开始日期 - 使用日期选择器
                OutlinedTextField(
                    value = startDate.format(dateFormatter),
                    onValueChange = { },
                    label = { Text("第一天上课日期") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    readOnly = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, "选择日期")
                        }
                    }
                )
                
                // 总周数 - 提供快速选择
                var showWeeksOptions by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showWeeksOptions,
                    onExpandedChange = { showWeeksOptions = it }
                ) {
                    OutlinedTextField(
                        value = "${totalWeeks}周",
                        onValueChange = { },
                        label = { Text("学期总周数") },
                        readOnly = true,
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showWeeksOptions) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showWeeksOptions,
                        onDismissRequest = { showWeeksOptions = false }
                    ) {
                        listOf(16, 18, 20, 22, 24).forEach { weeks ->
                            DropdownMenuItem(
                                text = { Text("${weeks}周") },
                                onClick = {
                                    totalWeeks = weeks
                                    showWeeksOptions = false
                                }
                            )
                        }
                    }
                }
                
                // 显示计算的结束日期
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "结束日期",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
                                .format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConfirm(scheduleName, startDate, totalWeeks)
                },
                enabled = scheduleName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
    
    // 日期选择器对话框
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 检查课表状态
 * 用于判断是否需要创建课表或提示课表过期
 * 
 * @param currentSchedule 当前课表（可能为null）
 * @return 课表选择状态
 */
fun checkScheduleState(currentSchedule: Schedule?): ScheduleSelectionState {
    // 没有课表，需要创建
    if (currentSchedule == null) {
        return ScheduleSelectionState.NeedCreate
    }
    
    // 检查课表是否过期（结束日期早于今天）
    val today = LocalDate.now()
    if (currentSchedule.endDate.isBefore(today)) {
        return ScheduleSelectionState.Expired(currentSchedule)
    }
    
    // 课表有效，可以继续
    return ScheduleSelectionState.Ready
}
