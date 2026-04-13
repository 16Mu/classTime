package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel
import com.wind.ggbond.classtime.ui.theme.LocalDesktopModeEnabled
import com.wind.ggbond.classtime.ui.theme.LocalWallpaperEnabled
import androidx.compose.ui.draw.blur

/**
 * 操作面板组件
 * 
 * 职责：
 * - 顶部应用栏（TopAppBar）：课表名称、周次选择器、视图模式切换、紧凑模式切换
 * - 浮动操作按钮（FAB）：快速添加课程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPanelProvider(
    currentSchedule: Schedule?,
    currentWeekNumber: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    viewMode: Int,
    compactModeEnabled: Boolean,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    onScheduleQuickEditRequest: () -> Unit,
    onWeekPickerRequest: () -> Unit,
    onViewModeToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
    isWallpaperEnabled: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val glassEffectEnabled by settingsViewModel.glassEffectEnabled.collectAsState()
    val isGlassEffectActive = isWallpaperEnabled && glassEffectEnabled
    val isDesktopModeEnabled = LocalDesktopModeEnabled.current
    val wallpaperActive = LocalWallpaperEnabled.current

    val topBarContainerColor = when {
        isDesktopModeEnabled && wallpaperActive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
        isGlassEffectActive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        wallpaperActive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface
    }

    Scaffold(
        modifier = modifier,
        containerColor = when {
            isDesktopModeEnabled && wallpaperActive -> Color.Transparent
            isGlassEffectActive -> Color.Transparent
            wallpaperActive -> Color.Transparent
            else -> MaterialTheme.colorScheme.background
        },
        floatingActionButton = {
            val fabInteractionSource = remember { MutableInteractionSource() }
            val fabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (fabPressed) 0.95f else 1f,
                animationSpec = tween(durationMillis = 100),
                label = "fabScale"
            )
            
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navController.navigate(
                        Screen.CourseEdit.createRoute(
                            weekNumber = pagerState.currentPage + 1
                        )
                    )
                },
                interactionSource = fabInteractionSource,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .background(
                        color = when {
                            isDesktopModeEnabled && isWallpaperEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            glassEffectEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .then(
                        if (glassEffectEnabled || (isDesktopModeEnabled && isWallpaperEnabled)) {
                            Modifier.border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Modifier
                        }
                    ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加课程", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加课程", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarContainerColor
                    ),
                    title = {
                        Column(
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScheduleQuickEditRequest()
                                }
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentSchedule?.name ?: "未设置课表",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑课表",
                                    modifier = Modifier
                                        .size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AssistChip(
                                onClick = onWeekPickerRequest,
                                label = {
                                    Text(
                                        text = "第 ${pagerState.currentPage + 1} 周",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.UnfoldMore,
                                        contentDescription = "选择周次",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    },
                    actions = {
                        val scale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "button_scale"
                        )
                        
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                settingsViewModel.updateCompactModeEnabled(!compactModeEnabled)
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (compactModeEnabled) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            AnimatedContent(
                                targetState = compactModeEnabled,
                                transitionSpec = {
                                    scaleIn(
                                        initialScale = 0.6f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        )
                                    ) + fadeIn() togetherWith scaleOut(
                                        targetScale = 0.6f,
                                        animationSpec = tween(150)
                                    ) + fadeOut()
                                },
                                label = "compact_icon_transition"
                            ) { isCompact ->
                                Icon(
                                    imageVector = if (isCompact) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (isCompact) "关闭紧凑模式" else "开启紧凑模式"
                                )
                            }
                        }
                        
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onViewModeToggle()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = {
                                    scaleIn(
                                        initialScale = 0.6f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        )
                                    ) + fadeIn() togetherWith scaleOut(
                                        targetScale = 0.6f,
                                        animationSpec = tween(150)
                                    ) + fadeOut()
                                },
                                label = "icon_transition"
                            ) { mode ->
                                Icon(
                                    imageVector = if (mode == 0) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (mode == 0) "列表" else "网格"
                                )
                            }
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}
