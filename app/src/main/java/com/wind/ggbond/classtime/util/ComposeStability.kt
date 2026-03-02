package com.wind.ggbond.classtime.util

import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable

/**
 * Compose 稳定性标记
 * 
 * 为数据类添加 @Stable 或 @Immutable 注解可以帮助 Compose 编译器
 * 更好地优化重组，减少不必要的UI更新
 * 
 * @Stable: 表示该类的公共属性在没有通知Compose的情况下不会改变
 * @Immutable: 表示该类的所有公共属性在构造后都不会改变
 */

// 该文件用于集中管理 Compose 稳定性相关的工具和扩展
// 虽然我们的 Entity 类已经是 data class，但添加 @Immutable 注解
// 可以明确告诉 Compose 编译器这些类是不可变的

/**
 * 标记课程颜色列表为稳定的
 */
@Immutable
data class CourseColor(
    val value: androidx.compose.ui.graphics.Color,
    val name: String
)

