package com.wind.ggbond.classtime.ui.screen.scheduleimport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.wind.ggbond.classtime.util.WeekParser
import com.wind.ggbond.classtime.ui.screen.course.components.SelectionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineWeekSelector(
    selectedWeeks: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
    maxWeeks: Int = 20
) {
    var tempSelectedWeeks by remember { mutableStateOf(selectedWeeks.toSet()) }
    var selectionMode by remember { mutableStateOf(SelectionMode.MANUAL) }
    var pasteInputText by remember { mutableStateOf("") }
    var parseResultText by remember { mutableStateOf("") }

    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择周次",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "已选 ${tempSelectedWeeks.size} 周",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectionMode == SelectionMode.ALL,
                        onClick = {
                            selectionMode = SelectionMode.ALL
                            tempSelectedWeeks = (1..maxWeeks).toSet()
                        },
                        label = { Text("全选", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectionMode == SelectionMode.ODD,
                        onClick = {
                            selectionMode = SelectionMode.ODD
                            tempSelectedWeeks = (1..maxWeeks).filter { it % 2 == 1 }.toSet()
                        },
                        label = { Text("单周", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectionMode == SelectionMode.EVEN,
                        onClick = {
                            selectionMode = SelectionMode.EVEN
                            tempSelectedWeeks = (1..maxWeeks).filter { it % 2 == 0 }.toSet()
                        },
                        label = { Text("双周", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = tempSelectedWeeks.isEmpty(),
                        onClick = {
                            selectionMode = SelectionMode.MANUAL
                            tempSelectedWeeks = emptySet()
                        },
                        label = { Text("清空", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                val columns = 5
                val rows = (maxWeeks + columns - 1) / columns

                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(scrollState)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until columns) {
                                    val week = row * columns + col + 1
                                    if (week <= maxWeeks) {
                                        val isSelected = tempSelectedWeeks.contains(week)
                                        Surface(
                                            onClick = {
                                                tempSelectedWeeks = if (isSelected) {
                                                    tempSelectedWeeks - week
                                                } else {
                                                    tempSelectedWeeks + week
                                                }
                                                selectionMode = SelectionMode.MANUAL
                                            },
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = week.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = pasteInputText,
                    onValueChange = { newText ->
                        pasteInputText = newText
                        if (newText.isNotBlank()) {
                            val parsedWeeks = WeekParser.parseWeekExpression(newText)
                            val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                            parseResultText = if (validWeeks.isNotEmpty())
                                "识别: ${validWeeks.size}周"
                            else
                                "未识别到有效周次"
                        } else {
                            parseResultText = ""
                        }
                    },
                    label = { Text("粘贴表达式", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("例如: 1-8周", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        if (pasteInputText.isNotBlank()) {
                            IconButton(onClick = {
                                val parsedWeeks = WeekParser.parseWeekExpression(pasteInputText)
                                val validWeeks = parsedWeeks.filter { it in 1..maxWeeks }
                                if (validWeeks.isNotEmpty()) {
                                    tempSelectedWeeks = validWeeks.toSet()
                                    selectionMode = SelectionMode.MANUAL
                                }
                            }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Check, "应用", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    supportingText = if (parseResultText.isNotEmpty()) {
                        { Text(parseResultText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    } else null
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(tempSelectedWeeks.toList().sorted()) },
                        enabled = tempSelectedWeeks.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("确定", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
