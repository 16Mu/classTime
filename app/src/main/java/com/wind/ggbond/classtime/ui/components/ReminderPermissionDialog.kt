package com.wind.ggbond.classtime.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.wind.ggbond.classtime.util.ReminderPermissionHelper

/**
 * 课程提醒权限引导对话框
 * 在用户开启提醒功能时，检查并引导用户授予必要的权限
 * 
 * 注意：现在默认使用 PermissionStepGuideDialog 以获得更好的用户体验
 */
@Composable
fun ReminderPermissionDialog(
    onDismiss: () -> Unit,
    onAllPermissionsGranted: () -> Unit,
    useStepGuide: Boolean = true  // 是否使用新的步骤引导对话框
) {
    // 默认使用新的步骤引导对话框
    PermissionStepGuideDialog(
        onDismiss = onDismiss,
        onAllCriticalCompleted = onAllPermissionsGranted
    )
}


