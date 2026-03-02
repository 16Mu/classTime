package com.wind.ggbond.classtime.service

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.util.HtmlScheduleParser
import com.wind.ggbond.classtime.util.MutableContextWrapper
import com.wind.ggbond.classtime.util.SecureCookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 🎯 统一的课表获取服务 - 基于Cookie
 * 
 * 核心设计思路：
 * 1. 使用保存的Cookie获取课表
 * 2. Cookie失效时提示用户重新登录
 * 3. 手动导入和自动更新使用相同的底层实现
 * 
 * @author AI Assistant
 * @since 2025-11-04
 */
@Singleton
class UnifiedScheduleFetchService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val htmlParser: HtmlScheduleParser,
    private val secureCookieManager: SecureCookieManager,
    private val extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
) {
    companion object {
        private const val TAG = "UnifiedScheduleFetch"
        private const val TIMEOUT_MS = 30000L // 30秒超时
        private const val PAGE_LOAD_WAIT_MS = 1000L // 页面加载后等待1秒
    }
    
    /**
     * 🌟 核心方法：获取课表数据
     * 
     * 工作流程：
     * 1. 使用保存的Cookie获取课表
     * 2. 如果Cookie失效或不存在，提示用户重新登录
     * 
     * @param schoolConfig 学校配置
     * @param showWebView 是否显示WebView（手动导入时为true，自动更新时为false）
     * @return 课程列表和Cookie
     */
    suspend fun fetchSchedule(
        schoolConfig: SchoolConfig,
        showWebView: Boolean = false
    ): Result<Pair<List<ParsedCourse>, String>> {
        
        val domain = extractDomain(schoolConfig.loginUrl)
        
        // 1. ✅ 使用Cookie获取课表
        val savedCookies = secureCookieManager.getCookies(domain)
        if (savedCookies.isNullOrEmpty()) {
            Log.w(TAG, "❌ 未找到保存的Cookie")
            return Result.failure(
                Exception("登录凭证不存在，请重新登录教务系统导入课表")
            )
        }
        
        Log.d(TAG, "✅ 找到保存的Cookie，使用Cookie获取课表")
        
        val cookieResult = try {
            fetchWithCookie(schoolConfig, savedCookies)
        } catch (e: Exception) {
            Log.w(TAG, "使用Cookie获取课表失败: ${e.message}")
            return Result.failure(
                Exception("登录已过期，请重新登录教务系统导入课表")
            )
        }
        
        if (cookieResult.isSuccess) {
            Log.d(TAG, "✅ Cookie有效，成功获取课表")
            return cookieResult
        } else {
            Log.d(TAG, "⚠️ Cookie已失效")
            return Result.failure(
                Exception("登录已过期，请重新登录教务系统导入课表")
            )
        }
    }
    
    /**
     * 从URL中提取域名
     */
    private fun extractDomain(url: String): String {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.host
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * 使用Cookie直接获取课表（不需要登录）
     */
    private suspend fun fetchWithCookie(
        config: SchoolConfig,
        cookies: String
    ): Result<Pair<List<ParsedCourse>, String>> = suspendCancellableCoroutine { continuation ->
        
        val mainHandler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var timeoutHandler: Handler? = null
        val hasResumed = AtomicBoolean(false)
        
        // 安全的resume函数
        fun safeResume(block: () -> Unit) {
            if (hasResumed.compareAndSet(false, true)) {
                try {
                    // 清理WebView
                    mainHandler.post {
                        webView?.apply {
                            stopLoading()
                            webViewClient = WebViewClient()
                            webChromeClient = null
                            removeAllViews()
                            destroy()
                        }
                        webView = null
                    }
                    
                    // 取消超时
                    timeoutHandler?.removeCallbacksAndMessages(null)
                    timeoutHandler = null
                    
                    block()
                } catch (e: Exception) {
                    Log.e(TAG, "Resume失败", e)
                }
            }
        }
        
        mainHandler.post {
            try {
                Log.d(TAG, "🍪 使用Cookie直接访问课表页面: ${config.name}")
                
                // 创建WebView
                val webViewContext = MutableContextWrapper(context.applicationContext)
                webView = WebView(webViewContext).apply {
                    layout(0, 0, 1, 1)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        blockNetworkImage = true // 不加载图片，提升速度
                    }
                    
                    // ✅ 设置Cookie
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        removeAllCookies(null)
                        flush()
                        
                        // 为登录域名设置Cookie
                        val domain = extractDomain(config.loginUrl)
                        val cookieParts = cookies.split(";")
                        cookieParts.forEach { cookie ->
                            val trimmedCookie = cookie.trim()
                            if (trimmedCookie.isNotEmpty()) {
                                setCookie(domain, trimmedCookie)
                            }
                        }
                        flush()
                        
                        Log.d(TAG, "✓ Cookie已设置到WebView: $domain")
                    }
                    
                    webViewClient = object : WebViewClient() {
                        private var pageLoadCount = 0
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            if (hasResumed.get()) return
                            
                            pageLoadCount++
                            Log.d(TAG, "📄 页面加载完成 ($pageLoadCount): $url")
                            
                            // 判断当前页面类型
                            val isLoginPage = url?.contains("login", ignoreCase = true) == true ||
                                             url?.contains("cas", ignoreCase = true) == true
                            
                            if (isLoginPage && pageLoadCount > 1) {
                                // 如果加载了多次还是登录页，说明Cookie已失效
                                Log.w(TAG, "⚠️ Cookie已失效，无法自动跳转")
                                safeResume {
                                    continuation.resumeWithException(
                                        Exception("Cookie已失效")
                                    )
                                }
                                return
                            }
                            
                            if (!isLoginPage) {
                                // 已经自动跳转到课表页面了
                                Log.d(TAG, "✅ 服务器识别Cookie，自动跳转到课表页面")
                                
                                mainHandler.postDelayed({
                                    if (hasResumed.get()) return@postDelayed
                                    
                                    // 提取课表数据
                                    Log.d(TAG, "📚 开始提取课表数据")
                                    extractScheduleData(view, config, safeResume = { safeResume(it) }, continuation)
                                }, PAGE_LOAD_WAIT_MS)
                            } else {
                                // 第一次加载登录页，等待服务器自动跳转
                                Log.d(TAG, "等待服务器识别Cookie并自动跳转...")
                            }
                        }
                        
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.e(TAG, "❌ 页面加载错误: $description")
                            safeResume {
                                continuation.resumeWithException(
                                    Exception("页面加载失败: $description")
                                )
                            }
                        }
                    }
                    
                    // ✅ 访问登录页面（带上Cookie，让服务器自动识别并跳转）
                    loadUrl(config.loginUrl)
                }
                
                // 设置超时
                timeoutHandler = Handler(Looper.getMainLooper())
                timeoutHandler?.postDelayed({
                    safeResume {
                        continuation.resumeWithException(
                            Exception("获取课表超时（${TIMEOUT_MS / 1000}秒）")
                        )
                    }
                }, TIMEOUT_MS)
                
                // 支持取消
                continuation.invokeOnCancellation {
                    Log.w(TAG, "⚠️ 操作被取消")
                    safeResume {
                        // 已取消，不需要额外操作
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 使用Cookie获取课表失败", e)
                safeResume {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * 提取课表数据
     */
    private fun extractScheduleData(
        webView: WebView?,
        config: SchoolConfig,
        safeResume: (() -> Unit) -> Unit,
        continuation: kotlinx.coroutines.CancellableContinuation<Result<Pair<List<ParsedCourse>, String>>>
    ) {
        if (webView == null) {
            safeResume {
                continuation.resume(Result.failure(Exception("WebView已销毁")))
            }
            return
        }
        
        // 获取Cookie
        val cookies = CookieManager.getInstance().getCookie(config.scheduleUrl) ?: ""
        
        // 获取提取脚本
        val extractor = extractorFactory.getExtractor(config.id)
        if (extractor == null) {
            Log.e(TAG, "❌ 未找到学校 ${config.name} 的提取器")
            safeResume {
                continuation.resume(Result.failure(Exception("该学校暂未适配自动提取功能")))
            }
            return
        }
        
        val extractScript = extractor.generateExtractionScript()
        Log.d(TAG, "🔧 使用提取器: ${extractor.javaClass.simpleName}")
        
        // 执行提取脚本
        webView.evaluateJavascript("($extractScript)") { result ->
            try {
                Log.d(TAG, "📊 提取器返回数据: ${result?.take(200)}...")
                
                // 使用提取器解析数据
                val courses = extractor.parseCourses(result ?: "")
                
                if (courses.isEmpty()) {
                    Log.w(TAG, "⚠️ 未提取到课程数据")
                    safeResume {
                        continuation.resume(
                            Result.failure(Exception("未提取到课程数据，可能登录失败或页面结构变化"))
                        )
                    }
                } else {
                    Log.d(TAG, "✅ 成功提取 ${courses.size} 门课程")
                    safeResume {
                        continuation.resume(Result.success(Pair(courses, cookies)))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 解析课程数据失败", e)
                safeResume {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }
}

