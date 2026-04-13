// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.ui.theme.Spacing
import com.wind.ggbond.classtime.util.Constants
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 课表快速编辑对话框
 * 
 * 用于在主界面快速编辑当前课表的名称、起始日期和持续周次
 * 
 * @param schedule 当前课表
 * @param onConfirm 确认编辑，参数为(课表名称, 开始日期, 总周数)
 * @param onDismiss 取消编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleQuickEditDialog(
    schedule: Schedule,
    onConfirm: (name: String, startDate: LocalDate, totalWeeks: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 触觉反馈
    val haptic = LocalHapticFeedback.current
    
    // 状态 - 使用当前课表的值作为初始值
    var scheduleName by remember(schedule.id) { mutableStateOf(schedule.name) }
    var startDate by remember(schedule.id) { mutableStateOf(schedule.startDate) }
    var totalWeeks by remember(schedule.id) { mutableStateOf(schedule.totalWeeks) }
    var weeksInput by remember(schedule.id) { mutableStateOf(schedule.totalWeeks.toString()) }
    var weeksError by remember(schedule.id) { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("编辑课表")
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = showSemesterOptions,
                        onDismissRequest = { showSemesterOptions = false }
                    ) {
                        // 生成学期选项
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
                        .fillMaxWidth(),
                    readOnly = true,
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
                
                // 总周数 - NumberStepper + 手动输入 + 快速选择
                NumberStepper(
                    value = totalWeeks,
                    onValueChange = {
                        totalWeeks = it
                        weeksInput = it.toString()
                        weeksError = null
                    },
                    range = Constants.Semester.MIN_WEEK_NUMBER..Constants.Semester.MAX_WEEK_NUMBER,
                    label = "周",
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weeksInput,
                    onValueChange = { input ->
                        weeksInput = input
                        val parsed = input.toIntOrNull()
                        weeksError = when {
                            parsed == null -> "请输入有效的数字"
                            parsed < Constants.Semester.MIN_WEEK_NUMBER -> "最少${Constants.Semester.MIN_WEEK_NUMBER}周"
                            parsed > Constants.Semester.MAX_WEEK_NUMBER -> "最多${Constants.Semester.MAX_WEEK_NUMBER}周"
                            else -> {
                                totalWeeks = parsed
                                null
                            }
                        }
                    },
                    label = { Text("学期总周数") },
                    isError = weeksError != null,
                    supportingText = weeksError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // 快速选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(16, 18, 20, 22, 24).forEach { weeks ->
                        FilterChip(
                            selected = totalWeeks == weeks,
                            onClick = {
                                totalWeeks = weeks
                                weeksInput = weeks.toString()
                                weeksError = null
                            },
                            label = { Text("${weeks}周") }
                        )
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
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    }
                ) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfirm(scheduleName, startDate, totalWeeks)
                    },
                    enabled = scheduleName.isNotBlank()
                ) {
                    Text("保存")
                }
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

