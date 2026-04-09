package com.wind.ggbond.classtime.ui.screen.course

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.wind.ggbond.classtime.ui.navigation.Screen
import kotlinx.coroutines.launch
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.util.WeekParser
import com.wind.ggbond.classtime.ui.theme.contentColorForBackground
import com.wind.ggbond.classtime.ui.theme.topGradientOverlayAlpha
import com.wind.ggbond.classtime.ui.theme.wallpaperAwareBackground
import com.wind.ggbond.classtime.BuildConfig
import com.wind.ggbond.classtime.util.CourseColorProvider

/**
 * 课程详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    navController: NavController,
    courseId: Long,
    viewModel: CourseDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(courseId) {
        viewModel.loadCourse(courseId)
    }
    
    val course by viewModel.course.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showAdjustmentDialog by viewModel.showAdjustmentDialog.collectAsState()
    val showAddExamDialog by viewModel.showAddExamDialog.collectAsState()
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val currentWeekNumber by viewModel.currentWeekNumber.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    var dynamicCourseColor by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(course) {
        course?.let { c ->
            if (c.color.isEmpty()) {
                dynamicCourseColor = CourseColorProvider.getColorForCourse(c.courseName)
            } else {
                dynamicCourseColor = c.color
            }
        }
    }
    
    // 临时调课 ViewModel
    val adjustmentViewModel: CourseAdjustmentViewModel = hiltViewModel()
    val adjustmentSaveState by adjustmentViewModel.saveState.collectAsState()
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("课程详情") },
                navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // Debug模式：测试通知按钮
                    if (BuildConfig.DEBUG) {
                        IconButton(
                            onClick = {
                                course?.let { c ->
                                    viewModel.sendTestNotification(c)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("已发送测试通知")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, "测试通知")
                        }
                        
                        if (BuildConfig.DEBUG) {
                            // 后台测试按钮（仅在 Debug 构建中可见）
                            IconButton(
                                onClick = {
                                    course?.let { c ->
                                        // 检查全屏通知权限（Android 14+）
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                            if (!notificationManager.canUseFullScreenIntent()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("需要开启全屏通知权限才能在后台弹出，即将跳转到设置页面")
                                                }
                                                try {
                                                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(intent)
                                                }
                                                return@IconButton
                                            }
                                        }
                                        
                                        viewModel.sendBackgroundTestNotification(c, 10)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("10秒后将发送通知，请切换到后台观察")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Schedule, "后台测试")
                            }
                        }
                        
                        // 通知设置按钮
                        IconButton(
                            onClick = {
                                // 跳转到应用通知设置
                                try {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    } else {
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    }
                                    context.startActivity(intent)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("请检查通知权限是否开启")
                                    }
                                } catch (e: Exception) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("无法打开设置页面")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Settings, "通知设置")
                        }
                    }
                    
                    IconButton(onClick = {
                        navController.navigate(Screen.CourseEdit.createRoute(courseId))
                    }) {
                        Icon(Icons.Default.Edit, "编辑")
                    }
                    IconButton(onClick = { viewModel.showDeleteDialog() }) {
                        Icon(Icons.Default.Delete, "删除")
                    }
                }
            )
        }
    ) { paddingValues ->
        course?.let { c ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 课程颜色卡片 - 现代化设计
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .wallpaperAwareBackground(
                            try {
                                val colorStr = dynamicCourseColor ?: c.color
                                Color(android.graphics.Color.parseColor(colorStr))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                ) {
                    val bg = try { 
                        val colorStr = dynamicCourseColor ?: c.color
                        Color(android.graphics.Color.parseColor(colorStr)) 
                    } catch (e: Exception) { MaterialTheme.colorScheme.primaryContainer }
                    val contentColor = contentColorForBackground(bg)
                    val overlayAlpha = topGradientOverlayAlpha(bg)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = overlayAlpha)
                                    )
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 课程图标
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = contentColor.copy(alpha = 0.15f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = "课程图标",
                                        tint = contentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 课程名称（共享元素）
                            Text(
                                text = c.courseName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 基本信息
                InfoSection(title = "基本信息") {
                    InfoItem(label = "教师", value = c.teacher.ifEmpty { "未设置" })
                    InfoItem(label = "教室", value = c.classroom.ifEmpty { "未设置" })
                    InfoItem(
                        label = "学分", 
                        value = if (c.credit > 0) "${c.credit}" else "未设置"
                    )
                    if (c.courseCode.isNotEmpty()) {
                        InfoItem(label = "课程代码", value = c.courseCode)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 时间信息
                InfoSection(title = "时间安排") {
                    InfoItem(label = "星期", value = DateUtils.getDayOfWeekName(c.dayOfWeek))
                    InfoItem(label = "节次", value = "第${c.startSection}节 (共${c.sectionCount}节)")
                    InfoItem(
                        label = "周次",
                        value = if (c.weekExpression.isNotEmpty()) {
                            c.weekExpression
                        } else {
                            WeekParser.formatWeekList(c.weeks)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 临时调课功能卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "临时调课",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "如遇临时调课，可快速调整本周或其他周次的上课时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { viewModel.showAdjustmentDialog() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("设置临时调课")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 考试安排卡片
                ExamInfoSection(
                    course = c,
                    viewModel = viewModel,
                    navController = navController
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 提醒设置卡片 - 带开关
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(6.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {}
                        
                            Text(
                                text = "提醒设置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 通知开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "上课提醒",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (c.reminderEnabled) "已开启" else "已关闭",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = c.reminderEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.toggleReminderEnabled(enabled)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(if (enabled) "已开启上课提醒" else "已关闭上课提醒")
                                    }
                                }
                            )
                        }
                        
                        // 提前提醒时间设置（仅在开启提醒时显示）
                        if (c.reminderEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                            InfoItem(
                                label = "提前提醒",
                                value = "${c.reminderMinutes}分钟"
                            )
                        }

                        if (BuildConfig.DEBUG) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                            Text(
                                text = "后台通知测试（开发专用）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                        if (!notificationManager.canUseFullScreenIntent()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("需要开启全屏通知权限才能在后台弹出，即将跳转设置")
                                            }
                                            try {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }
                                            return@Button
                                        }
                                    }
                                    viewModel.sendBackgroundTestNotification(c, 10)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("10秒后将发送通知，请切换到后台观察")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("后台通知测试（10秒）")
                            }
                        }
                    }
                }
                
                // 备注
                if (c.note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoSection(title = "备注") {
                        Text(
                            text = c.note,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("删除课程") },
            text = { Text("确定要删除这门课程吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCourse()
                    navController.navigateUp()
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
    
    // 临时调课对话框
    if (showAdjustmentDialog && course != null && currentSchedule != null) {
        LaunchedEffect(Unit) {
            adjustmentViewModel.loadCourse(courseId, currentWeekNumber)
        }
        
        LaunchedEffect(adjustmentSaveState) {
            when (val state = adjustmentSaveState) {
                is CourseAdjustmentViewModel.SaveState.Success -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(state.message)
                    }
                    viewModel.hideAdjustmentDialog()
                    adjustmentViewModel.resetSaveState()
                }
                is CourseAdjustmentViewModel.SaveState.Error -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(state.message)
                    }
                    adjustmentViewModel.resetSaveState()
                }
                else -> {}
            }
        }

        val currentCourse = course
        val currentSch = currentSchedule
        if (currentCourse != null && currentSch != null) {
            com.wind.ggbond.classtime.ui.screen.course.components.CourseAdjustmentDialog(
                course = currentCourse,
                currentWeekNumber = adjustmentViewModel.originalWeekNumber.collectAsState().value,
                totalWeeks = currentSch.totalWeeks,
            newWeekNumber = adjustmentViewModel.newWeekNumber.collectAsState().value,
            newDayOfWeek = adjustmentViewModel.newDayOfWeek.collectAsState().value,
            newStartSection = adjustmentViewModel.newStartSection.collectAsState().value,
            newSectionCount = adjustmentViewModel.newSectionCount.collectAsState().value,
            newClassroom = adjustmentViewModel.newClassroom.collectAsState().value,
            reason = adjustmentViewModel.reason.collectAsState().value,
            hasConflict = adjustmentViewModel.hasConflict.collectAsState().value,
            conflictMessage = adjustmentViewModel.conflictMessage.collectAsState().value,
            isSaving = adjustmentSaveState is CourseAdjustmentViewModel.SaveState.Saving,
            onNewWeekNumberChange = { adjustmentViewModel.setNewWeekNumber(it) },
            onNewDayOfWeekChange = { adjustmentViewModel.setNewDayOfWeek(it) },
            onNewStartSectionChange = { adjustmentViewModel.setNewStartSection(it) },
            onNewSectionCountChange = { adjustmentViewModel.setNewSectionCount(it) },
            onNewClassroomChange = { adjustmentViewModel.setNewClassroom(it) },
            onReasonChange = { adjustmentViewModel.setReason(it) },
            onConfirm = { adjustmentViewModel.saveAdjustment() },
            onDismiss = { viewModel.hideAdjustmentDialog() }
            )
        }
    }

    // 添加考试对话框
    val courseVal = course
    val scheduleVal = currentSchedule
    if (showAddExamDialog && courseVal != null && scheduleVal != null) {
        com.wind.ggbond.classtime.ui.screen.course.components.AddExamDialog(
            course = courseVal,
            currentWeek = currentWeekNumber,
            totalWeeks = scheduleVal.totalWeeks,
            onDismiss = { viewModel.hideAddExamDialog() },
            onConfirm = { exam ->
                viewModel.saveExam(exam)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("考试已添加")
                }
                viewModel.hideAddExamDialog()
            }
        )
    }
}

@Composable
fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {}
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
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

@Composable
fun ExamInfoSection(
    course: com.wind.ggbond.classtime.data.local.entity.Course,
    viewModel: CourseDetailViewModel,
    navController: NavController
) {
    val exams by viewModel.exams.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "考试安排",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                
                // 添加考试按钮
                TextButton(onClick = { viewModel.showAddExamDialog() }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
            }
            
            if (exams.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "暂无考试安排",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                
                exams.forEach { exam ->
                    ExamInfoItem(
                        exam = exam,
                        onDelete = { viewModel.deleteExam(exam.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ExamInfoItem(
    exam: com.wind.ggbond.classtime.data.local.entity.Exam,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = exam.examType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 时间信息
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = exam.getTimeDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 地点信息
            if (exam.location.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = exam.location + if (exam.seat.isNotEmpty()) " (${exam.seat})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 备注
            if (exam.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = exam.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除考试") },
            text = { Text("确定要删除这场考试吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}



