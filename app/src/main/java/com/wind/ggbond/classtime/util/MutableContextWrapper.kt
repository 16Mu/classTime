package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.ContextWrapper

/**
 * 可变Context包装器，用于避免WebView内存泄漏
 * 
 * 使用方法：
 * ```kotlin
 * val webViewContext = MutableContextWrapper(context.applicationContext)
 * val webView = WebView(webViewContext)
 * // ... 使用完后
 * webViewContext.setBaseContext(null)
 * webView.destroy()
 * ```
 */
class MutableContextWrapper(base: Context?) : ContextWrapper(base) {
    private var mBase: Context? = base
    
    override fun getBaseContext(): Context? {
        return mBase
    }
    
    fun setBaseContext(base: Context?) {
        mBase = base
    }
}












