package com.wind.ggbond.classtime.service

import android.content.Context
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
import com.wind.ggbond.classtime.util.MutableContextWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BackgroundWebViewFetchService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
) {
    companion object {
        private const val TAG = "BgWebViewFetch"
        private const val TIMEOUT_MS = 30_000L
        private const val JS_DELAY_MS = 2_000L
        private const val MAX_SSL_RETRIES = 3
    }

    suspend fun fetchScheduleWithWebView(config: SchoolConfig, cookies: String): Result<List<ParsedCourse>> =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            var wv: WebView? = null
            var timeoutHandler: Handler? = null
            val done = AtomicBoolean(false)

            fun finish(block: () -> Unit) {
                if (done.compareAndSet(false, true)) {
                    cleanup(wv, timeoutHandler)
                    try { block() } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
                }
            }

            handler.post {
                try {
                    val ctx = MutableContextWrapper(context.applicationContext)
                    wv = createConfiguredWebView(ctx, config, cookies, handler, done, { block -> finish(block) }, cont)
                    timeoutHandler = Handler(Looper.getMainLooper())
                    timeoutHandler?.postDelayed({ finish { cont.resumeWithException(Exception("页面加载超时")) } }, TIMEOUT_MS)
                    val secureLoginUrl = if (config.loginUrl.startsWith("http://")) config.loginUrl.replace("http://", "https://") else config.loginUrl
                    wv?.loadUrl(secureLoginUrl)
                } catch (e: Exception) { finish { cont.resumeWithException(e) } }
            }

            cont.invokeOnCancellation { done.set(true); cleanup(wv, timeoutHandler) }
        }

    suspend fun fetchScheduleWithAutoLogin(config: SchoolConfig, username: String, password: String): Result<Pair<List<ParsedCourse>, String>> =
        Result.failure(Exception("请使用Cookie方式登录"))

    private fun createConfiguredWebView(
        ctx: MutableContextWrapper,
        config: SchoolConfig,
        cookies: String,
        handler: Handler,
        done: AtomicBoolean,
        finish: (() -> Unit) -> Unit,
        cont: kotlinx.coroutines.CancellableContinuation<Result<List<ParsedCourse>>>
    ): WebView {
        return WebView(ctx).apply {
            layout(0, 0, 1, 1)
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
                allowFileAccess = false; allowContentAccess = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                setSupportZoom(false); blockNetworkImage = true
                @Suppress("DEPRECATION")
                savePassword = false
            }
            WebView.setWebContentsDebuggingEnabled(false)
            applyCookies(config.loginUrl, cookies)
            webViewClient = createWebViewClient(handler, done, finish, cont, config)
        }
    }

    private fun createWebViewClient(
        handler: Handler,
        done: AtomicBoolean,
        finish: (() -> Unit) -> Unit,
        cont: kotlinx.coroutines.CancellableContinuation<Result<List<ParsedCourse>>>,
        config: SchoolConfig
    ): WebViewClient {
        return object : WebViewClient() {
            private val pageDone = AtomicBoolean(false)
            private var sslRetryCount = 0

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!pageDone.compareAndSet(false, true) || done.get()) return
                scheduleExtraction(view, handler, done, finish, cont, config)
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                super.onReceivedError(view, req, err)
                if (!req.isForMainFrame) return

                val desc = err.description?.toString().orEmpty()
                val failUrl = req.url?.toString()
                val isSsl = desc.contains("ERR_SSL_PROTOCOL_ERROR", ignoreCase = true) ||
                    desc.contains("ERR_SSL_VERSION_OR_CIPHER_MISMATCH", ignoreCase = true)

                if (isSsl && sslRetryCount < MAX_SSL_RETRIES && failUrl != null) {
                    sslRetryCount++
                    view.clearCache(true)
                    view.postDelayed({ view.loadUrl(failUrl) }, 1500L * sslRetryCount)
                    return
                }
                finish { cont.resumeWithException(Exception("页面加载失败: $desc")) }
            }
        }
    }

    private fun scheduleExtraction(
        view: WebView?,
        handler: Handler,
        done: AtomicBoolean,
        finish: (() -> Unit) -> Unit,
        cont: kotlinx.coroutines.CancellableContinuation<Result<List<ParsedCourse>>>,
        config: SchoolConfig
    ) {
        handler.postDelayed({
            if (done.get()) return@postDelayed
            val extractor = extractorFactory.getExtractor(config.id)
            val script = extractor?.generateExtractionScript() ?: defaultScript()
            view?.evaluateJavascript(script) { result ->
                if (done.get()) return@evaluateJavascript
                try {
                    val courses = extractor?.parseCourses(result ?: "") ?: emptyList()
                    finish {
                        if (courses.isNotEmpty()) cont.resume(Result.success(courses)) { }
                        else cont.resumeWithException(Exception("未提取到课程数据，Cookie可能已失效"))
                    }
                } catch (e: Exception) {
                    finish { cont.resumeWithException(e) }
                }
            }
        }, JS_DELAY_MS)
    }

    private fun WebView.applyCookies(loginUrl: String, cookies: String) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true); cm.removeAllCookies(null)
        cookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { cm.setCookie(loginUrl, it) }
        cm.flush()
    }

    private fun cleanup(wv: WebView?, th: Handler?) {
        try {
            th?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            AppLogger.e("Safety", "操作异常", e)
        }

        val destroy: () -> Unit = {
            try {
                wv?.apply {
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
            } catch (e: Exception) {
                AppLogger.e("Safety", "操作异常", e)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) destroy()
        else Handler(Looper.getMainLooper()).post(destroy)
    }

    private fun defaultScript(): String = """
        (function(){
            try{
                const c=document.getElementById('kbgrid_table_0')||document.querySelector('[id*="kb"]');
                return JSON.stringify({success:!!c,courses:[]});
            }catch(e){return JSON.stringify({success:false,error:e.message});}
        })();""".trimIndent()
}
