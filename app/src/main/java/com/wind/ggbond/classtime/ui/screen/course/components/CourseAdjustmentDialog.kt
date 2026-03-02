package com.wind.ggbond.classtime.ui.screen.course.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.util.DateUtils

/**
 * 临时调课对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseAdjustmentDialog(
    course: Course,
    currentWeekNumber: Int,
    totalWeeks: Int,
    newWeekNumber: Int,
    newDayOfWeek: Int,
    newStartSection: Int,
    newSectionCount: Int,
    newClassroom: String,
    reason: String,
    hasConflict: Boolean,
    conflictMessage: String,
    isSaving: Boolean,
    onNewWeekNumberChange: (Int) -> Unit,
    onNewDayOfWeekChange: (Int) -> Unit,
    onNewStartSectionChange: (Int) -> Unit,
    onNewSectionCountChange: (Int) -> Unit,
    onNewClassroomChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "临时调课",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 课程信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = course.courseName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "教师: ${course.teacher}  |  教室: ${course.classroom}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 原始时间信息
                Text(
                    text = "原始安排",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        InfoRow("周次", "第 $currentWeekNumber 周")
                        InfoRow("时间", "${DateUtils.getDayOfWeekName(course.dayOfWeek)} 第${course.startSection}-${course.startSection + course.sectionCount - 1}节")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 新时间设置
                Text(
                    text = "调整后安排",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // 周次选择
                var showWeekPicker by remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWeekPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "周次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "第 $newWeekNumber 周",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (showWeekPicker) {
                    WeekPickerDialog(
                        currentWeek = newWeekNumber,
                        totalWeeks = totalWeeks,
                        onWeekSelected = {
                            onNewWeekNumberChange(it)
                            showWeekPicker = false
                        },
                        onDismiss = { showWeekPicker = false }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 星期选择
                Text(
                    text = "星期",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // 周一到周五
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("一", "二", "三", "四", "五").forEachIndexed { index, day ->
                        val dayOfWeek = index + 1
                        val isSelected = newDayOfWeek == dayOfWeek
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onNewDayOfWeekChange(dayOfWeek) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // 周六周日
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("六", "日").forEachIndexed { index, day ->
                        val dayOfWeek = index + 6
                        val isSelected = newDayOfWeek == dayOfWeek
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onNewDayOfWeekChange(dayOfWeek) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 填充空白使其和上面对齐
                    Spacer(modifier = Modifier.weight(3f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 节次选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 开始节次
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "开始节次",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        var expandedStart by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedStart,
                            onExpandedChange = { expandedStart = it }
                        ) {
                            OutlinedTextField(
                                value = "第 $newStartSection 节",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStart) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedStart,
                                onDismissRequest = { expandedStart = false }
                            ) {
                                (1..12).forEach { section ->
                                    DropdownMenuItem(
                                        text = { Text("第 $section 节") },
                                        onClick = {
                                            onNewStartSectionChange(section)
                                            expandedStart = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 持续节数
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "持续节数",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        var expandedCount by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCount,
                            onExpandedChange = { expandedCount = it }
                        ) {
                            OutlinedTextField(
                                value = "$newSectionCount 节",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCount) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCount,
                                onDismissRequest = { expandedCount = false }
                            ) {
                                (1..6).forEach { count ->
                                    DropdownMenuItem(
                                        text = { Text("$count 节") },
                                        onClick = {
                                            onNewSectionCountChange(count)
                                            expandedCount = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 教室（调课时可能换教室）
                OutlinedTextField(
                    value = newClassroom,
                    onValueChange = onNewClassroomChange,
                    label = { Text("教室") },
                    placeholder = { Text("调课后的教室（不填则不变）") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 调课原因
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("调课原因（可选）") },
                    placeholder = { Text("例如：教师临时有事") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 冲突提示
                if (hasConflict) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = conflictMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "取消",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = !hasConflict && !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (isSaving) "保存中..." else "确认调课",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 周次选择对话框
 */
@Composable
fun WeekPickerDialog(
    currentWeek: Int,
    totalWeeks: Int,
    onWeekSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                (1..totalWeeks).forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onWeekSelected(week) }
                            .background(
                                if (week == currentWeek) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第 $week 周",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (week == currentWeek)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (week == currentWeek) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (week < totalWeeks) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

