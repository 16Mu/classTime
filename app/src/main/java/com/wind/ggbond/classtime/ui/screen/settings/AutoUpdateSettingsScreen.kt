package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.viewmodel.AutoUpdateViewModel
import com.wind.ggbond.classtime.util.AutoLoginManager
import com.wind.ggbond.classtime.util.AutoLoginResultCode
import com.wind.ggbond.classtime.util.AutoLoginResultMessages
import com.wind.ggbond.classtime.util.AutoUpdateManager
import com.wind.ggbond.classtime.util.UpdateLogEntry
import com.wind.ggbond.classtime.util.UpdateStatus
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoLoginManagerEntryPoint {
    fun autoLoginManager(): AutoLoginManager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoUpdateSettingsScreen(
    navController: NavController,
    viewModel: AutoUpdateViewModel = hiltViewModel(),
    settingsViewModel: AutoUpdateSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val autoLoginManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoLoginManagerEntryPoint::class.java
        ).autoLoginManager()
    }
    
    val config by viewModel.config.collectAsState()
    val updateLogs by viewModel.updateLogs.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val showIntervalDialog by viewModel.showIntervalDialog.collectAsState()
    val showClearStatsDialog by viewModel.showClearStatsDialog.collectAsState()
    
    // 间隔和定时更新状态
    val intervalUpdateEnabled by settingsViewModel.intervalUpdateEnabled.collectAsState()
    val scheduledUpdateEnabled by settingsViewModel.scheduledUpdateEnabled.collectAsState()
    val scheduledUpdateTime by settingsViewModel.scheduledUpdateTime.collectAsState()
    val showScheduledTimeDialog by settingsViewModel.showScheduledTimeDialog.collectAsState()
    
    // 自动登录相关状态
    var autoLoginEnabled by remember { mutableStateOf(autoLoginManager.isAutoLoginEnabled()) }
    var username by remember { mutableStateOf(autoLoginManager.getUsername() ?: "") }
    var password by remember { mutableStateOf(autoLoginManager.getPassword() ?: "") }
    var showAutoLoginDialog by remember { mutableStateOf(false) }
    var isEditingCredentials by remember { mutableStateOf(false) }
    var lastUpdateResultCode by remember { mutableStateOf(autoLoginManager.getLastUpdateResultCode() ?: "") }
    var lastUpdateResultMessage by remember { mutableStateOf(autoLoginManager.getLastUpdateResultMessage() ?: "") }
    var lastUpdateTime by remember { mutableStateOf(autoLoginManager.getLastUpdateTime()) }
    var hasCredentials by remember { mutableStateOf(autoLoginManager.hasCredentials()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            lastUpdateResultCode = autoLoginManager.getLastUpdateResultCode() ?: ""
            lastUpdateResultMessage = autoLoginManager.getLastUpdateResultMessage() ?: ""
            lastUpdateTime = autoLoginManager.getLastUpdateTime()
            hasCredentials = autoLoginManager.hasCredentials()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("自动更新设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 启用开关
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "启用自动更新",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "每次打开应用时自动检查课表变化",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { viewModel.toggleEnabled(it) }
                        )
                    }
                }
            }
            
            // 间隔更新配置
            item {
                AnimatedVisibility(
                    visible = config.enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "间隔自动更新",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 间隔更新开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "启用间隔更新",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "每隔指定时间检查一次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Switch(
                                    checked = intervalUpdateEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.updateIntervalUpdateEnabled(enabled)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 更新间隔选择
                            ListItem(
                                headlineContent = { Text("更新间隔") },
                                supportingContent = { Text("避免频繁更新消耗流量") },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${config.minIntervalHours}小时",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    viewModel.showIntervalPicker()
                                }
                            )
                        }
                    }
                }
            }
            
            // 定时更新配置
            item {
                AnimatedVisibility(
                    visible = config.enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "定时自动更新",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 定时更新开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "启用定时更新",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "每天在指定时间自动更新",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Switch(
                                    checked = scheduledUpdateEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.updateScheduledUpdateEnabled(enabled)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 定时时间选择
                            ListItem(
                                headlineContent = { Text("更新时间") },
                                supportingContent = { Text("每天在此时间执行更新") },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            scheduledUpdateTime,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    settingsViewModel.showScheduledTimeDialog()
                                }
                            )
                        }
                    }
                }
            }
            
            // 账号管理区域（与自动更新开关同级，无论是否开启都显示）
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "账号管理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 当前账号显示或编辑提示
                        if (hasCredentials && !isEditingCredentials) {
                            // 已保存账号状态
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                "已保存账号",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                autoLoginManager.getUsername() ?: "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            username = autoLoginManager.getUsername() ?: ""
                                            password = ""
                                            isEditingCredentials = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "编辑",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 清除按钮
                            OutlinedButton(
                                onClick = {
                                    autoLoginManager.clearCredentials()
                                    username = ""
                                    password = ""
                                    hasCredentials = false
                                    isEditingCredentials = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("清除账号")
                            }
                        } else {
                            // 未保存账号状态 - 显示编辑表单
                            Text(
                                "输入账号密码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 账号输入框
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("账号") },
                                placeholder = { Text("学号/工号/邮箱") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 密码输入框
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("密码") },
                                placeholder = { Text("登录密码") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 保存按钮
                            Button(
                                onClick = {
                                    if (username.isNotBlank() && password.isNotBlank()) {
                                        autoLoginManager.saveCredentials(username, password)
                                        // 保存成功后清空输入框并更新状态
                                        username = ""
                                        password = ""
                                        hasCredentials = true
                                        isEditingCredentials = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = username.isNotBlank() && password.isNotBlank()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("保存账号")
                            }
                        }
                    }
                }
            }
            
            // 最近一次自动更新结果（仅在有保存账号时显示）
            item {
                AnimatedVisibility(
                    visible = hasCredentials && lastUpdateResultCode.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (lastUpdateResultCode) {
                                AutoLoginResultCode.OK -> MaterialTheme.colorScheme.surfaceContainerLow
                                AutoLoginResultCode.NEED_CAPTCHA -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                when (lastUpdateResultCode) {
                                    AutoLoginResultCode.OK -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = when (lastUpdateResultCode) {
                                    AutoLoginResultCode.OK -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "最近一次自动更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    lastUpdateResultMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (lastUpdateTime > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                            .format(java.util.Date(lastUpdateTime)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 更新统计
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 标题栏：包含标题和清空按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "更新统计",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            // 清空统计按钮
                            IconButton(
                                onClick = { viewModel.showClearStatsDialog() },
                                enabled = config.totalAttempts > 0 || config.totalChangesDetected > 0
                            ) {
                                Icon(
                                    Icons.Default.RestartAlt,
                                    contentDescription = "清空统计",
                                    tint = if (config.totalAttempts > 0 || config.totalChangesDetected > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatItem("总次数", config.totalAttempts, Color.Gray)
                            StatItem("成功", config.successCount, MaterialTheme.colorScheme.primary)
                            StatItem("失败", config.failureCount, MaterialTheme.colorScheme.error)
                            StatItem("跳过", config.skipCount, MaterialTheme.colorScheme.tertiary)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // 累计发现的课程更新
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "累计发现课程更新",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    "${config.totalChangesDetected} 次",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            // 手动更新按钮（仅在有保存账号时显示）
            item {
                if (hasCredentials) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                // 触发手动更新（使用自动登录）
                                viewModel.updateNow()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            enabled = !isUpdating
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("更新中...")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("手动更新（使用保存账号）")
                            }
                        }
                        
                        Text(
                            "立即使用保存的账号登录并更新课表",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // 更新日志标题
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "更新日志",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    TextButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("清空日志")
                    }
                }
            }
            
            // 日志列表
            if (updateLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无更新日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(updateLogs) { log ->
                    UpdateLogItem(log)
                }
            }
        }
    }
    
    // 间隔选择器对话框
    if (showIntervalDialog) {
        IntervalPickerDialog(
            currentInterval = config.minIntervalHours,
            onDismiss = { viewModel.hideIntervalPicker() },
            onConfirm = { hours ->
                viewModel.setInterval(hours)
                viewModel.hideIntervalPicker()
            }
        )
    }
    
    // 定时时间选择器对话框
    if (showScheduledTimeDialog) {
        ScheduledTimePickerDialog(
            currentTime = scheduledUpdateTime,
            onDismiss = { settingsViewModel.hideScheduledTimeDialog() },
            onConfirm = { time ->
                settingsViewModel.updateScheduledUpdateTime(time)
                settingsViewModel.hideScheduledTimeDialog()
            }
        )
    }
    
    // 清空统计确认对话框
    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearStatsDialog() },
            icon = {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("清空统计数据")
            },
            text = {
                Column {
                    Text("确定要清空所有统计数据吗？")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "包括：总次数、成功、失败、跳过、累计发现课程更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ 此操作不可恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearStatistics()
                        viewModel.hideClearStatsDialog()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearStatsDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun UpdateLogItem(log: UpdateLogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (log.status) {
                UpdateStatus.SUCCESS -> MaterialTheme.colorScheme.surfaceContainerLow
                UpdateStatus.FAILURE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                UpdateStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                when (log.status) {
                    UpdateStatus.SUCCESS -> Icons.Default.CheckCircle
                    UpdateStatus.FAILURE -> Icons.Default.Error
                    UpdateStatus.SKIPPED -> Icons.Default.Block
                },
                contentDescription = null,
                tint = when (log.status) {
                    UpdateStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    UpdateStatus.FAILURE -> MaterialTheme.colorScheme.error
                    UpdateStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.padding(top = 2.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(log.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    when (log.status) {
                        UpdateStatus.SUCCESS -> "自动更新"
                        UpdateStatus.FAILURE -> "更新失败"
                        UpdateStatus.SKIPPED -> "跳过更新"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (log.message.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    
                    // 判断消息是否包含变更详情（如"新增"、"删除"、"调课"）
                    val isChangeDetails = log.message.contains("新增") || 
                                         log.message.contains("删除") || 
                                         log.message.contains("调课")
                    
                    Text(
                        log.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            log.message.contains("无课程更新") -> MaterialTheme.colorScheme.onSurfaceVariant
                            isChangeDetails -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (isChangeDetails || log.message.contains("无课程更新")) {
                            FontWeight.Medium
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
                
                if (log.duration > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "耗时: ${log.duration}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IntervalPickerDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedInterval by remember { mutableStateOf(currentInterval) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择更新间隔") },
        text = {
            Column {
                AutoUpdateManager.INTERVAL_OPTIONS.forEach { hours ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedInterval = hours }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedInterval == hours,
                            onClick = { selectedInterval = hours }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$hours 小时")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedInterval) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ScheduledTimePickerDialog(
    currentTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedHour by remember { mutableStateOf(currentTime.split(":")[0].toIntOrNull() ?: 7) }
    var selectedMinute by remember { mutableStateOf(currentTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择更新时间") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 小时选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("小时:")
                    Button(
                        onClick = { if (selectedHour > 0) selectedHour-- },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("-")
                    }
                    Text(
                        String.format("%02d", selectedHour),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { if (selectedHour < 23) selectedHour++ },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 分钟选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("分钟:")
                    Button(
                        onClick = { if (selectedMinute > 0) selectedMinute -= 5 else selectedMinute = 55 },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("-")
                    }
                    Text(
                        String.format("%02d", selectedMinute),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { if (selectedMinute < 55) selectedMinute += 5 else selectedMinute = 0 },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 显示选中的时间
                Text(
                    "选中时间: ${String.format("%02d:%02d", selectedHour, selectedMinute)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onConfirm(String.format("%02d:%02d", selectedHour, selectedMinute))
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
