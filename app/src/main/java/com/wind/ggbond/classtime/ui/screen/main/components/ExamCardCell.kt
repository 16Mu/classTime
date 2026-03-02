package com.wind.ggbond.classtime.ui.screen.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wind.ggbond.classtime.data.local.entity.Exam

/**
 * 考试卡片单元格 - 在课表中显示考试
 */
@Composable
fun ExamCardCell(
    exam: Exam,
    onExamClick: (Exam) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clickable { onExamClick(exam) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)  // 浅红色背景
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：考试图标 + 课程名
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "考试",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFD32F2F)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (exam.examType.isNotEmpty()) {
                Text(
                    text = "📝 ${exam.examType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD32F2F).copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (exam.location.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 ${exam.location}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
            }
            
            if (exam.examTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "⏰ ${exam.examTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
            }
            
            if (exam.seat.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "💺 ${exam.seat}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
