package com.wind.ggbond.classtime.ui.screen.coursecolor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseColorPickerScreen(
    navController: NavController,
    courseName: String,
    viewModel: CourseColorPickerViewModel = hiltViewModel()
) {
    val selectedColor by viewModel.selectedColor.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(courseName) {
        viewModel.initForCourse(courseName)
    }

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("选择颜色") },
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
                    onClick = { viewModel.saveColor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isSaving && selectedColor.isNotEmpty(),
                    colors = if (selectedColor.isNotEmpty()) ButtonDefaults.buttonColors(
                        containerColor = try { Color(android.graphics.Color.parseColor(selectedColor)) }
                        catch (_: Exception) { MaterialTheme.colorScheme.primary }
                    ) else ButtonDefaults.buttonColors()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确认", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                CoursePreviewCard(courseName, selectedColor)
            }

            item {
                PresetColorSection(
                    selectedColor = selectedColor,
                    onSelect = { viewModel.selectColor(it) }
                )
            }

            item {
                ExtendedColorSection(
                    selectedColor = selectedColor,
                    onSelect = { viewModel.selectColor(it) }
                )
            }
        }
    }
}

@Composable
private fun CoursePreviewCard(courseName: String, colorHex: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = try {
                Color(android.graphics.Color.parseColor(colorHex)).copy(alpha = 0.85f)
            } catch (_: Exception) { Color.Gray }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = courseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = colorHex,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PresetColorSection(
    selectedColor: String,
    onSelect: (String) -> Unit
) {
    val presetColors = listOf(
        "#5B9BD5", "#F5A864", "#6FBE6E", "#9B7BD7", "#F57C82",
        "#52B3D9", "#FFD666", "#C89B7D", "#4A90E2", "#FF8C69",
        "#4DB897", "#B28FCE", "#EF7A82", "#58C1D3", "#FFC44C",
        "#B8956F", "#6B8DD6", "#FFB347", "#5FCF80", "#A68EC5",
        "#F59BB0", "#7FA3B8", "#FA9FB5", "#8BA7BB"
    )

    Column {
        Text(
            text = "预设颜色",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presetColors.chunked(6).forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowColors.forEach { color ->
                        val isSelected = selectedColor.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    try { Color(android.graphics.Color.parseColor(color)) }
                                    catch (_: Exception) { Color.Gray }
                                )
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .clickable { onSelect(color) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtendedColorSection(
    selectedColor: String,
    onSelect: (String) -> Unit
) {
    val extendedColors = listOf(
        "#EF4444", "#F97316", "#F59E0B", "#10B981", "#3B82F6",
        "#8B5CF6", "#EC4899", "#6B7280", "#1F2937", "#000000",
        "#00BCD4", "#8BC34A", "#FF5722", "#795548", "#607D8B",
        "#E91E63", "#9C27B0", "#673AB7", "#2196F3", "#009688",
        "#CDDC39", "#FFC107", "#FF9800", "#FF5722", "#795548"
    )

    Column {
        Text(
            text = "更多颜色",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            extendedColors.chunked(6).forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowColors.forEach { color ->
                        val isSelected = selectedColor.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    try { Color(android.graphics.Color.parseColor(color)) }
                                    catch (_: Exception) { Color.Gray }
                                )
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .clickable { onSelect(color) }
                        )
                    }
                }
            }
        }
    }
}
