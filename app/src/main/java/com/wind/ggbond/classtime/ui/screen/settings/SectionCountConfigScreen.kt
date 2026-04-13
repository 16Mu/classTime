package com.wind.ggbond.classtime.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionCountConfigScreen(
    navController: NavController,
    viewModel: ClassTimeConfigViewModel = hiltViewModel()
) {
    val morningSectionCount by viewModel.morningSections.collectAsState()
    val afternoonSectionCount by viewModel.afternoonSections.collectAsState()
    val showMorningSectionsDialog by viewModel.showMorningSectionsDialog.collectAsState()
    val showAfternoonSectionsDialog by viewModel.showAfternoonSectionsDialog.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val maxCourseSection by viewModel.maxCourseSection.collectAsState()
    val sectionWarning by viewModel.sectionWarning.collectAsState()
    
    val totalSections = morningSectionCount + afternoonSectionCount
    val recommendedMode = totalSections <= 12
    
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("课程数设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
            // 说明卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "设置说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• 上午节次：设置上午课程的节次数（如4节）\n• 下午节次：设置下午课程的节次数（如4节）\n• 设置为0表示该时段没有课程\n• 设置后系统会自动生成对应的课程时间表",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // 节次不足警告卡片
            if (sectionWarning != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "节次设置不足",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = sectionWarning!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
            
            // 节次数设置卡片
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
                        // 上午节次数
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.showMorningSectionsDialog() }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "上午节次",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "上午课程节次数",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${morningSectionCount}节",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // 下午节次数
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.showAfternoonSectionsDialog() }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "下午节次",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "下午课程节次数",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${afternoonSectionCount}节",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
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
                }
            }
            
            // 总计提示
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
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
                                text = "每天总节次数",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (maxCourseSection > 0) {
                                Text(
                                    text = "课程最大节次：第${maxCourseSection}节",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (totalSections < maxCourseSection) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Text(
                            text = "${morningSectionCount + afternoonSectionCount}节",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (maxCourseSection > 0 && totalSections < maxCourseSection) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
            
            // 显示模式设置卡片
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "课程表显示模式",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "选择课程表的显示方式，影响节次高度和滚动行为",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = displayMode == ClassTimeConfigViewModel.DISPLAY_MODE_ADAPTIVE,
                                onClick = { viewModel.updateDisplayMode(ClassTimeConfigViewModel.DISPLAY_MODE_ADAPTIVE) },
                                label = { Text("自适应一屏") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = displayMode == ClassTimeConfigViewModel.DISPLAY_MODE_FIXED_SCROLL,
                                onClick = { viewModel.updateDisplayMode(ClassTimeConfigViewModel.DISPLAY_MODE_FIXED_SCROLL) },
                                label = { Text("固定高度滚动") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Text(
                            text = if (displayMode == ClassTimeConfigViewModel.DISPLAY_MODE_ADAPTIVE) {
                                "所有节次均分屏幕高度，在一页内完整显示"
                            } else {
                                "格子高度固定，超出屏幕时可以滚动查看"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (displayMode != recommendedMode) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
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
                                            text = "💡 智能推荐",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = if (recommendedMode) "当前${totalSections}节，推荐自适应一屏" else "当前${totalSections}节，推荐固定高度滚动",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.updateDisplayMode(recommendedMode) }
                                    ) {
                                        Text("应用推荐")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 上午节次数编辑对话框
    if (showMorningSectionsDialog) {
        SectionCountDialog(
            title = "设置上午节次数",
            currentSections = morningSectionCount,
            minSections = maxOf(0, maxCourseSection - afternoonSectionCount),
            maxCourseSection = maxCourseSection,
            otherSections = afternoonSectionCount,
            onDismiss = { viewModel.hideMorningSectionsDialog() },
            onConfirm = { sections ->
                viewModel.updateMorningSections(sections)
                viewModel.hideMorningSectionsDialog()
            }
        )
    }
    
    // 下午节次数编辑对话框
    if (showAfternoonSectionsDialog) {
        SectionCountDialog(
            title = "设置下午节次数",
            currentSections = afternoonSectionCount,
            minSections = maxOf(0, maxCourseSection - morningSectionCount),
            maxCourseSection = maxCourseSection,
            otherSections = morningSectionCount,
            onDismiss = { viewModel.hideAfternoonSectionsDialog() },
            onConfirm = { sections ->
                viewModel.updateAfternoonSections(sections)
                viewModel.hideAfternoonSectionsDialog()
            }
        )
    }
}
