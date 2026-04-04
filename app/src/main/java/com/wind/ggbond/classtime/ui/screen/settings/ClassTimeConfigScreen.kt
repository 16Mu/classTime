package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.Screen
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 课程时间设置页面
 * 参考图2设计：按上午/下午分组，支持每节课时长相同开关
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassTimeConfigScreen(
    navController: NavController,
    fromImport: Boolean = false,  // 是否从导入跳转而来
    viewModel: ClassTimeConfigViewModel = hiltViewModel()
) {
    val classTimes by viewModel.classTimes.collectAsState()
    val uniformDuration by viewModel.uniformDuration.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val editingClassTime by viewModel.editingClassTime.collectAsState()
    val breakDuration by viewModel.breakDuration.collectAsState()
    val classDuration by viewModel.classDuration.collectAsState()
    val showBreakDurationDialog by viewModel.showBreakDurationDialog.collectAsState()
    val showClassDurationDialog by viewModel.showClassDurationDialog.collectAsState()
    val morningSectionCount by viewModel.morningSections.collectAsState()
    val afternoonSectionCount by viewModel.afternoonSections.collectAsState()
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val currentConfigName by viewModel.currentConfigName.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    // 底部弹窗状态
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // 将课程时间按上午/下午分组（基于实际设置的节次数）
    val morningSections = remember(classTimes, morningSectionCount) {
        if (morningSectionCount > 0) {
            classTimes.filter { it.sectionNumber <= morningSectionCount }.sortedBy { it.sectionNumber }
        } else {
            emptyList()
        }
    }
    val afternoonSections = remember(classTimes, morningSectionCount, afternoonSectionCount) {
        if (afternoonSectionCount > 0) {
            classTimes.filter { 
                it.sectionNumber > morningSectionCount && 
                it.sectionNumber <= (morningSectionCount + afternoonSectionCount)
            }.sortedBy { it.sectionNumber }
        } else {
            emptyList()
        }
    }
    
    // 当显示对话框时，显示底部弹窗
    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            sheetState.show()
        }
    }
    
    // 监听弹窗关闭
    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && showEditDialog) {
            viewModel.hideEditDialog()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { 
                    Column {
                        Text(if (fromImport) "设置上课时间" else "课程时间设置")
                        currentSchedule?.let { schedule ->
                            Text(
                                text = "${schedule.name} (${currentConfigName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) 
                        if (fromImport) {
                            // 从导入进入，跳转到主界面并强制刷新数据（使用底部Tab的课表路由，确保导航栈正确清理）
                            navController.navigate(Screen.Main.createRoute(refresh = true)) {
                                popUpTo(BottomNavItem.Schedule.route) {
                                    inclusive = true
                                }
                            }
                        } else {
                            // 正常返回
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) 
                        // 保存并返回/跳转
                        if (fromImport) {
                            // 从导入进入，跳转到主界面并强制刷新数据（使用底部Tab的课表路由，确保导航栈正确清理）
                            navController.navigate(Screen.Main.createRoute(refresh = true)) {
                                popUpTo(BottomNavItem.Schedule.route) {
                                    inclusive = true
                                }
                            }
                        } else {
                            // 正常返回
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Default.Check, "确认")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 每节课时长相同和课间时长设置（紧凑布局）
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 课程时长
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.showClassDurationDialog()
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "课程时长",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "设置每节课的时长",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${classDuration}分钟",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // 课间时长
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.showBreakDurationDialog()
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "课间时长",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "设置课间休息时间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${breakDuration}分钟",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // 上午时间段
            if (morningSections.isNotEmpty()) {
                item {
                    Text(
                        text = "上午",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(morningSections) { classTime ->
                    ClassTimeRowItem(
                        classTime = classTime,
                        onClick = { viewModel.showEditDialog(classTime) }
                    )
                }
            }
            
            // 下午时间段
            if (afternoonSections.isNotEmpty()) {
                item {
                    Text(
                        text = "下午",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(afternoonSections) { classTime ->
                    ClassTimeRowItem(
                        classTime = classTime,
                        onClick = { viewModel.showEditDialog(classTime) }
                    )
                }
            }
        }
    }
    
    // 编辑时间底部弹窗
    if (showEditDialog && editingClassTime != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                viewModel.hideEditDialog()
            },
            sheetState = sheetState,  // ✅ 添加 sheetState 参数
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                HorizontalDivider(
                    modifier = Modifier
                        .width(40.dp)
                        .padding(vertical = 12.dp),
                    thickness = 4.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            )
        ) {
            ClassTimeEditBottomSheet(
                classTime = editingClassTime!!,
                uniformDuration = uniformDuration,
                classDuration = classDuration,
                onDismiss = { viewModel.hideEditDialog() },
                onConfirm = { sectionNumber, startTime, endTime ->
                    val updatedClassTime = editingClassTime!!.copy(
                        sectionNumber = sectionNumber,
                        startTime = startTime,
                        endTime = endTime
                    )
                    
                    // 如果启用了统一时长，应用到所有节次
                    if (uniformDuration) {
                        viewModel.updateClassTimeAndApplyUniform(updatedClassTime)
                    } else {
                        // 否则只更新当前节次并调整后续节次
                        viewModel.updateClassTime(updatedClassTime)
                    }
                    
                    // ✅ 修复：延迟关闭底部卡片，确保保存操作完成
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(300) // 等待300ms确保保存完成
                        viewModel.hideEditDialog()
                    }
                }
            )
        }
    }
    
    // 课程时长编辑对话框
    if (showClassDurationDialog) {
        ClassDurationDialog(
            currentDuration = classDuration,
            onDismiss = { viewModel.hideClassDurationDialog() },
            onConfirm = { minutes ->
                viewModel.updateClassDuration(minutes)
                viewModel.hideClassDurationDialog()
            }
        )
    }
    
    // 课间时长编辑对话框
    if (showBreakDurationDialog) {
        BreakDurationDialog(
            currentDuration = breakDuration,
            onDismiss = { viewModel.hideBreakDurationDialog() },
            onConfirm = { minutes ->
                viewModel.updateBreakDuration(minutes)
                viewModel.hideBreakDurationDialog()
            }
        )
    }
}

/**
 * 课程时间行项目
 */
@Composable
fun ClassTimeRowItem(
    classTime: ClassTime,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val timeRange = "${classTime.startTime.format(timeFormatter)}-${classTime.endTime.format(timeFormatter)}"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第${classTime.sectionNumber}节",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = timeRange,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 编辑课程时间底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassTimeEditBottomSheet(
    classTime: ClassTime,
    uniformDuration: Boolean,
    classDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, LocalTime, LocalTime) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // 使用classTime.id作为key来标识不同的课程
    val dialogKey = remember(classTime.id) { Any() }
    
    // 使用 remember(classTime.id) 来确保每次打开不同课程时重新初始化
    var startHour by remember(classTime.id) { mutableIntStateOf(classTime.startTime.hour) }
    var startMinute by remember(classTime.id) { mutableIntStateOf(classTime.startTime.minute) }
    var endHour by remember(classTime.id) { mutableIntStateOf(classTime.endTime.hour) }
    var endMinute by remember(classTime.id) { mutableIntStateOf(classTime.endTime.minute) }
    
    // 使用全局设置的课程时长（而不是当前课程的实际时长）
    val fixedDurationMinutes = classDuration
    
    // 标志位：是否正在初始化
    var isInitializing by remember(classTime.id) { mutableStateOf(true) }
    
    // 初始化完成后，标记为 false
    LaunchedEffect(classTime.id) {
        kotlinx.coroutines.delay(100) // 短暂延迟确保状态已设置
        isInitializing = false
    }
    
    // 当开始时间改变时，自动调整结束时间（保持固定时长）
    // 但在初始化时不执行，避免覆盖初始值
    LaunchedEffect(startHour, startMinute, isInitializing) {
        if (!isInitializing) {
            val newStartTime = LocalTime.of(startHour, startMinute)
            val newEndTime = newStartTime.plusMinutes(fixedDurationMinutes.toLong())
            endHour = newEndTime.hour
            endMinute = newEndTime.minute
        }
    }
    
    // 计算时长（分钟）
    val durationMinutes = remember(startHour, startMinute, endHour, endMinute) {
        val start = LocalTime.of(startHour, startMinute)
        val end = LocalTime.of(endHour, endMinute)
        java.time.Duration.between(start, end).toMinutes().toInt()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "编辑第${classTime.sectionNumber}节时间",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        // 统一时长提示
        if (uniformDuration) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "统一时长模式：修改此节次时间将应用到所有节次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // 时间段选择器（整节课时间）
        Text(
            text = "请调节整节课时间（本节 ${durationMinutes} 分钟）",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        TimeRangePicker(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            onStartHourChange = { startHour = it },
            onStartMinuteChange = { startMinute = it },
            onEndHourChange = { endHour = it },
            onEndMinuteChange = { endMinute = it },
            resetKey = dialogKey
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 分隔线
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 操作按钮 - 增大高度和字体
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp) // 增加按钮高度
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val startTime = LocalTime.of(startHour, startMinute)
                    val endTime = LocalTime.of(endHour, endMinute)
                    onConfirm(classTime.sectionNumber, startTime, endTime)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp) // 增加按钮高度
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 课程时长编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDurationDialog(
    currentDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dialogKey = remember(currentDuration) { Any() }
    var selectedDuration by remember(currentDuration) { mutableIntStateOf(currentDuration) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(vertical = 12.dp),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = "设置课程时长",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 说明文字
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "设置每节课的上课时间长度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            // 横向滚轮选择器
            HorizontalNumberPicker(
                value = selectedDuration,
                range = 30..120,
                onValueChange = { selectedDuration = it },
                label = "分钟",
                resetKey = dialogKey
            )
            
            // 建议文本
            Text(
                text = "建议设置40-50分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 分隔线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfirm(selectedDuration)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = "确定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 课间时长编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakDurationDialog(
    currentDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dialogKey = remember(currentDuration) { Any() }
    var selectedDuration by remember(currentDuration) { mutableIntStateOf(currentDuration) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(vertical = 12.dp),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = "设置课间时长",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 说明文字
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "设置每节课之间的休息时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            // 横向滚轮选择器
            HorizontalNumberPicker(
                value = selectedDuration,
                range = 1..60,
                onValueChange = { selectedDuration = it },
                label = "分钟",
                resetKey = dialogKey
            )
            
            // 建议文本
            Text(
                text = "建议设置5-20分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 分隔线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作按钮 - 增大高度和字体
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp) // 增加按钮高度
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfirm(selectedDuration)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp) // 增加按钮高度
                ) {
                    Text(
                        text = "确定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 节次数编辑对话框（通用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionCountDialog(
    title: String,
    currentSections: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dialogKey = remember(currentSections) { Any() }
    var selectedSections by remember(currentSections) { mutableIntStateOf(currentSections) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(vertical = 12.dp),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 说明文字
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "系统会根据设置自动生成课程时间表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            // 横向滚轮选择器
            HorizontalNumberPicker(
                value = selectedSections,
                range = 0..8,
                onValueChange = { selectedSections = it },
                label = "节",
                resetKey = dialogKey
            )
            
            // 建议文本
            Text(
                text = "设置为0表示没有${if (title.contains("上午")) "上午" else "下午"}课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 分隔线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作按钮 - 增大高度和字体
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp) // 增加按钮高度
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfirm(selectedSections)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp) // 增加按钮高度
                ) {
                    Text(
                        text = "确定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 时间选择器行（包含小时和分钟的滚轮选择器）
 */
@Composable
fun TimePickerRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    resetKey: Any? = null // 接收外部的重置key
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 小时滚轮
        NumberPicker(
            value = hour,
            range = 0..23,
            onValueChange = onHourChange,
            modifier = Modifier.weight(1f),
            label = "时",
            resetKey = resetKey // 传递重置key
        )
        
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // 分钟滚轮
        NumberPicker(
            value = minute,
            range = 0..59,
            onValueChange = onMinuteChange,
            modifier = Modifier.weight(1f),
            label = "分",
            resetKey = resetKey // 传递重置key
        )
    }
}

/**
 * iOS风格的数字滚轮选择器（支持无限滚动和震动反馈）
 * 使用 layoutInfo 精确计算视口中心，视觉效果基于连续 float 距离实现平滑过渡
 */
@Composable
fun NumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    resetKey: Any? = null // 当这个key变化时，重新对齐到value
) {
    val values = remember(range) { range.toList() }
    val haptic = LocalHapticFeedback.current
    
    // 创建无限滚动的数据（重复50次，足够滚动且不浪费内存）
    val repeatCount = 50
    val infiniteValues = remember(values) {
        List(repeatCount) { values }.flatten()
    }
    
    // 计算中间位置的索引
    val middleRepeatIndex = repeatCount / 2
    
    // 根据当前value计算目标索引（定位到中间重复段）
    fun getTargetIndex(currentValue: Int): Int {
        val valueIndex = values.indexOf(currentValue).coerceAtLeast(0)
        return middleRepeatIndex * values.size + valueIndex
    }
    
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = getTargetIndex(value))
    
    // 当resetKey或value变化时重新对齐
    LaunchedEffect(resetKey, value) {
        // 短暂延迟确保列表已渲染
        kotlinx.coroutines.delay(50)
        // 滚动到目标位置
        val targetIndex = getTargetIndex(value)
        listState.scrollToItem(targetIndex)
    }
    
    // 精确计算视口中心最近的 item 索引（基于 layoutInfo 布局信息）
    fun getCenterIndex(): Int {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.visibleItemsInfo.isEmpty()) {
            return listState.firstVisibleItemIndex.coerceIn(0, infiniteValues.size - 1)
        }
        // 视口中心坐标（像素）
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        // 遍历可见 item，找到距离视口中心最近的那个
        var closestItem = layoutInfo.visibleItemsInfo.first()
        var minDistance = Int.MAX_VALUE
        for (item in layoutInfo.visibleItemsInfo) {
            val itemCenter = item.offset + item.size / 2
            val distance = abs(itemCenter - viewportCenter)
            if (distance < minDistance) {
                minDistance = distance
                closestItem = item
            }
        }
        return closestItem.index.coerceIn(0, infiniteValues.size - 1)
    }
    
    // 实时监听滚动，每经过一个数字就震动
    var lastCenterValue by remember { mutableStateOf(value) }
    LaunchedEffect(Unit) {
        snapshotFlow { 
            // 同时监听 index 和 offset，确保连续滚动时也能检测到值变化
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collectLatest { _ ->
            val centerIndex = getCenterIndex()
            if (centerIndex in infiniteValues.indices) {
                val centerValue = infiniteValues[centerIndex]
                if (centerValue != lastCenterValue) {
                    lastCenterValue = centerValue
                    // 每滚动到新的数字就震动
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }
    
    // 当列表停止滚动时，更新选中的值
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = getCenterIndex()
            if (centerIndex in infiniteValues.indices) {
                val newValue = infiniteValues[centerIndex]
                if (newValue != value) {
                    onValueChange(newValue)
                }
            }
        }
    }
    
    Box(
        modifier = modifier.height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // 中间选中区域的背景
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small
        ) {}
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(vertical = 50.dp) // 上下留白使首尾 item 可滚到中心
        ) {
            // 数字项（无限滚动，视觉效果基于连续距离）
            items(infiniteValues.size) { index ->
                val number = infiniteValues[index]
                
                // 基于 layoutInfo 计算该 item 距视口中心的连续距离（单位：item 个数）
                val distanceFromCenter by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                        if (itemInfo != null) {
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            // 返回连续的浮点距离（0.0 = 完全居中，1.0 = 偏移一个 item）
                            abs(itemCenter - viewportCenter) / itemInfo.size.toFloat()
                        } else {
                            // item 不在可见范围，给一个较大值
                            3f
                        }
                    }
                }
                
                // 透明度：中心为1.0，随距离平滑衰减
                val alpha = (1f - distanceFromCenter * 0.35f).coerceIn(0.1f, 1f)
                
                // 缩放：中心为1.0，随距离平滑缩小
                val scale = (1f - distanceFromCenter * 0.15f).coerceIn(0.6f, 1f)
                
                // 判断是否为选中项（距离中心不到半个 item）
                val isSelected = distanceFromCenter < 0.5f
                
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                        .graphicsLayer {
                            // 应用平滑缩放和透明度
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", number),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = if (isSelected) 24.sp else 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1, // 防止数字换行
                        softWrap = false, // 禁止软换行
                        overflow = TextOverflow.Visible // 允许溢出显示，避免截断
                    )
                }
            }
        }
        
        // 标签
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
    }
}

/**
 * 时间段选择器（一体化选择开始和结束时间）
 * 显示格式：HH:mm - HH:mm
 */
@Composable
fun TimeRangePicker(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartHourChange: (Int) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
    resetKey: Any? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 开始时间 - 小时（按比例分配宽度，避免窄屏溢出）
        NumberPicker(
            value = startHour,
            range = 0..23,
            onValueChange = onStartHourChange,
            modifier = Modifier.weight(1f),
            label = "",
            resetKey = resetKey
        )
        
        Text(
            text = "时",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 开始时间 - 分钟
        NumberPicker(
            value = startMinute,
            range = 0..59,
            onValueChange = onStartMinuteChange,
            modifier = Modifier.weight(1f),
            label = "",
            resetKey = resetKey
        )
        
        Text(
            text = "分",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 分隔符
        Text(
            text = " - ",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 结束时间 - 小时
        NumberPicker(
            value = endHour,
            range = 0..23,
            onValueChange = onEndHourChange,
            modifier = Modifier.weight(1f),
            label = "",
            resetKey = resetKey
        )
        
        Text(
            text = "时",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 结束时间 - 分钟
        NumberPicker(
            value = endMinute,
            range = 0..59,
            onValueChange = onEndMinuteChange,
            modifier = Modifier.weight(1f),
            label = "",
            resetKey = resetKey
        )
        
        Text(
            text = "分",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 横向数字滚轮选择器（类似相机缩放滚轮，支持无限滚动和震动反馈）
 */
@Composable
fun HorizontalNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    resetKey: Any? = null // 当这个key变化时，重新对齐到value
) {
    val values = remember(range) { range.toList() }
    val haptic = LocalHapticFeedback.current
    
    // 创建无限滚动的数据（重复100次）
    val repeatCount = 100
    val infiniteValues = remember(values) {
        List(repeatCount) { values }.flatten()
    }
    
    // 计算中间位置的索引
    val middleRepeatIndex = repeatCount / 2
    
    // 根据当前value计算目标索引
    fun getTargetIndex(currentValue: Int): Int {
        val valueIndex = values.indexOf(currentValue).coerceAtLeast(0)
        return middleRepeatIndex * values.size + valueIndex
    }
    
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // 每个刻度item的宽度（dp）
    val itemWidthDp = 16.dp
    // contentPadding（dp），用于让第一个item居中显示
    val contentPaddingDp = 180.dp

    // 首次显示和value变化时都要对齐
    LaunchedEffect(value, resetKey) {
        // 增加延迟确保列表完全渲染
        kotlinx.coroutines.delay(300)
        val targetIndex = getTargetIndex(value)
        listState.scrollToItem(targetIndex)
    }
    
    // 计算当前视觉中心位置的索引（基于布局信息精确计算）
    fun getCenterIndex(): Int {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.visibleItemsInfo.isEmpty()) {
            return listState.firstVisibleItemIndex
        }
        // 视口中心坐标（像素）
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        // 找到距离视口中心最近的item
        var closestItem = layoutInfo.visibleItemsInfo.first()
        var minDistance = Int.MAX_VALUE
        for (item in layoutInfo.visibleItemsInfo) {
            val itemCenter = item.offset + item.size / 2
            val distance = abs(itemCenter - viewportCenter)
            if (distance < minDistance) {
                minDistance = distance
                closestItem = item
            }
        }
        return closestItem.index.coerceIn(0, infiniteValues.size - 1)
    }
    
    // 记录上一次的中心值，用于震动反馈
    var lastCenterValue by remember { mutableStateOf(value) }
    
    // 实时监听滚动，每经过一个数字就震动
    LaunchedEffect(Unit) {
        snapshotFlow { 
            // 监听滚动位置变化（包括offset）
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collectLatest { _ ->
            val centerIndex = getCenterIndex()
            if (centerIndex in infiniteValues.indices) {
                val centerValue = infiniteValues[centerIndex]
                if (centerValue != lastCenterValue) {
                    lastCenterValue = centerValue
                    // 每滚动到新的数字就震动
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }
    
    // 当列表停止滚动时，更新选中的值并snap对齐
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = getCenterIndex()
            if (centerIndex in infiniteValues.indices) {
                val newValue = infiniteValues[centerIndex]
                if (newValue != value) {
                    onValueChange(newValue)
                }
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 当前选中的值（大字显示）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (label.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // 横向滚轮 - 使用BoxWithConstraints动态计算contentPadding，适配不同屏幕
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 动态计算contentPadding：容器宽度的一半减去半个item宽度，使item能滚到正中心
            val halfContainerWidth = maxWidth / 2
            val centerPadding = halfContainerWidth - (itemWidthDp / 2)

            // 中间选中区域的指示器（垂直线）
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(45.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(2.dp)
                        .height(45.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(1.dp)
                ) {}
            }
            
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                contentPadding = PaddingValues(horizontal = centerPadding)
            ) {
                items(infiniteValues.size) { index ->
                    val number = infiniteValues[index]
                    // 基于视觉中心索引判断是否选中（而非firstVisibleItemIndex）
                    val centerIdx = getCenterIndex()
                    val isSelected = centerIdx == index
                    
                    // 刻度线（更细更密集，类似相机刻度）
                    Box(
                        modifier = Modifier
                            .width(16.dp)  // 进一步减小到16dp，更密集
                            .height(60.dp)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            // 细长的刻度线（模拟相机刻度）
                            Surface(
                                modifier = Modifier
                                    .width(
                                        when {
                                            isSelected -> 2.dp  // 选中时稍粗
                                            number % 10 == 0 -> 1.5.dp  // 10的倍数
                                            number % 5 == 0 -> 1.2.dp  // 5的倍数
                                            else -> 1.dp  // 其他刻度
                                        }
                                    )
                                    .height(
                                        when {
                                            isSelected -> 40.dp  // 选中时最长
                                            number % 10 == 0 -> 32.dp  // 10的倍数较长
                                            number % 5 == 0 -> 24.dp  // 5的倍数中等
                                            else -> 16.dp  // 其他较短
                                        }
                                    ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = when {
                                            number % 10 == 0 -> 0.5f
                                            number % 5 == 0 -> 0.4f
                                            else -> 0.25f
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(0.5.dp)
                            ) {}
                            
                            // 数字标签（只在10的倍数或选中时显示）
                            if (number % 10 == 0 || isSelected) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = number.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    },
                                    fontSize = if (isSelected) 12.sp else 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
