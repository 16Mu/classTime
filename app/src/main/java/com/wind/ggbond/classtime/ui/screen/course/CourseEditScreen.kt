package com.wind.ggbond.classtime.ui.screen.course

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.wind.ggbond.classtime.ui.screen.course.components.WeekSelectorDialog
import com.wind.ggbond.classtime.ui.theme.CourseColors
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.BuildConfig
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 课程编辑/新增页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    navController: NavController,
    courseId: Long?,
    defaultDayOfWeek: Int? = null,
    defaultStartSection: Int? = null,
    defaultSectionCount: Int? = null,
    defaultWeekNumber: Int? = null,
    defaultCourseName: String? = null,
    viewModel: CourseEditViewModel = hiltViewModel()
) {
    LaunchedEffect(courseId) {
        if (courseId != null && courseId > 0) {
            viewModel.loadCourse(courseId)
        }
    }
    LaunchedEffect(courseId, defaultDayOfWeek, defaultStartSection, defaultSectionCount, defaultWeekNumber, defaultCourseName) {
        if (courseId == null) {
            viewModel.applyDefaultsIfNeeded(
                dayOfWeek = defaultDayOfWeek,
                startSection = defaultStartSection,
                sectionCount = defaultSectionCount,
                weekNumber = defaultWeekNumber,
                courseName = defaultCourseName
            )
        }
    }
    
    // 优化：使用remember包装isEdit计算，避免每次重组
    val isEdit = remember(courseId) { courseId != null && courseId > 0 }
    
    // 优化：避免过度订阅，减少不必要的重组
    val courseName by viewModel.courseName.collectAsState()
    val teacher by viewModel.teacher.collectAsState()
    val classroom by viewModel.classroom.collectAsState()
    val dayOfWeek by viewModel.dayOfWeek.collectAsState()
    val startSection by viewModel.startSection.collectAsState()
    val sectionCount by viewModel.sectionCount.collectAsState()
    val selectedWeeks by viewModel.selectedWeeks.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderMinutes by viewModel.reminderMinutes.collectAsState()
    val note by viewModel.note.collectAsState()
    val credit by viewModel.credit.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    
    val showWeekSelector by viewModel.showWeekSelector.collectAsState()
    val showColorPicker by viewModel.showColorPicker.collectAsState()
    
    // ✅ 使用新的 SaveState
    val saveState by viewModel.saveState.collectAsState()
    
    // ✅ Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    // ✅ 监听保存状态变化，显示反馈
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Success -> {
                // 显示成功消息
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
                // P3-10: 直接导航回主界面，不再人为延迟（使用底部Tab的课表路由，确保导航栈正确清理）
                navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.Main.createRoute(refresh = true)) {
                    popUpTo(com.wind.ggbond.classtime.ui.navigation.BottomNavItem.Schedule.route) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
            is SaveState.Error -> {
                // 显示错误消息
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                // 重置状态，允许重试
                viewModel.resetSaveState()
            }
            SaveState.Idle, SaveState.Saving -> {
                // 不显示任何内容
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(if (isEdit) "编辑课程" else "新增课程") },
                navigationIcon = {
                IconButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navController.navigateUp()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // ✅ 根据保存状态显示不同的按钮状态
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // 只调用保存，导航由 LaunchedEffect 处理
                            viewModel.saveCourse()
                        },
                        enabled = saveState !is SaveState.Saving,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        if (saveState is SaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "确认",
                            modifier = Modifier.size(18.dp)
                        )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (saveState is SaveState.Saving) "保存中..." else "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 基础信息卡片 - 优化布局
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 标题 - 更现代化
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            "基础信息",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    
                    // 课程名称 - 支持动态扩大，内容过多时可内部滑动
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { viewModel.updateCourseName(it) },
                        label = { Text("课程名称 *") },
                        placeholder = { Text("例如：高等数学、英语、计算机原理") },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.Book, 
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    
                    // 教师和教室 - 并排显示以节省空间
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 教师 - 支持动态扩大
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { viewModel.updateTeacher(it) },
                            label = { Text("教师") },
                            placeholder = { Text("张老师") },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Person, 
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                ) 
                            },
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        
                        // 教室 - 支持动态扩大
                        OutlinedTextField(
                            value = classroom,
                            onValueChange = { viewModel.updateClassroom(it) },
                            label = { Text("教室") },
                            placeholder = { Text("A101") },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Place, 
                                    null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                ) 
                            },
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 时间安排卡片 - 统一的现代化设计
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            "时间安排",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    
                    // 星期选择
                    var expandedDay by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedDay,
                        onExpandedChange = { expandedDay = it }
                    ) {
                        OutlinedTextField(
                            value = DateUtils.getDayOfWeekName(dayOfWeek),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("星期 *") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDay,
                            onDismissRequest = { expandedDay = false }
                        ) {
                            (1..7).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(DateUtils.getDayOfWeekName(day)) },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.updateDayOfWeek(day)
                                        expandedDay = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // 节次和持续时间 - 更好的布局
                    // Bug2修复：使用字符串状态管理输入框，允许清空内容
                    var startSectionText by remember(startSection) { mutableStateOf(startSection.toString()) }
                    var sectionCountText by remember(sectionCount) { mutableStateOf(sectionCount.toString()) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 开始节次
                        OutlinedTextField(
                            value = startSectionText,
                            onValueChange = { newValue ->
                                // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
                                startSectionText = newValue
                                newValue.toIntOrNull()?.let { num -> viewModel.updateStartSection(num) }
                            },
                            label = { Text("开始节次 *") },
                            placeholder = { Text("1") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        
                        // 持续节数
                        OutlinedTextField(
                            value = sectionCountText,
                            onValueChange = { newValue ->
                                // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
                                sectionCountText = newValue
                                newValue.toIntOrNull()?.let { num -> viewModel.updateSectionCount(num) }
                            },
                            label = { Text("持续节数 *") },
                            placeholder = { Text("2") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timer,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // 周次选择 - 使用 clickable Surface 替代 disabled 输入框
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.showWeekSelector()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "上课周次 *",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (selectedWeeks.isEmpty()) "点击选择"
                                    else "已选择 ${selectedWeeks.size} 周",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 颜色选择卡片 - 优化设计
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "课程颜色",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                "选择喜欢的颜色标识课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 颜色选择器 - 网格布局
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 第一行颜色
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CourseColors.take(6).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == toHexString(color)) 3.dp else 0.dp,
                                            color = if (selectedColor == toHexString(color))
                                                MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.updateColor(toHexString(color))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColor == toHexString(color)) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 更多颜色按钮
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.showColorPicker()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("查看更多颜色")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 提醒设置卡片 - 优化交互
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (reminderEnabled)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (reminderEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "课程提醒",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                if (reminderEnabled) "将在上课前提醒您" else "开启后可设置提醒时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // 直接开启提醒，不强制检查权限
                                viewModel.updateReminderEnabled(enabled)
                            }
                        )
                    }
                    
                    // Bug2修复：提醒分钟数输入框同样允许清空
                    var reminderMinutesText by remember(reminderMinutes) { mutableStateOf(reminderMinutes.toString()) }
                    
                    AnimatedVisibility(
                        visible = reminderEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = reminderMinutesText,
                            onValueChange = { newValue ->
                                // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
                                reminderMinutesText = newValue
                                newValue.toIntOrNull()?.let { num -> viewModel.updateReminderMinutes(num) }
                            },
                            label = { Text("提前提醒") },
                            suffix = { Text("分钟") },
                            placeholder = { Text("15") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timer,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 学分卡片 - Bug3: 支持填写学分
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            "学分",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    
                    // 学分输入框（同样使用字符串状态管理，解决Bug2）
                    var creditText by remember(credit) {
                        mutableStateOf(if (credit > 0f) credit.toString() else "")
                    }
                    
                    OutlinedTextField(
                        value = creditText,
                        onValueChange = { newValue ->
                            // 直接赋值，不做 filter，避免光标位置丢失导致删除键失效
                            creditText = newValue
                            newValue.toFloatOrNull()?.let { num -> viewModel.updateCredit(num) }
                        },
                        label = { Text("课程学分") },
                        placeholder = { Text("例如: 3.0") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 备注 - 卡片式设计
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "备注",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    
                    OutlinedTextField(
                        value = note,
                        onValueChange = { viewModel.updateNote(it) },
                        placeholder = { Text("添加课程备注，如作业要求、考试范围等") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    // 周次选择器对话框 - Bug1修复：传入当前学期的总周数
    if (showWeekSelector) {
        WeekSelectorDialog(
            selectedWeeks = selectedWeeks,
            maxWeeks = totalWeeks,
            onDismiss = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.hideWeekSelector()
            },
            onConfirm = { weeks -> 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateSelectedWeeks(weeks)
                viewModel.hideWeekSelector()
            }
        )
    }
    
    // 颜色选择器对话框
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { viewModel.hideColorPicker() },
            title = { Text("选择颜色") },
            text = {
                Column {
                    CourseColors.chunked(5).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            row.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.updateColor(toHexString(color))
                                            viewModel.hideColorPicker()
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.hideColorPicker()
                }) {
                    Text("关闭")
                }
            }
        )
    }
}

fun toHexString(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}



