package com.wind.ggbond.classtime.ui.screen.course.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekSelectorDialog(
    selectedWeeks: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
    maxWeeks: Int = 20
) {
    val haptic = LocalHapticFeedback.current
    
    var tempSelectedWeeks by remember { mutableStateOf(selectedWeeks.toSet()) }
    var selectionMode by remember { mutableStateOf(SelectionMode.MANUAL) }
    var dragStartSnapshot by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isDragSelecting by remember { mutableStateOf(true) }
    var draggedWeeks by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val weekItemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var gridPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var pasteInputText by remember { mutableStateOf("") }
    var parseResultText by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(text = "选择周次", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectionMode == SelectionMode.ALL, onClick = {
                        selectionMode = SelectionMode.ALL; tempSelectedWeeks = (1..maxWeeks).toSet()
                    }, label = { Text("全选") })
                    FilterChip(selected = selectionMode == SelectionMode.ODD, onClick = {
                        selectionMode = SelectionMode.ODD; tempSelectedWeeks = (1..maxWeeks).filter { it % 2 == 1 }.toSet()
                    }, label = { Text("单周") })
                    FilterChip(selected = selectionMode == SelectionMode.EVEN, onClick = {
                        selectionMode = SelectionMode.EVEN; tempSelectedWeeks = (1..maxWeeks).filter { it % 2 == 0 }.toSet()
                    }, label = { Text("双周") })
                    FilterChip(selected = tempSelectedWeeks.isEmpty(), onClick = {
                        selectionMode = SelectionMode.MANUAL; tempSelectedWeeks = emptySet()
                    }, label = { Text("清空") })
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "已选择 ${tempSelectedWeeks.size} 周，支持拖动多选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val columns = 5
                val rows = (maxWeeks + columns - 1) / columns
                val scrollState = rememberScrollState()
                var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
                var isDragging by remember { mutableStateOf(false) }
                val dragThreshold = 10f
                
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .onGloballyPositioned { gridPositionInRoot = it.positionInRoot() }
                        .pointerInput(maxWeeks) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                dragStartPosition = down.position
                                isDragging = false
                                dragStartSnapshot = tempSelectedWeeks.toSet()
                                draggedWeeks = emptySet()

                                val downPositionInRoot = gridPositionInRoot + down.position
                                val startWeek = findWeekAtPositionInRoot(downPositionInRoot, weekItemBounds)
                                if (startWeek == null) return@awaitEachGesture

                                isDragSelecting = !dragStartSnapshot.contains(startWeek)
                                draggedWeeks = setOf(startWeek)
                                tempSelectedWeeks = applyDrag(dragStartSnapshot, draggedWeeks, isDragSelecting)
                                selectionMode = SelectionMode.MANUAL

                                var hasDragged = false
                                drag(down.id) { change ->
                                    val distance = (change.position - dragStartPosition).getDistance()
                                    if (distance > dragThreshold) {
                                        if (!isDragging) { isDragging = true; hasDragged = true }
                                        change.consume()
                                        val positionInRoot = gridPositionInRoot + change.position
                                        val currentWeek = findWeekAtPositionInRoot(positionInRoot, weekItemBounds)
                                        if (currentWeek != null && !draggedWeeks.contains(currentWeek)) {
                                            draggedWeeks = draggedWeeks + currentWeek
                                            tempSelectedWeeks = applyDrag(dragStartSnapshot, draggedWeeks, isDragSelecting)
                                        }
                                    }
                                }

                                if (!hasDragged) {
                                    tempSelectedWeeks = if (dragStartSnapshot.contains(startWeek))
                                        dragStartSnapshot - startWeek else dragStartSnapshot + startWeek
                                    selectionMode = SelectionMode.MANUAL
                                }
                                draggedWeeks = emptySet()
                                isDragging = false
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (col in 0 until columns) {
                                    val week = row * columns + col + 1
                                    if (week <= maxWeeks) {
                                        val isSelected = tempSelectedWeeks.contains(week)
                                        Box(
                                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                                .onGloballyPositioned { weekItemBounds[week] = it.boundsInRoot() }
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                                                .border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = week.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(value = pasteInputText, onValueChange = { newText ->
                    pasteInputText = newText
                    if (newText.isNotBlank()) {
                        val parsedWeeks = WeekParser.parseWeekExpression(newText)
                        val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                        parseResultText = if (validWeeks.isNotEmpty()) "识别: ${validWeeks.size}周(${WeekParser.formatWeekList(validWeeks)})" else "未识别到有效周次"
                    } else parseResultText = ""
                }, label = { Text("粘贴周次表达式") }, placeholder = { Text("例如: 1-8周, 1-16周, 1,3,5周") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = {
                    if (pasteInputText.isNotBlank()) {
                        IconButton(onClick = {
                            val parsedWeeks = WeekParser.parseWeekExpression(pasteInputText)
                            val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                            if (validWeeks.isNotEmpty()) { tempSelectedWeeks = validWeeks.toSet(); selectionMode = SelectionMode.MANUAL }
                        }) { Icon(Icons.Default.Check, contentDescription = "应用", tint = MaterialTheme.colorScheme.primary) }
                    }
                }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = {
                    val parsedWeeks = WeekParser.parseWeekExpression(pasteInputText)
                    val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                    if (validWeeks.isNotEmpty()) { tempSelectedWeeks = validWeeks.toSet(); selectionMode = SelectionMode.MANUAL }
                }), supportingText = if (parseResultText.isNotEmpty()) ({ Text(parseResultText, color = MaterialTheme.colorScheme.primary) }) else null, shape = RoundedCornerShape(10.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDismiss() }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onConfirm(tempSelectedWeeks.toList().sorted()) }, enabled = tempSelectedWeeks.isNotEmpty()) { Text("确定") }
                }
            }
        }
    }
}

private fun applyDrag(snapshot: Set<Int>, dragged: Set<Int>, isSelecting: Boolean): Set<Int> {
    return if (isSelecting) snapshot + dragged else snapshot - dragged
}

private fun findWeekAtPositionInRoot(positionInRoot: Offset, boundsInRoot: Map<Int, Rect>): Int? {
    for ((week, rect) in boundsInRoot) { if (rect.contains(positionInRoot)) return week }
    return null
}

enum class SelectionMode { MANUAL, ALL, ODD, EVEN }
