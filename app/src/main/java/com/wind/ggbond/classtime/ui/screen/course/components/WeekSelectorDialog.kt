package com.wind.ggbond.classtime.ui.screen.course.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wind.ggbond.classtime.util.WeekParser

/**
 * 周次选择器对话框
 * 支持点击选择、拖动选择和粘贴周次表达式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekSelectorDialog(
    selectedWeeks: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
    maxWeeks: Int = 20
) {
    // 临时选中的周次集合
    var tempSelectedWeeks by remember { mutableStateOf(selectedWeeks.toSet()) }

    // 快速选择模式
    var selectionMode by remember { mutableStateOf(SelectionMode.MANUAL) }
    // 拖动开始时的选中状态快照（用于判断拖动是选中还是取消）
    var dragStartSnapshot by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 拖动模式：true=选中，false=取消
    var isDragSelecting by remember { mutableStateOf(true) }
    // 当前拖动经过的周次集合
    var draggedWeeks by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 每个周次格子的位置和大小信息
    val weekItemBounds = remember { mutableStateMapOf<Int, Rect>() }

    // 网格容器在 Root 中的位置（用于将手指坐标换算到 Root 坐标系）
    var gridPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    
    // 粘贴输入框内容
    var pasteInputText by remember { mutableStateOf("") }
    // 解析结果提示
    var parseResultText by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "选择周次",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 快速选择模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectionMode == SelectionMode.ALL,
                        onClick = {
                            selectionMode = SelectionMode.ALL
                            tempSelectedWeeks = (1..maxWeeks).toSet()
                        },
                        label = { Text("全选") }
                    )
                    FilterChip(
                        selected = selectionMode == SelectionMode.ODD,
                        onClick = {
                            selectionMode = SelectionMode.ODD
                            tempSelectedWeeks = (1..maxWeeks).filter { it % 2 == 1 }.toSet()
                        },
                        label = { Text("单周") }
                    )
                    FilterChip(
                        selected = selectionMode == SelectionMode.EVEN,
                        onClick = {
                            selectionMode = SelectionMode.EVEN
                            tempSelectedWeeks = (1..maxWeeks).filter { it % 2 == 0 }.toSet()
                        },
                        label = { Text("双周") }
                    )
                    FilterChip(
                        selected = tempSelectedWeeks.isEmpty(),
                        onClick = {
                            selectionMode = SelectionMode.MANUAL
                            tempSelectedWeeks = emptySet()
                        },
                        label = { Text("清空") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 提示文字
                Text(
                    text = "已选择 ${tempSelectedWeeks.size} 周（支持点击和拖动选择）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 每行列数
                val columns = 5
                // 计算行数
                val rows = (maxWeeks + columns - 1) / columns
                
                // 周次网格 - 使用 Root 坐标系命中检测，同时处理点击和拖动
                val scrollState = rememberScrollState()
                // 拖动起始位置（用于判断是点击还是拖动）
                var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
                // 是否正在拖动（移动距离超过阈值）
                var isDragging by remember { mutableStateOf(false) }
                // 拖动距离阈值（超过此距离视为拖动，否则视为点击）
                val dragThreshold = 10f
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { layoutCoordinates ->
                            gridPositionInRoot = layoutCoordinates.positionInRoot()
                        }
                        .pointerInput(maxWeeks) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // 记录起始位置
                                dragStartPosition = down.position
                                isDragging = false
                                
                                // 拖动开始：记录当前选中状态快照
                                dragStartSnapshot = tempSelectedWeeks.toSet()
                                draggedWeeks = emptySet()

                                val downPositionInRoot = gridPositionInRoot + down.position
                                val startWeek = findWeekAtPositionInRoot(downPositionInRoot, weekItemBounds)
                                if (startWeek == null) {
                                    return@awaitEachGesture
                                }

                                // 如果起始点的周次已选中，则拖动为取消模式；否则为选中模式
                                isDragSelecting = !dragStartSnapshot.contains(startWeek)
                                draggedWeeks = setOf(startWeek)
                                tempSelectedWeeks = applyDrag(dragStartSnapshot, draggedWeeks, isDragSelecting)
                                selectionMode = SelectionMode.MANUAL

                                var hasDragged = false
                                drag(down.id) { change ->
                                    // 计算移动距离
                                    val distance = (change.position - dragStartPosition).getDistance()
                                    
                                    // 超过阈值才视为拖动
                                    if (distance > dragThreshold) {
                                        if (!isDragging) {
                                            isDragging = true
                                            hasDragged = true
                                        }
                                        // consume：避免 verticalScroll 抢走拖动
                                        change.consume()

                                        val positionInRoot = gridPositionInRoot + change.position
                                        val currentWeek = findWeekAtPositionInRoot(positionInRoot, weekItemBounds)
                                        if (currentWeek != null && !draggedWeeks.contains(currentWeek)) {
                                            draggedWeeks = draggedWeeks + currentWeek
                                            tempSelectedWeeks = applyDrag(dragStartSnapshot, draggedWeeks, isDragSelecting)
                                        }
                                    }
                                }

                                // 如果没有拖动，则视为点击（切换选中状态）
                                if (!hasDragged) {
                                    tempSelectedWeeks = if (dragStartSnapshot.contains(startWeek)) {
                                        dragStartSnapshot - startWeek
                                    } else {
                                        dragStartSnapshot + startWeek
                                    }
                                    selectionMode = SelectionMode.MANUAL
                                }

                                draggedWeeks = emptySet()
                                isDragging = false
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until columns) {
                                    val week = row * columns + col + 1
                                    if (week <= maxWeeks) {
                                        val isSelected = tempSelectedWeeks.contains(week)
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .onGloballyPositioned { layoutCoordinates ->
                                                    // 记录每个格子的位置和大小
                                                    weekItemBounds[week] = layoutCoordinates.boundsInRoot()
                                                }
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = week.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        // 占位空白格
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 粘贴周次输入框
                OutlinedTextField(
                    value = pasteInputText,
                    onValueChange = { newText ->
                        pasteInputText = newText
                        // 实时解析并显示结果
                        if (newText.isNotBlank()) {
                            val parsedWeeks = WeekParser.parseWeekExpression(newText)
                            // 过滤掉超出范围的周次
                            val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                            if (validWeeks.isNotEmpty()) {
                                parseResultText = "识别: ${validWeeks.size}周 (${WeekParser.formatWeekList(validWeeks)})"
                            } else {
                                parseResultText = "未识别到有效周次"
                            }
                        } else {
                            parseResultText = ""
                        }
                    },
                    label = { Text("粘贴周次表达式") },
                    placeholder = { Text("例如: 1-8周(单), 1-16周, 1,3,5周") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (pasteInputText.isNotBlank()) {
                            IconButton(onClick = {
                                val parsedWeeks = WeekParser.parseWeekExpression(pasteInputText)
                                val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                                if (validWeeks.isNotEmpty()) {
                                    tempSelectedWeeks = validWeeks.toSet()
                                    selectionMode = SelectionMode.MANUAL
                                    parseResultText = "已选择 ${validWeeks.size}周"
                                }
                            }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "应用",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsedWeeks = WeekParser.parseWeekExpression(pasteInputText)
                            val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                            if (validWeeks.isNotEmpty()) {
                                tempSelectedWeeks = validWeeks.toSet()
                                selectionMode = SelectionMode.MANUAL
                            }
                        }
                    ),
                    supportingText = if (parseResultText.isNotEmpty()) {
                        { Text(parseResultText, color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    shape = RoundedCornerShape(10.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(tempSelectedWeeks.toList().sorted()) },
                        enabled = tempSelectedWeeks.isNotEmpty()
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/**
 * 根据拖动操作计算新的选中集合
 * 
 * @param snapshot 拖动开始时的选中快照
 * @param dragged 拖动经过的周次集合
 * @param isSelecting true=选中模式，false=取消模式
 * @return 新的选中集合
 */
private fun applyDrag(
    snapshot: Set<Int>,
    dragged: Set<Int>,
    isSelecting: Boolean
): Set<Int> {
    return if (isSelecting) {
        // 选中模式：在快照基础上添加拖动经过的周次
        snapshot + dragged
    } else {
        // 取消模式：在快照基础上移除拖动经过的周次
        snapshot - dragged
    }
}

/**
 * 根据触摸位置（Root 坐标系）查找对应的周次编号
 */
private fun findWeekAtPositionInRoot(
    positionInRoot: Offset,
    boundsInRoot: Map<Int, Rect>
): Int? {
    for ((week, rect) in boundsInRoot) {
        if (rect.contains(positionInRoot)) {
            return week
        }
    }
    return null
}

/**
 * 快速选择模式枚举
 */
enum class SelectionMode {
    MANUAL, ALL, ODD, EVEN
}

