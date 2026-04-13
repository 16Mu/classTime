package com.wind.ggbond.classtime.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wind.ggbond.classtime.data.local.dao.*
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.database.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCourseDatabase(@ApplicationContext context: Context): CourseDatabase {
        return Room.databaseBuilder(
            context,
            CourseDatabase::class.java,
            CourseDatabase.DATABASE_NAME
        )
            .addMigrations(*Migrations.getAllMigrations())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    AppLogger.i("DatabaseModule", "数据库已创建 - 版本: ${db.version}")
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    AppLogger.i("DatabaseModule", "数据库已打开 - 版本: ${db.version}")
                }

                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    AppLogger.w("DatabaseModule",
                        "数据库发生破坏性迁移！旧数据已清除。版本: ${db.version}")
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideCourseDao(database: CourseDatabase): CourseDao {
        return database.courseDao()
    }

    @Provides
    @Singleton
    fun provideClassTimeDao(database: CourseDatabase): ClassTimeDao {
        return database.classTimeDao()
    }

    @Provides
    @Singleton
    fun provideReminderDao(database: CourseDatabase): ReminderDao {
        return database.reminderDao()
    }

    @Provides
    @Singleton
    fun provideScheduleDao(database: CourseDatabase): ScheduleDao {
        return database.scheduleDao()
    }

    @Provides
    @Singleton
    fun provideSchoolDao(database: CourseDatabase): SchoolDao {
        return database.schoolDao()
    }

    @Provides
    @Singleton
    fun provideCourseAdjustmentDao(database: CourseDatabase): CourseAdjustmentDao {
        return database.courseAdjustmentDao()
    }

    @Provides
    @Singleton
    fun provideExamDao(database: CourseDatabase): ExamDao {
        return database.examDao()
    }

    @Provides
    @Singleton
    fun provideAutoUpdateLogDao(database: CourseDatabase): AutoUpdateLogDao {
        return database.autoUpdateLogDao()
    }

    @Provides
    @Singleton
    fun provideAutoLoginLogDao(database: CourseDatabase): AutoLoginLogDao {
        return database.autoLoginLogDao()
    }
}
