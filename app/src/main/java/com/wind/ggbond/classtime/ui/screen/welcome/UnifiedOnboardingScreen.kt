package com.wind.ggbond.classtime.ui.screen.welcome

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 全屏沉浸式引导页 - 3页精简设计
 * 在 MainActivity 顶层渲染，Scaffold/底部Tab 之前拦截，防止用户绕过
 *
 * 第1页：欢迎（品牌展示 + 核心标签）
 * 第2页：核心亮点（导入/更新/调课 三大特性）
 * 第3页：开始使用（操作步骤 + 免责声明CheckBox内联）
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UnifiedOnboardingScreen(
    onComplete: () -> Unit,
    onAcceptDisclaimer: () -> Unit,
    disclaimerAccepted: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 主题色
    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    // 免责声明确认状态（已接受则默认勾选）
    var disclaimerChecked by remember { mutableStateOf(disclaimerAccepted) }

    // 固定3页
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // 全屏沉浸式布局
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ==================== 顶部进度条 ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    // 宽度弹性动画
                    val width by animateDpAsState(
                        targetValue = if (index == pagerState.currentPage) 40.dp else 16.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "indicatorWidth"
                    )
                    // 透明度动画（已经过的页面保持高亮）
                    val alpha by animateFloatAsState(
                        targetValue = if (index <= pagerState.currentPage) 1f else 0.3f,
                        animationSpec = tween(300),
                        label = "indicatorAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(width)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primaryColor.copy(alpha = alpha))
                    )
                    if (index < pageCount - 1) {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            // ==================== 页面内容 ====================
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> WelcomePage(primaryColor, textColor, surfaceColor)
                    1 -> HighlightsPage(primaryColor, textColor, surfaceColor)
                    2 -> GetStartedPage(
                        primaryColor = primaryColor,
                        textColor = textColor,
                        surfaceColor = surfaceColor,
                        disclaimerChecked = disclaimerChecked,
                        onDisclaimerCheckedChange = { checked ->
                            disclaimerChecked = checked
                            if (checked) onAcceptDisclaimer()
                        },
                        context = context
                    )
                }
            }

            // ==================== 底部按钮 ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 跳过（最后一页不显示）
                if (pagerState.currentPage < pageCount - 1) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pageCount - 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("跳过", fontSize = 16.sp)
                    }
                }

                // 继续 / 开始使用
                val isLastPage = pagerState.currentPage == pageCount - 1
                val canProceed = !isLastPage || disclaimerChecked

                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (!isLastPage) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                onComplete()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canProceed,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        text = if (isLastPage) "开始使用" else "继续",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isLastPage) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward, null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// 旧的OnboardingPageContent/InfoCard/DisclaimerPageContent/CardInfo已废弃
// 新的页面组件定义在 OnboardingPages.kt 中：WelcomePage/HighlightsPage/GetStartedPage












