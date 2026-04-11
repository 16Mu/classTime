package com.wind.ggbond.classtime.ui.screen.coursecolor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeColorSelectionScreen(
    navController: NavController,
    viewModel: ThemeColorSelectionViewModel = hiltViewModel()
) {
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsState()
    val previewColors by viewModel.previewColors.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val applyResult by viewModel.applyResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(applyResult) {
        applyResult?.let {
            if (it.startsWith("已更新")) {
                snackbarHostState.showSnackbar(it)
                viewModel.clearApplyResult()
                navController.navigateUp()
            } else {
                snackbarHostState.showSnackbar(it)
                viewModel.clearApplyResult()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("主题色调选择") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = { viewModel.applyThemeColors() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isApplying && previewColors.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(selectedThemeColor)
                    )
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确认应用", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "个性化你的空间",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 36.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "选择一个符合您学习氛围的色彩系统，让日程安排更显直观。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }

                item {
                    PresetThemeSection(
                        selectedColor = selectedThemeColor,
                        onSelect = { viewModel.selectThemeColor(it) }
                    )
                }

                item {
                    HsvColorPickerSection(
                        selectedColor = selectedThemeColor,
                        onSelect = { viewModel.selectThemeColor(it) }
                    )
                }
            }

            if (previewColors.isNotEmpty()) {
                FloatingPreviewCard(
                    colors = previewColors,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

private val presetThemes = listOf(
    "经典蓝" to 0xFF0058BC.toInt(),
    "翡翠绿" to 0xFF10B981.toInt(),
    "极光紫" to 0xFF8B5CF6.toInt(),
    "琥珀金" to 0xFFF59E0B.toInt()
)

@Composable
private fun PresetThemeSection(
    selectedColor: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "预设色系",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "SELECT ONE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presetThemes.forEach { (name, color) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSelect(color) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .then(
                                    if (selectedColor == color) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.primaryContainer,
                                            CircleShape
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.surface,
                                            CircleShape
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedColor == color) FontWeight.Medium else FontWeight.Normal,
                            color = if (selectedColor == color) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onSelect(selectedColor) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Red, Color.Yellow, Color.Green,
                                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                    )
                                )
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "自定义",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "自定义",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun HsvColorPickerSection(
    selectedColor: Int,
    onSelect: (Int) -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var isUserInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(selectedColor) {
        if (!isUserInteracting) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(selectedColor, hsv)
            if (hsv[1] > 0.01f) {
                hue = hsv[0]
            }
            saturation = hsv[1]
            value = hsv[2]
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自定义颜色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "HSV PICKER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSVChange = { s, v ->
                        saturation = s
                        value = v
                        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v))
                        onSelect(color)
                    },
                    onInteractionStart = { isUserInteracting = true },
                    onInteractionEnd = { isUserInteracting = false }
                )

                HueSlider(
                    hue = hue,
                    onHueChange = { newHue ->
                        hue = newHue
                        val color = android.graphics.Color.HSVToColor(floatArrayOf(newHue, saturation, value))
                        onSelect(color)
                    },
                    onInteractionStart = { isUserInteracting = true },
                    onInteractionEnd = { isUserInteracting = false }
                )

                HexInputWithPreview(
                    selectedColor = selectedColor,
                    onColorChange = { color ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(color, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                        onSelect(color)
                    }
                )
            }
        }
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSVChange: (Float, Float) -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {}
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
    ) {
        val width = maxWidth
        val height = maxHeight

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        onInteractionStart()
                        onSVChange(s, v)
                        onInteractionEnd()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onInteractionStart() },
                        onDragEnd = { onInteractionEnd() },
                        onDragCancel = { onInteractionEnd() }
                    ) { change, _ ->
                        change.consume()
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onSVChange(s, v)
                    }
                }
        ) {
            drawRect(color = hueColor)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )

            val indicatorX = saturation * size.width
            val indicatorY = (1f - value) * size.height
            val indicatorRadius = 10.dp.toPx()

            drawCircle(
                color = Color.White,
                radius = indicatorRadius,
                center = Offset(indicatorX, indicatorY),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HUE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                fontSize = 10.sp
            )
            Text(
                text = "${hue.roundToInt()}°",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val sliderWidth = maxWidth

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val h = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                            onInteractionStart()
                            onHueChange(h)
                            onInteractionEnd()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onInteractionStart() },
                            onDragEnd = { onInteractionEnd() },
                            onDragCancel = { onInteractionEnd() }
                        ) { change, _ ->
                            change.consume()
                            val h = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                            onHueChange(h)
                        }
                    }
            ) {
                val hueColors = listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(colors = hueColors),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                )

                val thumbX = (hue / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(thumbX, size.height / 2)
                )
                drawCircle(
                    color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                    radius = 7.dp.toPx(),
                    center = Offset(thumbX, size.height / 2)
                )
            }
        }
    }
}

@Composable
private fun HexInputWithPreview(
    selectedColor: Int,
    onColorChange: (Int) -> Unit
) {
    var hexText by remember(selectedColor) {
        mutableStateOf(
            Integer.toHexString(selectedColor).uppercase().padStart(6, '0').takeLast(6)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = hexText,
            onValueChange = { input ->
                val filtered = input.filter { it.isLetterOrDigit() }.take(6)
                hexText = filtered.uppercase()
                if (filtered.length == 6) {
                    try {
                        val color = android.graphics.Color.parseColor("#$filtered")
                        onColorChange(color)
                    } catch (_: Exception) { }
                }
            },
            modifier = Modifier.weight(1f),
            prefix = {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(selectedColor))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun FloatingPreviewCard(
    colors: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .padding(top = 8.dp, end = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "预览",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (expanded) {
                    colors.take(4).forEachIndexed { index, colorHex ->
                        val bgColor = try {
                            Color(android.graphics.Color.parseColor(colorHex)).copy(alpha = 0.2f - index * 0.04f)
                        } catch (_: Exception) {
                            Color.Gray.copy(alpha = 0.15f)
                        }
                        val courseNames = listOf("数字电路", "高等数学", "英语听说", "人工智能")
                        val teachers = listOf("杨高", "王伟", "Lee", "张敏")

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = courseNames.getOrElse(index) { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = teachers.getOrElse(index) { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 7.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                } else {
                    colors.take(4).forEachIndexed { index, colorHex ->
                        val bgColor = try {
                            Color(android.graphics.Color.parseColor(colorHex))
                        } catch (_: Exception) {
                            Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                        )
                    }
                }
            }
        }
    }
}
