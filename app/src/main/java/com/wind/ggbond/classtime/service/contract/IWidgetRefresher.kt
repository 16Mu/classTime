package com.wind.ggbond.classtime.service.contract

import android.content.Context

interface IWidgetRefresher {

    fun refreshAllWidgets(context: Context)

    fun hasActiveWidgets(context: Context): Boolean

    fun startPeriodicRefresh(context: Context)

    fun stopPeriodicRefresh(context: Context)
}
