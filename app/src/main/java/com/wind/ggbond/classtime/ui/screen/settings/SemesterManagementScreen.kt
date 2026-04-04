package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.ui.navigation.Screen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 学期管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterManagementScreen(
    navController: NavController,
    fromImport: Boolean = false,  // 是否从导入跳转而来
    fallSemesterStartDate: String? = null,  // 秋季学期的默认开始日期
    springSemesterStartDate: String? = null,  // 春季学期的默认开始日期
    viewModel: SemesterManagementViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val editingSchedule by viewModel.editingSchedule.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val deletingSchedule by viewModel.deletingSchedule.collectAsState()
    
    // 如果是从导入进入且还没有课表，自动打开添加对话框
    LaunchedEffect(fromImport, schedules) {
        if (fromImport && schedules.isEmpty()) {
            viewModel.showAddDialog()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(if (fromImport) "设置课表信息" else "课表管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, "添加课表")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(schedules) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onEdit = { viewModel.showEditDialog(schedule) },
                    onDelete = { viewModel.showDeleteDialog(schedule) },
                    onSetCurrent = { viewModel.setCurrentSchedule(schedule) }
                )
            }
            
            if (schedules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无课表，点击右下角添加",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // 根据当前月份选择合适的学校默认日期
    val currentMonth = LocalDate.now().monthValue
    val schoolDefaultStartDate = if (currentMonth >= 2 && currentMonth <= 7) {
        // 春季学期
        springSemesterStartDate
    } else {
        // 秋季学期
        fallSemesterStartDate
    }
    
    // 添加课表对话框
    if (showAddDialog) {
        ScheduleDialog(
            title = if (fromImport) "设置课表信息" else "添加课表",
            onDismiss = { 
                viewModel.hideAddDialog()
                // 如果是从导入进入且用户取消设置，跳转到时间配置界面
                if (fromImport) {
                    navController.navigate(Screen.ClassTimeConfig.createRoute(fromImport = true)) {
                        popUpTo(Screen.Main.route) { inclusive = false }
                    }
                }
            },
            onConfirm = { name, startDate, endDate, totalWeeks ->
                viewModel.addSchedule(name, startDate, endDate, totalWeeks)
                // 如果是从导入进入，保存成功后跳转到时间配置界面
                if (fromImport) {
                    navController.navigate(Screen.ClassTimeConfig.createRoute(fromImport = true)) {
                        popUpTo(Screen.Main.route) { inclusive = false }
                    }
                }
            },
            schoolDefaultStartDate = schoolDefaultStartDate
        )
    }
    
    // 编辑课表对话框
    if (showEditDialog && editingSchedule != null) {
        ScheduleDialog(
            title = "编辑课表",
            schedule = editingSchedule,
            onDismiss = { viewModel.hideEditDialog() },
            onConfirm = { name, startDate, endDate, totalWeeks ->
                editingSchedule?.let {
                    viewModel.updateSchedule(
                        it.copy(
                            name = name,
                            startDate = startDate,
                            endDate = endDate,
                            totalWeeks = totalWeeks
                        )
                    )
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteDialog && deletingSchedule != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("删除课表") },
            text = { Text("确定要删除「${deletingSchedule?.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    deletingSchedule?.let { viewModel.deleteSchedule(it) }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 课表卡片
 */
@Composable
fun ScheduleCard(
    schedule: Schedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetCurrent: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (schedule.isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "当前",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "开始: ${schedule.startDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "结束: ${schedule.endDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "总周数: ${schedule.totalWeeks}周",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            if (!schedule.isCurrent) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSetCurrent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("设为当前课表")
                }
            }
        }
    }
}

/**
 * 课表添加/编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    title: String,
    schedule: Schedule? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDate: LocalDate, endDate: LocalDate, totalWeeks: Int) -> Unit,
    schoolDefaultStartDate: String? = null  // 学校特定的默认日期，格式：yyyy-MM-dd
) {
    // 智能生成默认课表名称（仅用于新建时）
    val currentDate = LocalDate.now()
    val month = currentDate.monthValue
    val year = currentDate.year
    val defaultScheduleName = if (schedule != null) {
        schedule.name
    } else if (month >= 2 && month <= 7) {
        "${year - 1}-${year}学年第二学期"
    } else {
        "${year}-${year + 1}学年第一学期"
    }
    
    // 智能生成默认开始日期（仅用于新建时）
    val defaultStartDate = if (schedule != null) {
        schedule.startDate
    } else if (month >= 2 && month <= 7) {
        // 春季学期，默认3月2日（或学校特定日期）
        if (schoolDefaultStartDate != null) {
            // 如果学校指定了春季日期，使用学校日期
            try {
                LocalDate.parse(schoolDefaultStartDate)
            } catch (e: Exception) {
                // 解析失败，使用默认的3月第一周的周一
                val marchFirst = LocalDate.of(year, 3, 1)
                var firstMonday = marchFirst
                while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                    firstMonday = firstMonday.plusDays(1)
                }
                firstMonday
            }
        } else {
            // 默认3月第一周的周一
            val marchFirst = LocalDate.of(year, 3, 1)
            var firstMonday = marchFirst
            while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                firstMonday = firstMonday.plusDays(1)
            }
            firstMonday
        }
    } else {
        // 秋季学期，默认9月8日（或学校特定日期）
        if (schoolDefaultStartDate != null) {
            // 如果学校指定了秋季日期，使用学校日期
            try {
                LocalDate.parse(schoolDefaultStartDate)
            } catch (e: Exception) {
                // 解析失败，使用默认的9月第一周的周一
                val septFirst = LocalDate.of(year, 9, 1)
                var firstMonday = septFirst
                while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                    firstMonday = firstMonday.plusDays(1)
                }
                firstMonday
            }
        } else {
            // 默认9月第一周的周一
            val septFirst = LocalDate.of(year, 9, 1)
            var firstMonday = septFirst
            while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                firstMonday = firstMonday.plusDays(1)
            }
            firstMonday
        }
    }
    
    var name by remember { mutableStateOf(defaultScheduleName) }
    var startDate by remember { mutableStateOf(defaultStartDate) }
    var totalWeeks by remember { mutableStateOf(schedule?.totalWeeks ?: 20) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // 根据开始日期和总周数自动计算结束日期
    val endDate by remember(startDate, totalWeeks) {
        derivedStateOf {
            startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
        }
    }
    
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 学期名称 - 提供快速选择
                var showSemesterOptions by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showSemesterOptions,
                    onExpandedChange = { showSemesterOptions = it }
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("学期名称") },
                        placeholder = { Text("例如: 2023-2024学年第一学期") },
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSemesterOptions) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
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
                                    name = option
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                            text = endDate.format(dateFormatter),
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
                    onConfirm(name, startDate, endDate, totalWeeks)
                },
                enabled = name.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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


