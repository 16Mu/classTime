package com.wind.ggbond.classtime.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.util.MutableContextWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后台隐藏 WebView 课表抓取服务
 * 
 * 功能：使用Cookie自动登录并抓取课表
 */
@Singleton
class BackgroundWebViewFetchService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
) {
    companion object {
        private const val TAG = "BackgroundWebViewFetch"
        private const val PAGE_LOAD_TIMEOUT = 30000L
        private const val JS_EXECUTION_DELAY = 2000L
    }
    
    /**
     * 使用Cookie抓取课表
     */
    suspend fun fetchScheduleWithWebView(
        config: SchoolConfig,
        cookies: String
    ): Result<List<ParsedCourse>> = suspendCancellableCoroutine { continuation ->
        
        val mainHandler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var timeoutHandler: Handler? = null
        val hasResumed = AtomicBoolean(false)
        
        fun safeResume(block: () -> Unit) {
            if (hasResumed.compareAndSet(false, true)) {
                try {
                    cleanupWebView(webView, timeoutHandler)
                    block()
                } catch (e: Exception) {
                    Log.e(TAG, "Resume失败", e)
                }
            }
        }
        
        mainHandler.post {
            try {
                Log.d(TAG, "创建后台WebView")
                val webViewContext = MutableContextWrapper(context.applicationContext)
                
                webView = WebView(webViewContext).apply {
                    layout(0, 0, 1, 1)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        setSupportZoom(false)
                        blockNetworkImage = true
                    }
                    
                    // 设置Cookie
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.removeAllCookies(null)
                    
                    cookies.split(";").forEach { cookie ->
                        val trimmed = cookie.trim()
                        if (trimmed.isNotEmpty()) {
                            cookieManager.setCookie(config.loginUrl, trimmed)
                        }
                    }
                    cookieManager.flush()
                    
                    webViewClient = object : WebViewClient() {
                        private val pageFinished = AtomicBoolean(false)
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            if (!pageFinished.compareAndSet(false, true)) {
                                Log.d(TAG, "忽略重复的onPageFinished")
                                return
                            }
                            
                            if (hasResumed.get()) {
                                return
                            }
                            
                            Log.d(TAG, "页面加载完成: $url")
                            
                            mainHandler.postDelayed({
                                if (hasResumed.get()) return@postDelayed
                                
                                val extractor = extractorFactory.getExtractor(config.id)
                                if (extractor != null) {
                                    Log.d(TAG, "✅ 找到专用提取器: ${extractor.schoolName} (${extractor.schoolId})")
                                } else {
                                    Log.w(TAG, "⚠️ 未找到学校 ${config.id} 的专用提取器，使用默认提取（可能失败）")
                                }
                                
                                val jsCode = extractor?.generateExtractionScript() 
                                    ?: buildDefaultExtractionScript()
                                
                                Log.d(TAG, "开始执行JavaScript提取数据 (使用${if (extractor != null) "专用" else "默认"}脚本)")
                                view?.evaluateJavascript(jsCode) { result ->
                                    if (hasResumed.get()) {
                                        return@evaluateJavascript
                                    }
                                    
                                    try {
                                        Log.d(TAG, "JavaScript返回结果(前500字): ${result?.take(500)}")
                                        
                                        val courses = extractor?.parseCourses(result ?: "") 
                                            ?: emptyList()
                                        
                                        safeResume {
                                            if (courses.isNotEmpty()) {
                                                Log.d(TAG, "✅ 成功提取 ${courses.size} 门课程")
                                                courses.take(3).forEach { course ->
                                                    Log.d(TAG, "  示例课程: ${course.courseName} - ${course.teacher}")
                                                }
                                                continuation.resume(Result.success(courses))
                                            } else {
                                                Log.e(TAG, "❌ 提取到0门课程！JavaScript结果: $result")
                                                Log.e(TAG, "❌ 这可能导致数据被误删，已阻止更新")
                                                continuation.resumeWithException(
                                                    Exception("未提取到课程数据，Cookie可能已失效或提取器不匹配")
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "解析失败", e)
                                        safeResume {
                                            continuation.resumeWithException(e)
                                        }
                                    }
                                }
                            }, JS_EXECUTION_DELAY)
                        }
                        
                                                override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            super.onReceivedError(view, request, error)
                            if (!request.isForMainFrame) return
                            val description = error.description?.toString().orEmpty()
                            Log.e(TAG, "WebView??????: $description")
                            safeResume {
                                continuation.resumeWithException(Exception("?????????: $description"))
                            }
                        }
                    }
                }
                
                // 超时处理
                timeoutHandler = Handler(Looper.getMainLooper())
                timeoutHandler?.postDelayed({
                    safeResume {
                        Log.e(TAG, "页面加载超时")
                        continuation.resumeWithException(Exception("页面加载超时"))
                    }
                }, PAGE_LOAD_TIMEOUT)
                
                // 加载登录页面（有Cookie会自动跳转）
                Log.d(TAG, "🔐 加载登录页面: ${config.loginUrl}")
                webView?.loadUrl(config.loginUrl)
                
            } catch (e: Exception) {
                Log.e(TAG, "创建WebView失败", e)
                safeResume {
                    continuation.resumeWithException(e)
                }
            }
        }
        
        continuation.invokeOnCancellation {
            Log.d(TAG, "任务被取消")
            hasResumed.set(true)
            cleanupWebView(webView, timeoutHandler)
        }
    }
    
    /**
     * 使用账号密码自动登录并抓取（暂不实现，使用Cookie方式即可）
     */
    suspend fun fetchScheduleWithAutoLogin(
        config: SchoolConfig,
        username: String,
        password: String
    ): Result<Pair<List<ParsedCourse>, String>> {
        // 简化实现：返回失败，提示使用Cookie方式
        return Result.failure(Exception("请使用Cookie方式登录"))
    }
    
    private fun buildDefaultExtractionScript(): String {
        return """
            (function() {
                try {
                    const courses = [];
                    const container = document.getElementById('kbgrid_table_0') || 
                                     document.querySelector('[id*="kb"]');
                    
                    if (!container) {
                        return JSON.stringify({ success: false, courses: [] });
                    }
                    
                    // 简单提取
                    const cells = container.querySelectorAll('td');
                    // ... 简化的提取逻辑
                    
                    return JSON.stringify({ success: true, courses: courses });
                } catch (e) {
                    return JSON.stringify({ success: false, error: e.message });
                }
            })();
        """.trimIndent()
    }
    
    private fun cleanupWebView(webView: WebView?, timeoutHandler: Handler?) {
        try {
            timeoutHandler?.removeCallbacksAndMessages(null)
            
            if (Looper.myLooper() == Looper.getMainLooper()) {
                destroyWebView(webView)
            } else {
                Handler(Looper.getMainLooper()).post {
                    destroyWebView(webView)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理失败", e)
        }
    }
    
    private fun destroyWebView(webView: WebView?) {
        try {
            webView?.apply {
                stopLoading()
                webViewClient = object : WebViewClient() {}
                webChromeClient = null
                loadUrl("about:blank")
                clearHistory()
                clearCache(true)
                (context as? MutableContextWrapper)?.setBaseContext(null)
                removeAllViews()
                destroy()
            }
            Log.d(TAG, "✅ WebView已清理")
        } catch (e: Exception) {
            Log.e(TAG, "销毁WebView失败", e)
        }
    }
}



