package com.wind.ggbond.classtime.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.wind.ggbond.classtime.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import kotlinx.coroutines.launch

/**
 * 自定义背景设置页面（增强版）
 * 
 * 新增功能：
 * 1. ✅ 多套背景方案管理（最多10套，支持切换、删除、重命名）
 * 2. ✅ 背景模糊程度调节 (0-100)
 * 3. ✅ 背景暗化程度调节 (0-100)
 * 4. ✅ 支持图片/视频/GIF 三种类型
 * 5. ✅ 实时预览效果
 * 6. ✅ 一键恢复默认主题
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BackgroundSettingsScreen(
    navController: NavController,
    viewModel: BackgroundSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }
    
    // 视频选择器
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onVideoSelected(it) }
    }
    
    // GIF 选择器
    val gifPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onGifSelected(it) }
    }
    
    // 监听选择器状态
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== 当前背景预览 ====================
            item {
                ActiveBackgroundPreviewCard(
                    scheme = uiState.activeScheme,
                    blurRadius = uiState.blurRadius,
                    dimAmount = uiState.dimAmount,
                    isDynamicThemeEnabled = uiState.isDynamicThemeEnabled
                )
            }
            
            // ==================== 添加新背景按钮组 ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "添加背景",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 添加图片按钮
                            OutlinedButton(
                                onClick = { viewModel.showImagePicker() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, stringResource(R.string.desc_photo_library), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("图片")
                            }
                            
                            // 添加视频按钮
                            OutlinedButton(
                                onClick = { viewModel.showVideoPicker() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VideoLibrary, stringResource(R.string.desc_video_library), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("视频")
                            }
                            
                            // 添加 GIF 按钮
                            OutlinedButton(
                                onClick = { viewModel.showGifPicker() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.GifBox, stringResource(R.string.desc_gif), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GIF")
                            }
                        }
                        
                        if (uiState.backgroundSchemes.isNotEmpty()) {
                            Text(
                                text = "已添加 ${uiState.backgroundSchemes.size}/${DataStoreManager.SettingsKeys.MAX_BACKGROUNDS_COUNT} 套背景",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            
            // ==================== 效果参数调节 ====================
            if (uiState.isDynamicThemeEnabled || uiState.backgroundSchemes.isNotEmpty()) {
                item {
                    EffectParametersCard(
                        blurRadius = uiState.blurRadius.toFloat(),
                        dimAmount = uiState.dimAmount.toFloat(),
                        onBlurRadiusChange = { viewModel.updateBlurRadius(it) },
                        onDimAmountChange = { viewModel.updateDimAmount(it) }
                    )
                }
            }
            
            // ==================== 多背景方案列表 ====================
            if (uiState.backgroundSchemes.isNotEmpty()) {
                item {
                    Text(
                        text = "已保存的背景方案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                items(
                    items = uiState.backgroundSchemes,
                    key = { it.id }
                ) { scheme ->
                    val index = uiState.backgroundSchemes.indexOf(scheme)
                    val isActive = index == uiState.activeBackgroundIndex
                    
                    BackgroundSchemeItem(
                        scheme = scheme,
                        isActive = isActive,
                        index = index,
                        onSelect = { viewModel.switchToBackground(index) },
                        onDelete = { viewModel.deleteBackground(index) },
                        onRename = { viewModel.showRenameDialog(index) }
                    )
                }
            }
            
            // ==================== 动态主题开关 ====================
            item {
                DynamicThemeSwitchCard(
                    isEnabled = uiState.isDynamicThemeEnabled,
                    hasBackgrounds = uiState.backgroundSchemes.isNotEmpty(),
                    onToggle = { viewModel.toggleDynamicTheme(it) }
                )
            }
            
            // ==================== 种子颜色选择 ====================
            if (uiState.isDynamicThemeEnabled) {
                item {
                    SeedColorCard(
                        seedColor = uiState.activeScheme?.seedColor ?: BackgroundThemeManager.DEFAULT_SEED_COLOR,
                        onPickColor = { viewModel.showColorPicker() }
                    )
                }
                
                item {
                    PresetColorsGrid(
                        currentColor = uiState.activeScheme?.seedColor ?: BackgroundThemeManager.DEFAULT_SEED_COLOR,
                        onColorSelected = { viewModel.onSeedColorSelected(it) }
                    )
                }
            }
            
            // ==================== 功能说明 ====================
            item {
                FeatureDescriptionCard()
            }
        }
    }
    
    // 背景应用成功提示（莫奈取色）
    LaunchedEffect(uiState.backgroundAppliedSuccess) {
        if (uiState.backgroundAppliedSuccess) {
            val monetEnabled = settingsViewModel.monetCourseColorsEnabled.value
            val message = if (monetEnabled) {
                "课程颜色已自动更新"
            } else {
                "开启'莫奈课程取色'让课程颜色随之变化"
            }
            
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                actionLabel = if (!monetEnabled) "前往设置" else null
            ).let { result ->
                if (result == SnackbarResult.ActionPerformed && !monetEnabled) {
                    navController.navigate(Screen.Settings.route)
                }
            }
            
            viewModel.consumeBackgroundAppliedSuccess()
        }
    }
    
    // ==================== 对话框 ====================
    
    // 删除确认对话框
    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteBackground() },
            title = { Text("删除背景方案") },
            text = { 
                val name = uiState.backgroundSchemes.getOrNull(uiState.pendingDeleteIndex)?.name ?: "此"
                Text("确定要删除「$name」背景方案吗？此操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteBackground() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteBackground() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 重命名对话框
    if (uiState.showRenameDialog) {
        var name by remember { mutableStateOf(uiState.pendingRenameName) }
        
        AlertDialog(
            onDismissRequest = { viewModel.hideRenameDialog() },
            title = { Text("重命名背景方案") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("方案名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRenameName(name)
                    viewModel.confirmRename()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRenameDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 颜色选择对话框
    if (uiState.showColorPicker) {
        ColorPickerDialog(
            currentColor = uiState.activeScheme?.seedColor ?: BackgroundThemeManager.DEFAULT_SEED_COLOR,
            onColorSelected = { viewModel.onSeedColorSelected(it) },
            onDismiss = { viewModel.hideColorPicker() }
        )
    }
}

// ==================== 子组件 ====================

/**
 * 当前激活的背景预览卡片
 */
@Composable
private fun ActiveBackgroundPreviewCard(
    scheme: com.wind.ggbond.classtime.ui.theme.BackgroundScheme?,
    blurRadius: Int,
    dimAmount: Int,
    isDynamicThemeEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前背景预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (scheme != null) {
                    // 显示背景内容
                    when (scheme.type) {
                        com.wind.ggbond.classtime.ui.theme.BackgroundType.IMAGE -> {
                            var imageLoadFailed by remember { mutableStateOf(false) }

                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(scheme.uri)
                                    .build(),
                                contentDescription = stringResource(R.string.desc_photo_library),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onError = { imageLoadFailed = true },
                                onSuccess = { imageLoadFailed = false }
                            )

                            if (imageLoadFailed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.ImageNotSupported,
                                            contentDescription = "加载失败",
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "图片加载失败",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            // 应用模糊和暗化效果
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (blurRadius > 0) {
                                            Modifier.blur(blurRadius.dp / 10f) // 缩放模糊值
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .background(Color.Black.copy(alpha = dimAmount / 100f))
                            )
                            
                            // 种子颜色指示器
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(scheme.seedColor))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "#${Integer.toHexString(scheme.seedColor).uppercase().padStart(6, '0')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${scheme.type.value.uppercase()} • ${scheme.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        com.wind.ggbond.classtime.ui.theme.BackgroundType.VIDEO -> {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = stringResource(R.string.desc_video_file),
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "视频背景：${scheme.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        com.wind.ggbond.classtime.ui.theme.BackgroundType.GIF -> {
                            var gifLoadFailed by remember { mutableStateOf(false) }

                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(scheme.uri)
                                    .build(),
                                contentDescription = stringResource(R.string.desc_gif),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onError = { gifLoadFailed = true },
                                onSuccess = { gifLoadFailed = false }
                            )

                            if (gifLoadFailed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.ImageNotSupported,
                                            contentDescription = "加载失败",
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "GIF加载失败",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = dimAmount / 100f))
                            )
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GifBox,
                                        contentDescription = stringResource(R.string.desc_gif),
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "GIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    
                    // 当前激活标记
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.desc_check),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDynamicThemeEnabled) "当前使用" else "未启用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    // 占位符
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = false) {}
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = stringResource(R.string.desc_wallpaper),
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "未设置自定义背景",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击上方按钮添加背景",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 效果参数调节卡片（模糊 + 暗化）
 */
@Composable
private fun EffectParametersCard(
    blurRadius: Float,
    dimAmount: Float,
    onBlurRadiusChange: (Float) -> Unit,
    onDimAmountChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "效果调节",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 模糊程度滑块
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BlurOn,
                            contentDescription = stringResource(R.string.desc_blur_on),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "模糊程度", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "${blurRadius.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Slider(
                    value = blurRadius,
                    onValueChange = onBlurRadiusChange,
                    valueRange = 0f..100f,
                    steps = 20,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "数值越大，背景越模糊（建议 0-30）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 暗化程度滑块
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BrightnessLow,
                            contentDescription = stringResource(R.string.desc_expand),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "暗化程度", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "${dimAmount.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Slider(
                    value = dimAmount,
                    onValueChange = onDimAmountChange,
                    valueRange = 0f..100f,
                    steps = 20,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "提高暗化可增强文字可读性（建议 30-50）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 背景方案列表项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundSchemeItem(
    scheme: com.wind.ggbond.classtime.ui.theme.BackgroundScheme,
    isActive: Boolean,
    index: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSelect() },
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (scheme.type) {
                    com.wind.ggbond.classtime.ui.theme.BackgroundType.IMAGE,
                    com.wind.ggbond.classtime.ui.theme.BackgroundType.GIF -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(scheme.uri)
                                .build(),
                            contentDescription = stringResource(R.string.desc_photo_library),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    com.wind.ggbond.classtime.ui.theme.BackgroundType.VIDEO -> {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = stringResource(R.string.desc_video_file),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 类型标签
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                ) {
                    Text(
                        text = scheme.type.value.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息列
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scheme.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(scheme.seedColor))
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "#${Integer.toHexString(scheme.seedColor).uppercase().padStart(6, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (scheme.blurRadius > 0 || scheme.dimAmount > 0) {
                        Text(
                            text = buildString {
                                if (scheme.blurRadius > 0) append("模糊:${scheme.blurRadius}% ")
                                if (scheme.dimAmount > 0) append("暗化:${scheme.dimAmount}%")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // 操作菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作"
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isActive) "当前使用" else "设为当前") },
                        onClick = {
                            onSelect()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                if (isActive) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                contentDescription = stringResource(R.string.desc_check)
                            )
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            onRename()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.desc_edit))
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { 
                            Text(
                                "删除",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.desc_delete_outline),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 动态主题开关卡片
 */
@Composable
private fun DynamicThemeSwitchCard(
    isEnabled: Boolean,
    hasBackgrounds: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Material You 动态主题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = when {
                        !hasBackgrounds -> "添加背景后自动启用"
                        isEnabled -> "已启用 - 根据背景生成配色"
                        else -> "已关闭 - 使用默认主题"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                enabled = hasBackgrounds || !isEnabled
            )
        }
    }
}

/**
 * 种子颜色卡片
 */
@Composable
private fun SeedColorCard(
    seedColor: Int,
    onPickColor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "种子颜色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "用于生成完整的 Material 3 配色方案的基础色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(seedColor))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "当前种子颜色",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "#${Integer.toHexString(seedColor).uppercase().padStart(6, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                OutlinedButton(
                    onClick = onPickColor,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("手动选择", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 预设颜色网格
 */
@Composable
private fun PresetColorsGrid(
    currentColor: Int,
    onColorSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "推荐配色",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val presetColors = listOf(
                0xFFD4A574.toInt(), 0xFF6750A4.toInt(), 0xFF006B5A.toInt(), 0xFFD93025.toInt(),
                0xFFE879F9.toInt(), 0xFF7DD3FC.toInt(), 0xFFFACC15.toInt(), 0xFF4ADE80.toInt(),
                0xFFFB7185.toInt(), 0xFFA78BFA.toInt(), 0xFFEF4444.toInt(), 0xFFF97316.toInt(),
                0xFFF59E0B.toInt(), 0xFF10B981.toInt(), 0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(),
                0xFFEC4899.toInt(), 0xFF6B7280.toInt(), 0xFF1F2937.toInt(), 0xFF000000.toInt()
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presetColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (currentColor == color) 3.dp else 1.dp,
                                color = if (currentColor == color)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

/**
 * 功能说明卡片
 */
@Composable
private fun FeatureDescriptionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "✨ 高级功能说明",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = """• **多套背景**：最多保存 10 套背景方案，随时快速切换
• **模糊效果**：调节背景模糊程度（0-100），打造毛玻璃效果
• **暗化调节**：调整背景透明度（0-100），提升文字可读性
• **动态背景**：支持静态图片、视频、GIF 动画三种类型
• **智能取色**：自动提取主色调，一键生成 Material You 配色
• **实时预览**：所见即所得，调整后立即生效""".trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5
            )
        }
    }
}

/**
 * 简化的颜色选择对话框
 */
@Composable
private fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableIntStateOf(currentColor) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择种子颜色") },
        text = {
            Column {
                Text(
                    text = "选择一个颜色作为生成配色方案的基准",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(selectedColor))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "#${Integer.toHexString(selectedColor).uppercase().padStart(6, '0')}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val colors = listOf(
                    0xFFD4A574, 0xFF6750A4, 0xFF006B5A, 0xFFD93025, 0xFFE879F9,
                    0xFF7DD3FC, 0xFFFACC15, 0xFF4ADE80, 0xFFFB7185, 0xFFA78BFA,
                    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFF10B981, 0xFF3B82F6,
                    0xFF8B5CF6, 0xFFEC4899, 0xFF6B7280, 0xFF1F2937, 0xFF000000
                ).map { it.toInt() }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colors) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (selectedColor == color) 3.dp else 1.dp,
                                    color = if (selectedColor == color)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor) }) {
                Text("确定", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
