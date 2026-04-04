package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ClassTimeDao
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassTimeRepository @Inject constructor(
    classTimeDao: ClassTimeDao,
    private val scheduleRepository: ScheduleRepository
) : BaseRepository<ClassTime, ClassTimeDao>(classTimeDao) {

    override suspend fun getAll(): List<ClassTime> =
        dao.getClassTimesByConfigSync()

    override fun getAllFlow(): Flow<List<ClassTime>> = dao.getClassTimesByConfig()

    override suspend fun getById(id: Long): ClassTime? =
        dao.getClassTimesByConfigSync().find { it.id == id }

    override fun getByIdFlow(id: Long): Flow<ClassTime?> =
        kotlinx.coroutines.flow.flow { emit(getById(id)) }

    override suspend fun insert(entity: ClassTime): Long = dao.insertClassTime(entity)

    override suspend fun insertAll(entities: List<ClassTime>): List<Long> =
        entities.map { dao.insertClassTime(it) }

    override suspend fun update(entity: ClassTime) = dao.updateClassTime(entity)

    override suspend fun delete(entity: ClassTime) = dao.deleteClassTime(entity)

    override suspend fun deleteById(id: Long) {
        getById(id)?.let { delete(it) }
    }

    override suspend fun deleteAll() = dao.deleteAllByConfig()

    fun getClassTimesByConfig(configName: String = "default"): Flow<List<ClassTime>> =
        dao.getClassTimesByConfig(configName)

    suspend fun getClassTimesByConfigSync(configName: String = "default"): List<ClassTime> =
        dao.getClassTimesByConfigSync(configName)

    suspend fun getClassTime(configName: String = "default", sectionNumber: Int): ClassTime? =
        dao.getClassTime(configName, sectionNumber)

    suspend fun getClassTimesBySchedule(scheduleId: Long): List<ClassTime> {
        val schedule = scheduleRepository.getScheduleById(scheduleId)
        val configName = schedule?.classTimeConfigName ?: "default"
        return dao.getClassTimesByConfigSync(configName)
    }

    fun getClassTimesByScheduleFlow(scheduleId: Long): Flow<List<ClassTime>> = kotlinx.coroutines.flow.flow {
        val schedule = scheduleRepository.getScheduleById(scheduleId)
        emitAll(dao.getClassTimesByConfig(schedule?.classTimeConfigName ?: "default"))
    }

    suspend fun setClassTimeConfigForSchedule(scheduleId: Long, configName: String) {
        val schedule = scheduleRepository.getScheduleById(scheduleId)
        if (schedule != null) {
            scheduleRepository.updateSchedule(
                schedule.copy(
                    classTimeConfigName = configName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteAllByConfig(configName: String = "default") =
        dao.deleteAllByConfig(configName)

    private inline fun <T> runBlockingOrNull(block: () -> T): T? {
        return try { block() } catch (_: Exception) { null }
    }
}
