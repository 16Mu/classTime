package com.wind.ggbond.classtime.service

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.HtmlScheduleParser
import com.wind.ggbond.classtime.util.MutableContextWrapper
import com.wind.ggbond.classtime.util.SecureCookieManager
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class UnifiedScheduleFetchService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val htmlParser: HtmlScheduleParser,
    private val secureCookieManager: SecureCookieManager,
    private val extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
) : IScheduleFetcher {
    companion object {
        private const val TAG = "UnifiedScheduleFetch"
        private const val TIMEOUT_MS = 30000L
        private const val PAGE_LOAD_WAIT_MS = 1000L
    }

    suspend override fun fetchSchedule(schoolConfig: SchoolConfig, showWebView: Boolean): Result<Pair<List<ParsedCourse>, String>> {
        val domain = extractDomain(schoolConfig.loginUrl)
        val savedCookies = secureCookieManager.getCookies(domain)
        if (savedCookies.isNullOrEmpty()) {
            AppLogger.w(TAG, "未找到保存的Cookie")
            return Result.failure(Exception("登录凭证不存在，请重新登录教务系统导入课表"))
        }

        return try {
            fetchWithCookie(schoolConfig, savedCookies)
        } catch (e: Exception) {
            AppLogger.w(TAG, "使用Cookie获取课表失败: ${e.message}")
            Result.failure(Exception("登录已过期，请重新登录教务系统导入课表"))
        }
    }

    private fun extractDomain(url: String): String = try { java.net.URL(url).host } catch (_: Exception) { url }

    private suspend fun fetchWithCookie(config: SchoolConfig, cookies: String): Result<Pair<List<ParsedCourse>, String>> =
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            var webView: WebView? = null; var timeoutHandler: Handler? = null
            val hasResumed = AtomicBoolean(false)

            fun safeResume(block: () -> Unit) {
                if (hasResumed.compareAndSet(false, true)) try {
                    mainHandler.post {
                        webView?.apply { stopLoading(); webViewClient = WebViewClient(); webChromeClient = null; removeAllViews(); destroy() }
                        webView = null
                    }
                    timeoutHandler?.removeCallbacksAndMessages(null); timeoutHandler = null
                    block()
                } catch (e: Exception) { AppLogger.e(TAG, "Resume失败", e) }
            }

            mainHandler.post {
                try {
                    AppLogger.d(TAG, "使用Cookie访问课表页面: ${config.name}")
                    val webViewContext = MutableContextWrapper(context.applicationContext)
                    webView = WebView(webViewContext).apply {
                        layout(0, 0, 1, 1)
                        settings.apply {
                            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
                            allowFileAccess = false; allowContentAccess = false
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT; blockNetworkImage = true
                        }
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true); removeAllCookies(null); flush()
                            val domain = extractDomain(config.loginUrl)
                            cookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { setCookie(domain, it) }
                            flush()
                        }

                        webViewClient = object : WebViewClient() {
                            private var pageLoadCount = 0; private var sslRetryCount = 0

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (hasResumed.get()) return
                                pageLoadCount++
                                val isLoginPage = url?.contains("login", ignoreCase = true) == true || url?.contains("cas", ignoreCase = true) == true
                                if (isLoginPage && pageLoadCount > 1) {
                                    AppLogger.w(TAG, "Cookie已失效")
                                    safeResume { continuation.resumeWithException(Exception("Cookie已失效")) }; return
                                }
                                if (!isLoginPage) {
                                    mainHandler.postDelayed({
                                        if (hasResumed.get()) return@postDelayed
                                        extractScheduleData(view, config, safeResume = { safeResume(it) }, continuation)
                                    }, PAGE_LOAD_WAIT_MS)
                                }
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                super.onReceivedError(view, request, error)
                                if (!request.isForMainFrame || hasResumed.get()) return
                                val desc = error.description?.toString().orEmpty()
                                val failingUrl = request.url?.toString()
                                val isSslError = desc.contains("ERR_SSL_PROTOCOL_ERROR", ignoreCase = true) || desc.contains("ERR_SSL_VERSION_OR_CIPHER_MISMATCH", ignoreCase = true)

                                if (isSslError && sslRetryCount < 3 && failingUrl != null) {
                                    sslRetryCount++; view.clearCache(true); view.postDelayed({ view.loadUrl(failingUrl) }, 1500L * sslRetryCount); return
                                }
                                safeResume { continuation.resumeWithException(Exception("网络错误: $desc")) }
                            }
                        }
                        loadUrl(config.loginUrl)
                    }

                    timeoutHandler = Handler(Looper.getMainLooper())
                    timeoutHandler?.postDelayed({
                        safeResume { continuation.resumeWithException(Exception("获取课表超时（${TIMEOUT_MS / 1000}秒）")) }
                    }, TIMEOUT_MS)
                    continuation.invokeOnCancellation { AppLogger.w(TAG, "操作被取消"); safeResume {} }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取课表失败", e); safeResume { continuation.resumeWithException(e) }
                }
            }
        }

    private fun extractScheduleData(webView: WebView?, config: SchoolConfig, safeResume: (() -> Unit) -> Unit, continuation: kotlinx.coroutines.CancellableContinuation<Result<Pair<List<ParsedCourse>, String>>>) {
        if (webView == null) { safeResume { continuation.resume(Result.failure(Exception("WebView已销毁"))) }; return }

        val cookies = CookieManager.getInstance().getCookie(config.scheduleUrl) ?: ""
        val extractor = extractorFactory.getExtractor(config.id)
        if (extractor == null) {
            AppLogger.e(TAG, "未找到学校 ${config.name} 的提取器")
            safeResume { continuation.resume(Result.failure(Exception("该学校暂未适配自动提取功能"))) }; return
        }

        webView.evaluateJavascript("(${extractor.generateExtractionScript()})") { result ->
            try {
                val courses = extractor.parseCourses(result ?: "")
                if (courses.isEmpty()) {
                    safeResume { continuation.resume(Result.failure(Exception("未提取到课程数据"))) }
                } else {
                    safeResume { continuation.resume(Result.success(Pair(courses, cookies))) }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析课程数据失败", e); safeResume { continuation.resume(Result.failure(e)) }
            }
        }
    }
}
