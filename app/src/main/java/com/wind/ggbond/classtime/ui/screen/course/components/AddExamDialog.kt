package com.wind.ggbond.classtime.ui.screen.course.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Exam
import com.wind.ggbond.classtime.util.DateUtils
import java.time.LocalDate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/**
 * 添加/编辑考试对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamDialog(
    course: Course,
    currentWeek: Int,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onConfirm: (Exam) -> Unit,
    existingExam: Exam? = null
) {
    var examType by remember { mutableStateOf(existingExam?.examType ?: "期末考试") }
    var weekNumber by remember { mutableStateOf(existingExam?.weekNumber ?: currentWeek) }
    var dayOfWeek by remember { mutableStateOf<Int?>(existingExam?.dayOfWeek) }
    var startSection by remember { mutableStateOf<Int?>(existingExam?.startSection) }
    var sectionCount by remember { mutableStateOf(existingExam?.sectionCount ?: 2) }
    var examTime by remember { mutableStateOf(existingExam?.examTime ?: "") }
    var location by remember { mutableStateOf(existingExam?.location ?: "") }
    var seat by remember { mutableStateOf(existingExam?.seat ?: "") }
    var note by remember { mutableStateOf(existingExam?.note ?: "") }
    var reminderEnabled by remember { mutableStateOf(existingExam?.reminderEnabled ?: true) }
    var reminderDays by remember { mutableStateOf(existingExam?.reminderDays ?: 3) }
    
    // 是否精确模式（设置具体节次）
    var isPreciseMode by remember { mutableStateOf(existingExam?.isPreciseMode() ?: false) }
    
    // 展开的部分
    var showAdvancedOptions by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingExam != null) "编辑考试" else "添加考试",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 课程名称显示
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = course.courseName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 考试类型选择
                Text(
                    text = "考试类型",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("期中考试", "期末考试", "随堂测验", "补考").forEach { type ->
                        FilterChip(
                            selected = examType == type,
                            onClick = { examType = type },
                            label = { Text(type) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 周次选择
                Text(
                    text = "考试周次",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // 使用字符串状态管理，避免删除键光标跳动
                var weekNumberText by remember(weekNumber) { mutableStateOf(weekNumber.toString()) }
                OutlinedTextField(
                    value = weekNumberText,
                    onValueChange = { newValue ->
                        weekNumberText = newValue
                        newValue.toIntOrNull()?.let { week ->
                            if (week in 1..totalWeeks) {
                                weekNumber = week
                            }
                        }
                    },
                    label = { Text("第几周") },
                    leadingIcon = { Icon(Icons.Default.DateRange, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 精确模式开关
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPreciseMode) 
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "精确到节次",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "在课表中显示考试卡片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPreciseMode,
                            onCheckedChange = { isPreciseMode = it }
                        )
                    }
                }
                
                // 精确模式：星期和节次
                if (isPreciseMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 星期选择
                    Text(
                        text = "星期",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..7).forEach { day ->
                            FilterChip(
                                selected = dayOfWeek == day,
                                onClick = { dayOfWeek = day },
                                label = { 
                                    Text(
                                        DateUtils.getDayOfWeekName(day),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 节次选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 使用字符串状态管理，避免删除键光标跳动
                        var startSectionText by remember(startSection) { mutableStateOf(startSection?.toString() ?: "") }
                        var sectionCountText by remember(sectionCount) { mutableStateOf(sectionCount.toString()) }
                        OutlinedTextField(
                            value = startSectionText,
                            onValueChange = { newValue ->
                                startSectionText = newValue
                                newValue.toIntOrNull()?.let { section ->
                                    if (section > 0) startSection = section
                                }
                            },
                            label = { Text("开始节次") },
                            leadingIcon = { Icon(Icons.Default.AccessTime, null) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        
                        OutlinedTextField(
                            value = sectionCountText,
                            onValueChange = { newValue ->
                                sectionCountText = newValue
                                newValue.toIntOrNull()?.let { count ->
                                    if (count > 0) sectionCount = count
                                }
                            },
                            label = { Text("持续节数") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 时间段（可选）
                OutlinedTextField(
                    value = examTime,
                    onValueChange = { examTime = it },
                    label = { Text("考试时间段（可选）") },
                    placeholder = { Text("如：09:00-11:00") },
                    leadingIcon = { Icon(Icons.Default.Schedule, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 地点
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("考试地点") },
                    placeholder = { Text("如：教学楼A101") },
                    leadingIcon = { Icon(Icons.Default.Place, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 座位号
                OutlinedTextField(
                    value = seat,
                    onValueChange = { seat = it },
                    label = { Text("座位号（可选）") },
                    placeholder = { Text("如：第3排15号") },
                    leadingIcon = { Icon(Icons.Default.EventSeat, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 高级选项
                TextButton(
                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAdvancedOptions) "收起高级选项" else "展开高级选项")
                    Icon(
                        if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                
                if (showAdvancedOptions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 提醒设置
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "考前提醒",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = reminderEnabled,
                                    onCheckedChange = { reminderEnabled = it }
                                )
                            }
                            
                            if (reminderEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "提前 $reminderDays 天提醒",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = reminderDays.toFloat(),
                                    onValueChange = { reminderDays = it.toInt() },
                                    valueRange = 1f..7f,
                                    steps = 6
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 备注
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注") },
                        placeholder = { Text("如：开卷、可带计算器等") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val exam = if (existingExam != null) {
                        existingExam.copy(
                            examType = examType,
                            weekNumber = weekNumber,
                            dayOfWeek = if (isPreciseMode) dayOfWeek else null,
                            startSection = if (isPreciseMode) startSection else null,
                            sectionCount = sectionCount,
                            examTime = examTime,
                            location = location,
                            seat = seat,
                            reminderEnabled = reminderEnabled,
                            reminderDays = reminderDays,
                            note = note,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        Exam(
                            courseId = course.id,
                            courseName = course.courseName,
                            examType = examType,
                            weekNumber = weekNumber,
                            dayOfWeek = if (isPreciseMode) dayOfWeek else null,
                            startSection = if (isPreciseMode) startSection else null,
                            sectionCount = sectionCount,
                            examTime = examTime,
                            location = location,
                            seat = seat,
                            reminderEnabled = reminderEnabled,
                            reminderDays = reminderDays,
                            note = note
                        )
                    }
                    onConfirm(exam)
                }
            ) {
                Text(if (existingExam != null) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}



