package com.wind.ggbond.classtime.ui.screen.scheduleimport

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import kotlinx.coroutines.launch

/**
 * 学校选择页面
 * 优化方案：按省份分组 + 右侧字母索引（参照主流APP设计）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchoolSelectionScreen(
    navController: NavController,
    viewModel: SchoolSelectionViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val schools by viewModel.schools.collectAsState()
    val schoolGroups by viewModel.schoolGroups.collectAsState()
    val provinceIndex by viewModel.provinceIndex.collectAsState()
    val recentSchools by viewModel.recentSchools.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 判断是否在搜索模式
    val isSearchMode = searchQuery.isNotEmpty()
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("选择学校") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    placeholder = { Text("搜索学校名称或省份...") },
                    leadingIcon = { Icon(Icons.Default.Search, "搜索") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Clear, "清空")
                            }
                        }
                    },
                    singleLine = true
                )
                
                // 最近使用学校（仅在非搜索模式且存在时显示）
                if (!isSearchMode && recentSchools.isNotEmpty()) {
                    RecentSchoolsSection(
                        schools = recentSchools,
                        onSchoolClick = { school ->
                            viewModel.selectSchool(school)
                            navController.navigate("smart_webview_import/${school.id}")
                        }
                    )
                }
                
                // 省份快速筛选器（仅在非搜索模式且有多组时显示）
                if (!isSearchMode && schoolGroups.size > 1) {
                    ProvinceFilterBar(
                        provinces = schoolGroups.map { it.province },
                        onProvinceClick = { province ->
                            coroutineScope.launch {
                                val index = schoolGroups.indexOfFirst { it.province == province }
                                if (index >= 0) {
                                    var itemIndex = 0
                                    for (i in 0 until index) {
                                        itemIndex += 1 + schoolGroups[i].schools.size
                                    }
                                    listState.animateScrollToItem(itemIndex)
                                }
                            }
                        }
                    )
                }
                
                // 结果列表
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    
                    isSearchMode && schools.isEmpty() -> {
                        // 搜索无结果
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = "未找到匹配的学校",
                            subtitle = "请尝试其他关键词"
                        )
                    }
                    
                    !isSearchMode && schoolGroups.isEmpty() && !isLoading -> {
                        // 无数据（且不在加载中）
                        EmptyState(
                            icon = Icons.Default.School,
                            title = "暂无学校数据",
                            subtitle = "请稍后再试"
                        )
                    }
                    
                    isSearchMode -> {
                        // 搜索模式：扁平列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(schools) { school ->
                                SchoolItem(
                                    school = school,
                                    onClick = {
                                        viewModel.selectSchool(school)
                                        navController.navigate("smart_webview_import/${school.id}")
                                    }
                                )
                            }
                        }
                    }
                    
                    else -> {
                        // 分组模式：按省份分组 + StickyHeader
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // 为每个省份组创建section
                            schoolGroups.forEachIndexed { groupIndex, group ->
                                // 省份标题（Sticky Header）
                                stickyHeader(key = "header_${group.province}") {
                                    ProvinceHeader(group.province, group.schoolCount)
                                }
                                
                                // 该省份下的学校列表
                                items(
                                    count = group.schools.size,
                                    key = { index -> group.schools[index].id }
                                ) { index ->
                                    val school = group.schools[index]
                                    SchoolItem(
                                        school = school,
                                        onClick = {
                                            viewModel.selectSchool(school)
                                            navController.navigate("smart_webview_import/${school.id}")
                                        }
                                    )
                                    // 如果不是该组最后一个，添加间距
                                    if (index < group.schools.size - 1 || groupIndex < schoolGroups.size - 1) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 右侧省份索引（仅在分组模式且有多组时显示）
            if (!isSearchMode && provinceIndex.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)  // 对齐到右侧
                ) {
                    ProvinceIndexBar(
                        provinces = provinceIndex,
                        onProvinceClick = { province ->
                            coroutineScope.launch {
                            // 滚动到对应省份
                            val index = schoolGroups.indexOfFirst { it.province == province }
                            if (index >= 0) {
                                // 计算该省份在列表中的位置
                                // 每个group = 1个header + N个schools
                                var itemIndex = 0
                                for (i in 0 until index) {
                                    itemIndex += 1 + schoolGroups[i].schools.size // header + schools
                                }
                                // 滚动到该组的header位置
                                listState.animateScrollToItem(itemIndex)
                            }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 省份标题（Sticky Header）- 显示省份和学校数量
 */
@Composable
fun ProvinceHeader(province: String, schoolCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = "位置",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = province,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($schoolCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 最近使用学校区域
 */
@Composable
fun RecentSchoolsSection(
    schools: List<SchoolEntity>,
    onSchoolClick: (SchoolEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "最近使用",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            schools.forEach { school ->
                RecentSchoolChip(
                    school = school,
                    onClick = { onSchoolClick(school) }
                )
            }
        }
    }
}

/**
 * 最近使用学校芯片
 */
@Composable
fun RecentSchoolChip(
    school: SchoolEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = school.shortName.ifEmpty { school.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    }
}

/**
 * 省份快速筛选器（横向滚动标签栏）
 */
@Composable
fun ProvinceFilterBar(
    provinces: List<String>,
    onProvinceClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        provinces.forEach { province ->
            FilterChip(
                selected = false,
                onClick = { onProvinceClick(province) },
                label = { Text(province, style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

/**
 * 右侧省份索引条 - 仿微信通讯录样式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvinceIndexBar(
    provinces: List<String>,
    onProvinceClick: (String) -> Unit
) {
    // 当前选中的索引字母
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    // 获取震动反馈
    val hapticFeedback = LocalHapticFeedback.current
    
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(end = 4.dp),  // 更靠右，减小右侧边距
        contentAlignment = Alignment.CenterEnd  // 对齐到右侧
    ) {
        // 索引字母列表（带圆角半透明背景，像微信）
        Column(
            modifier = Modifier
                .fillMaxHeight(0.7f)  // 占屏幕高度的70%
                .width(20.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),  // 半透明背景
                    shape = RoundedCornerShape(10.dp)  // 圆角
                )
                .padding(vertical = 4.dp)  // 内边距
                .pointerInput(provinces) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // 计算触摸位置对应的字母
                            val index = (offset.y / (size.height / provinces.size)).toInt()
                                .coerceIn(0, provinces.size - 1)
                            val province = provinces[index]
                            selectedLetter = province.take(1)
                            onProvinceClick(province)
                            // 触发震动反馈
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            // 计算当前拖动位置对应的字母
                            val index = (change.position.y / (size.height / provinces.size)).toInt()
                                .coerceIn(0, provinces.size - 1)
                            val province = provinces[index]
                            val letter = province.take(1)
                            if (selectedLetter != letter) {
                                selectedLetter = letter
                                onProvinceClick(province)
                                // 切换字母时触发震动
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            // 延迟清除选中状态
                            kotlinx.coroutines.GlobalScope.launch {
                                kotlinx.coroutines.delay(200)
                                selectedLetter = null
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            provinces.forEach { province ->
                val letter = province.take(1)
                val isSelected = selectedLetter == letter
                
                Text(
                    text = letter,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { 
                            selectedLetter = letter
                            onProvinceClick(province)
                            // 点击时触发震动
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    fontSize = 10.sp,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // 放大提示框（滑动时显示）
        if (selectedLetter != null) {
            Box(
                modifier = Modifier
                    .offset(x = (-60).dp)
                    .size(50.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedLetter!!,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * 空状态组件
 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SchoolItem(
    school: SchoolEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 学校图标（更紧凑）
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = "学校图标",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 学校信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = school.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "位置",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = school.province,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = getSystemTypeName(school.systemType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (school.tips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = school.tips,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "选择学校",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getSystemTypeName(type: String): String {
    return when (type) {
        "zfsoft" -> "正方系统"
        "qinguo" -> "青果系统"
        "qiangzhi" -> "强智系统"
        "urp" -> "URP系统"
        "shuwei" -> "树维系统"
        "kingosoft" -> "青果系统"
        "custom" -> "自研系统"
        "chengfang" -> "乘方系统"
        "jinzhi" -> "金智系统"
        else -> "其他系统"
    }
}
