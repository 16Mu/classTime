package com.wind.ggbond.classtime.util.common

/**
 * 应用常量定义
 * 
 * ✅ 集中管理魔法数字，提高可维护性
 */
object Constants {
    
    /**
     * 课程相关常量
     */
    object Course {
        const val MAX_COURSE_NAME_LENGTH = 100
        const val MAX_TEACHER_NAME_LENGTH = 50
        const val MAX_CLASSROOM_NAME_LENGTH = 100
        const val MAX_NOTE_LENGTH = 500
        
        const val MIN_SECTION_NUMBER = 1
        const val MAX_SECTION_NUMBER = 24  // 上午+下午各最多12节，总共最多24节
        
        const val MIN_SECTION_COUNT = 1
        const val MAX_SECTION_COUNT = 12
        const val MAX_MORNING_SECTION_COUNT = 12
        const val MAX_AFTERNOON_SECTION_COUNT = 12
        
        const val MIN_DAY_OF_WEEK = 1
        const val MAX_DAY_OF_WEEK = 7
        
        const val DEFAULT_REMINDER_MINUTES = 10
        const val DEFAULT_SECTION_COUNT = 2
    }
    
    /**
     * 学期相关常量
     */
    object Semester {
        const val MIN_WEEK_NUMBER = 1
        const val MAX_WEEK_NUMBER = 30  // 一般学期不超过30周
        const val DEFAULT_TOTAL_WEEKS = 20
    }
    
    /**
     * UI相关常量
     */
    object UI {
        // GridWeekView
        const val GRID_MIN_DISPLAYED_SECTIONS = 12  // 默认显示12节课（如果实际课程超过12节会自动扩展）
        const val COMPACT_MODE_EMPTY_WEIGHT = 0.015f  // 紧凑模式下空节次/列的权重
        
        // 缓存
        const val LRU_CACHE_MAX_SIZE = 10
        const val WEEK_PRELOAD_COUNT = 1  // 预加载相邻周数
        
        // 动画时长(ms)
        const val ANIMATION_DURATION_SHORT = 300
        const val ANIMATION_DURATION_MEDIUM = 500
        const val ANIMATION_DURATION_LONG = 800
        
        // 卡片样式
        const val COURSE_CARD_CORNER_RADIUS = 6  // dp
        const val COURSE_CARD_PADDING_HORIZONTAL = 4  // dp
        const val COURSE_CARD_PADDING_VERTICAL = 4  // dp
    }
    
    /**
     * 网络相关常量
     */
    object Network {
        const val HTTP_TIMEOUT_SECONDS = 30L
        const val HTTP_READ_TIMEOUT_SECONDS = 60L
        const val HTTP_WRITE_TIMEOUT_SECONDS = 60L
        
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1000L
    }
    
    /**
     * Cookie相关常量
     */
    object Cookie {
        const val DEFAULT_LIFETIME_HOURS = 24L
        const val CLEANUP_INTERVAL_HOURS = 6L
    }
    
    /**
     * 数据库相关常量
     */
    object Database {
        const val DATABASE_NAME = "course_schedule.db"
        const val DATABASE_VERSION = 12
        
        // 备份
        const val AUTO_BACKUP_ENABLED = true
        const val BACKUP_RETENTION_DAYS = 7
    }
    
    /**
     * WorkManager相关常量
     */
    object Worker {
        const val REMINDER_WORK_TAG = "course_reminder"
        const val SYNC_WORK_TAG = "daily_sync"
        const val CLEANUP_WORK_TAG = "cleanup"
        
        const val BACKOFF_DELAY_MS = 1000L
    }
    
    /**
     * 权限相关常量
     */
    object Permissions {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        const val STORAGE_PERMISSION_REQUEST_CODE = 1002
    }
}
