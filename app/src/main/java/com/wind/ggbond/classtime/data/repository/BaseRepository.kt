package com.wind.ggbond.classtime.data.repository

import kotlinx.coroutines.flow.Flow

abstract class BaseRepository<T, DAO>(protected val dao: DAO) {

    abstract suspend fun getAll(): List<T>
    abstract fun getAllFlow(): Flow<List<T>>
    abstract suspend fun getById(id: Long): T?
    abstract fun getByIdFlow(id: Long): Flow<T?>

    abstract suspend fun insert(entity: T): Long
    abstract suspend fun update(entity: T)
    abstract suspend fun delete(entity: T)
    abstract suspend fun deleteById(id: Long)
    abstract suspend fun deleteAll()

    open suspend fun insertAll(entities: List<T>): List<Long> {
        return entities.map { insert(it) }
    }

    open suspend fun getByIds(ids: List<Long>): List<T> {
        return ids.mapNotNull { getById(it) }
    }

    open suspend fun deleteAll(entities: List<T>) {
        entities.forEach { delete(it) }
    }

    open suspend fun deleteByIds(ids: List<Long>) {
        ids.forEach { deleteById(it) }
    }

    open suspend fun insertOrUpdate(entity: T, predicate: (T) -> Boolean): Long {
        val existing = getAll().find(predicate)
        return if (existing != null) {
            update(entity)
            -1
        } else {
            insert(entity)
        }
    }
}
