package com.wind.ggbond.classtime.service.contract

interface IUpdateManager {

    data class UpdateDecision(
        val shouldUpdate: Boolean,
        val reason: String
    )

    suspend fun checkAndTriggerAutoUpdate(): UpdateDecision

    fun isUpdateEnabled(): Boolean
}
