package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自动登录日志实体
 */
@Entity(tableName = "auto_login_logs")
data class AutoLoginLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 时间戳
    val timestamp: Long = System.currentTimeMillis(),
    
    // 结果代码（OK/NEED_CAPTCHA/LOGIN_FAIL/NO_CREDENTIAL/NETWORK_ERROR/UNKNOWN_ERROR）
    val resultCode: String,
    
    // 结果消息
    val resultMessage: String,
    
    // 账号（仅记录用户名，不记录密码）
    val username: String? = null,
    
    // 耗时（毫秒）
    val durationMs: Long = 0,
    
    // 是否成功
    val success: Boolean = false,
    
    // 备注
    val remark: String? = null
)
