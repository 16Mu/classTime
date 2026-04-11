package com.wind.ggbond.classtime.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import com.wind.ggbond.classtime.util.MonetColorPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BackgroundSettingsScreen(
    navController: NavController,
    viewModel: BackgroundSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        settingsViewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    // 莫奈取色状态
    val monetEnabled by settingsViewModel.monetEnabled.collectAsState()
    val desktopModeEnabled by settingsViewModel.desktopModeEnabled.collectAsState()
    val courseColorSaturation by settingsViewModel.courseColorSaturation.collectAsState()
    val previewColors by settingsViewModel.observeCourseColors(
        saturationLevel = when (courseColorSaturation) {
            0 -> MonetColorPalette.SaturationLevel.SOFT
            2 -> MonetColorPalette.SaturationLevel.VIBRANT
            else -> MonetColorPalette.SaturationLevel.STANDARD
        }
    ).collectAsState(initial = emptyList())
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }
    
    val gifPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onGifSelected(uri)
        }
    }
    
    LaunchedEffect(uiState.showImagePicker) {
        if (uiState.showImagePicker) {
            imagePickerLauncher.launch("image/*")
            viewModel.hideImagePicker()
        }
    }
    
    LaunchedEffect(uiState.showVideoPicker) {
        if (uiState.showVideoPicker) {
            videoPickerLauncher.launch("video/*")
            viewModel.hideVideoPicker()
        }
    }
    
    LaunchedEffect(uiState.showGifPicker) {
        if (uiState.showGifPicker) {
            gifPickerLauncher.launch("image/gif")
            viewModel.hideGifPicker()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("自定义背景与主题") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (uiState.backgroundSchemes.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAllBackgrounds() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("恢复默认", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== 第1行：预览 + 添加按钮 ====================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 左侧：当前背景预览（缩小版）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.activeScheme != null) {
                            val activeScheme = uiState.activeScheme!!
                            when (activeScheme.type) {
                                com.wind.ggbond.classtime.ui.theme.BackgroundType.IMAGE,
                                com.wind.ggbond.classtime.ui.theme.BackgroundType.GIF -> {
                                    val previewBlur = if (uiState.blurRadius > 0) uiState.blurRadius.dp / 10f else 0.dp
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(activeScheme.uri)
                                            .build(),
                                        contentDescription = "当前背景",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (uiState.blurRadius > 0) {
                                                    Modifier.blur(previewBlur)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = uiState.dimAmount / 100f))
                                    )
                                }
                                else -> {}
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color(uiState.activeScheme?.seedColor ?: 0xFF888888.toInt()))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "#${Integer.toHexString(uiState.activeScheme?.seedColor ?: 0).uppercase().padStart(6, '0')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "未设置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    // 右侧：添加按钮组
                    Column(
                        modifier = Modifier.width(110.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.showImagePicker() },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("图片", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.showVideoPicker() },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("视频", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.showGifPicker() },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.GifBox, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GIF", fontSize = 13.sp)
                        }
                        
                        if (uiState.backgroundSchemes.isNotEmpty()) {
                            Text(
                                "${uiState.backgroundSchemes.size}/10 套",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            // ==================== 第2行：效果参数 + 颜色 + 莫奈取色（紧凑网格）====================
            if (uiState.isDynamicThemeEnabled || uiState.backgroundSchemes.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "效果 & 配色",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                
                                Switch(
                                    checked = uiState.isDynamicThemeEnabled,
                                    onCheckedChange = { viewModel.toggleDynamicTheme(it) },
                                    enabled = uiState.backgroundSchemes.isNotEmpty() || !uiState.isDynamicThemeEnabled,
                                    thumbContent = {
                                        if (uiState.isDynamicThemeEnabled) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else null
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // 模糊和暗化滑块（横向排列）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.BlurOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("模糊", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp)
                                        Text(
                                            "${uiState.blurRadius}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = uiState.blurRadius.toFloat(),
                                        onValueChange = { viewModel.updateBlurRadius(it) },
                                        valueRange = 0f..100f,
                                        steps = 20,
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.BrightnessLow, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("暗化", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp)
                                        Text(
                                            "${uiState.dimAmount}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    Slider(
                                        value = uiState.dimAmount.toFloat(),
                                        onValueChange = { viewModel.updateDimAmount(it) },
                                        valueRange = 0f..100f,
                                        steps = 20,
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (settingsViewModel.desktopModeEnabled.collectAsState().value) MaterialTheme.colorScheme.primary
                                          else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "桌面模式（Beta）",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontSize = 13.sp,
                                        color = if (settingsViewModel.desktopModeEnabled.collectAsState().value) MaterialTheme.colorScheme.primary
                                              else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "让壁纸像手机桌面一样清晰可见",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = settingsViewModel.desktopModeEnabled.collectAsState().value,
                                    onCheckedChange = { settingsViewModel.updateDesktopModeEnabled(it) },
                                    modifier = Modifier.height(28.dp),
                                    thumbContent = {
                                        if (settingsViewModel.desktopModeEnabled.collectAsState().value) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else null
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(uiState.activeScheme?.seedColor ?: BackgroundThemeManager.DEFAULT_SEED_COLOR))
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.showColorPicker() }
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("主题色", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp)
                                    Text(
                                        "#${Integer.toHexString(uiState.activeScheme?.seedColor ?: 0).uppercase().padStart(6, '0')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 课程配色开关（原"莫奈取色"）
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        settingsViewModel.updateMonetEnabled(!monetEnabled)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (monetEnabled) MaterialTheme.colorScheme.primary
                                              else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "课程配色",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontSize = 13.sp,
                                            color = if (monetEnabled) MaterialTheme.colorScheme.primary
                                                  else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "开启后，课程卡片颜色将自动适配当前壁纸",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Switch(
                                        checked = monetEnabled,
                                        onCheckedChange = { settingsViewModel.updateMonetEnabled(it) },
                                        modifier = Modifier.height(28.dp),
                                        thumbContent = {
                                            if (monetEnabled) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else null
                                        }
                                    )
                            }
                        }
                        
                            // 莫奈取色展开内容
                            AnimatedVisibility(
                                visible = monetEnabled,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text("饱和度风格", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        SaturationOptionChip(
                                            icon = Icons.Default.FavoriteBorder,
                                            name = "柔和",
                                            selected = courseColorSaturation == 0,
                                            onClick = { settingsViewModel.updateCourseColorSaturation(0) }
                                        )
                                        SaturationOptionChip(
                                            icon = Icons.Default.Tune,
                                            name = "标准",
                                            selected = courseColorSaturation == 1,
                                            onClick = { settingsViewModel.updateCourseColorSaturation(1) }
                                        )
                                        SaturationOptionChip(
                                            icon = Icons.Default.Palette,
                                            name = "鲜艳",
                                            selected = courseColorSaturation == 2,
                                            onClick = { settingsViewModel.updateCourseColorSaturation(2) }
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(32.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        previewColors.forEach { colorHex ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        try { Color(android.graphics.Color.parseColor(colorHex)) }
                                                        catch (e: Exception) { Color.Gray }
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // ==================== 已保存的背景方案（紧凑列表）====================
            if (uiState.backgroundSchemes.isNotEmpty()) {
                item {
                    Text(
                        "已保存的背景 (${uiState.backgroundSchemes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                itemsIndexed(
                    items = uiState.backgroundSchemes,
                    key = { _, it -> it.id }
                ) { index, scheme ->
                    val isActive = index == uiState.activeBackgroundIndex
                    
                    CompactBackgroundItem(
                        scheme = scheme,
                        isActive = isActive,
                        index = index,
                        onSelect = { viewModel.switchToBackground(index) },
                        onDelete = { viewModel.deleteBackground(index) },
                        onRename = { viewModel.showRenameDialog(index) }
                    )
                }
            }
        }
    }
    
    // 背景应用成功提示
    LaunchedEffect(uiState.backgroundAppliedSuccess) {
        if (uiState.backgroundAppliedSuccess) {
            snackbarHostState.showSnackbar(
                message = if (monetEnabled) "✅ 课程颜色已自动更新" 
                          else "💡 开启'莫奈取色'可让课程颜色随背景变化",
                duration = SnackbarDuration.Short
            )
            viewModel.consumeBackgroundAppliedSuccess()
        }
    }
    
    // 删除确认对话框
    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteBackground() },
            title = { Text("删除背景方案") },
            text = { 
                Text("确定要删除「${uiState.backgroundSchemes.getOrNull(uiState.pendingDeleteIndex)?.name ?: "此"}」吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteBackground() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteBackground() }) { Text("取消") }
            }
        )
    }
    
    // 重命名对话框
    if (uiState.showRenameDialog) {
        var name by remember(uiState.pendingRenameIndex, uiState.pendingRenameName) { mutableStateOf(uiState.pendingRenameName) }
        
        AlertDialog(
            onDismissRequest = { viewModel.hideRenameDialog() },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateRenameName(name); viewModel.confirmRename() }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRenameDialog() }) { Text("取消") }
            }
        )
    }
    
    // 颜色选择对话框
    if (uiState.showColorPicker) {
        var selectedColor by remember(uiState.activeScheme?.seedColor) { mutableIntStateOf(uiState.activeScheme?.seedColor ?: BackgroundThemeManager.DEFAULT_SEED_COLOR) }
        
        AlertDialog(
            onDismissRequest = { viewModel.hideColorPicker() },
            title = { Text("选择主题色") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(selectedColor))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("#${Integer.toHexString(selectedColor).uppercase().padStart(6, '0')}", 
                            style = MaterialTheme.typography.bodyLarge, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val colors = listOf(0xFFD4A574, 0xFF6750A4, 0xFF006B5A, 0xFFD93025, 0xFFE879F9,
                        0xFF7DD3FC, 0xFFFACC15, 0xFF4ADE80, 0xFFFB7185, 0xFFA78BFA,
                        0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFF10B981, 0xFF3B82F6,
                        0xFF8B5CF6, 0xFFEC4899, 0xFF6B7280, 0xFF1F2937, 0xFF000000).map { it.toInt() }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.chunked(5).forEach { rowColors ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowColors.forEach { color ->
                                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(color))
                                        .border(if (selectedColor == color) 3.dp else 1.dp,
                                            if (selectedColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            CircleShape)
                                        .clickable { selectedColor = color })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.onSeedColorSelected(selectedColor) }) { Text("确定", color = MaterialTheme.colorScheme.primary) } },
            dismissButton = { TextButton(onClick = { viewModel.hideColorPicker() }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactBackgroundItem(
    scheme: com.wind.ggbond.classtime.ui.theme.BackgroundScheme,
    isActive: Boolean,
    index: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSelect, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (scheme.type) {
                    com.wind.ggbond.classtime.ui.theme.BackgroundType.IMAGE,
                    com.wind.ggbond.classtime.ui.theme.BackgroundType.GIF -> {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(scheme.uri).build(),
                            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    com.wind.ggbond.classtime.ui.theme.BackgroundType.VIDEO -> {
                        Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(scheme.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(scheme.seedColor)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("#${Integer.toHexString(scheme.seedColor).uppercase().padStart(6, '0')}", style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "更多", modifier = Modifier.size(18.dp))
            }
        }
    }
    
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(text = { Text(if (isActive) "当前使用" else "设为当前") }, onClick = { onSelect(); showMenu = false },
            leadingIcon = { Icon(if (isActive) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline, null) })
        DropdownMenuItem(text = { Text("重命名") }, onClick = { onRename(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) })
        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
    }
}

@Composable
private fun SaturationOptionChip(icon: ImageVector, name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(90.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
