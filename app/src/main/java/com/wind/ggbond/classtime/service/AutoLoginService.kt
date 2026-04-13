package com.wind.ggbond.classtime.service

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.service.helper.LoginScriptGenerator
import com.wind.ggbond.classtime.util.AutoLoginResultCode
import com.wind.ggbond.classtime.util.MutableContextWrapper
import com.wind.ggbond.classtime.util.SecureCookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.UrlUtils
import javax.inject.Inject

class AutoLoginService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val schoolRepository: SchoolRepository,
    private val secureCookieManager: SecureCookieManager,
    private val loginScriptGenerator: LoginScriptGenerator
) {
    companion object {
        private const val TAG = "AutoLoginService"
        private const val LOGIN_TIMEOUT_MS = 30_000L
        private const val MAX_SSL_RETRIES = 3
    }

    private fun loginResult(code: String, message: String, success: Boolean = code == AutoLoginResultCode.OK) =
        AutoLoginResult(success, code, message)

    suspend fun performAutoLogin(schoolId: String, username: String, password: String): AutoLoginResult {
        return try {
            val school = withContext(Dispatchers.IO) { schoolRepository.getSchoolById(schoolId) }
                ?: return loginResult(AutoLoginResultCode.UNKNOWN_ERROR, "学校信息不存在")
            val loginUrl = school.loginUrl?.takeIf { it.isNotBlank() }
                ?: return loginResult(AutoLoginResultCode.UNKNOWN_ERROR, "登录URL不存在")
            val secureLoginUrl = if (loginUrl.startsWith("http://")) {
                AppLogger.w(TAG, "登录URL使用不安全的HTTP协议，已自动升级为HTTPS: $loginUrl")
                loginUrl.replace("http://", "https://")
            } else {
                loginUrl
            }
            withContext(Dispatchers.Main) {
                performLoginViaWebView(secureLoginUrl, username, password)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动登录异常", e)
            loginResult(AutoLoginResultCode.UNKNOWN_ERROR, "自动登录异常: ${e.message}")
        }
    }

    private suspend fun performLoginViaWebView(loginUrl: String, username: String, password: String): AutoLoginResult =
        suspendCancellableCoroutine { cont ->
            val webViewContext = MutableContextWrapper(context.applicationContext)
            val wv = WebView(webViewContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.savePassword = false
            }
            WebView.setWebContentsDebuggingEnabled(false)

            CookieManager.getInstance().setAcceptCookie(true)

            var completed = false
            var isFirstLoad = true
            var formSubmitted = false
            var sslRetryCount = 0

            cont.invokeOnCancellation { if (!completed) { completed = true; wv.post { cleanup(wv) } } }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (completed) return

                    if (isFirstLoad) {
                        isFirstLoad = false
                        view?.postDelayed({
                            if (completed) return@postDelayed
                            val script = loginScriptGenerator.generateAutoLoginScript(username, password)
                            view.evaluateJavascript(script) { result ->
                                if (completed) return@evaluateJavascript
                                val r = result ?: "{}"
                                when {
                                    r.contains("\"needsCaptcha\":true") -> finishLogin(cont, wv, { completed = true },
                                        loginResult(AutoLoginResultCode.NEED_CAPTCHA, "需要在设置页完成验证码和登录"))
                                    r.contains("\"submitted\":true") -> formSubmitted = true
                                    else -> finishLogin(cont, wv, { completed = true },
                                        loginResult(AutoLoginResultCode.LOGIN_FAIL, "登录页面结构不匹配，请检查学校配置"))
                                }
                            }
                        }, 1000)
                    } else if (formSubmitted) {
                        val isLoginPage = url?.contains("login", ignoreCase = true) == true ||
                            url?.contains("cas", ignoreCase = true) == true
                        completed = true
                        if (!isLoginPage) {
                            saveCookiesAfterLogin(loginUrl, url)
                            finishLogin(cont, wv, { completed = true }, loginResult(AutoLoginResultCode.OK, "自动登录成功"))
                        } else {
                            cleanup(wv)
                            cont.resume(loginResult(AutoLoginResultCode.LOGIN_FAIL, "登录失败，请检查账号密码"))
                        }
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (!request.isForMainFrame || completed) return

                    val desc = error.description?.toString().orEmpty()
                    val failUrl = request.url?.toString()
                    val isSsl = desc.contains("ERR_SSL_PROTOCOL_ERROR", ignoreCase = true) ||
                        desc.contains("ERR_SSL_VERSION_OR_CIPHER_MISMATCH", ignoreCase = true)

                    if (isSsl && sslRetryCount < MAX_SSL_RETRIES && failUrl != null) {
                        sslRetryCount++
                        AppLogger.w(TAG, "SSL错误重试 ($sslRetryCount/$MAX_SSL_RETRIES)")
                        view.clearCache(true)
                        view.postDelayed({ view.loadUrl(failUrl) }, 1500L * sslRetryCount)
                        return
                    }

                    finishLogin(cont, wv, { completed = true }, loginResult(AutoLoginResultCode.NETWORK_ERROR, "网络错误: $desc"))
                }
            }

            wv.loadUrl(loginUrl)
            wv.postDelayed({
                if (!completed) finishLogin(cont, wv, { completed = true }, loginResult(AutoLoginResultCode.NETWORK_ERROR, "自动登录超时"))
            }, LOGIN_TIMEOUT_MS)
        }

    private fun <T> finishLogin(
        cont: CancellableContinuation<T>, wv: WebView,
        flag: () -> Unit, value: T
    ) { flag(); cleanup(wv); if (cont.isActive) cont.resume(value) }

    private fun cleanup(wv: WebView) {
        try {
            wv.stopLoading(); wv.loadUrl("about:blank"); wv.clearHistory()
            (wv.context as? MutableContextWrapper)?.setBaseContext(null)
            wv.destroy()
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
    }

    private fun saveCookiesAfterLogin(loginUrl: String, currentUrl: String?) {
        try {
            val cm = CookieManager.getInstance()
            val domain = extractDomain(loginUrl)
            val merged = mergeCookies(cm.getCookie(loginUrl).orEmpty(), currentUrl?.let { cm.getCookie(it).orEmpty() }.orEmpty())
            if (merged.isNotEmpty()) secureCookieManager.saveCookies(domain, merged)
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
    }

    private fun mergeCookies(c1: String, c2: String): String {
        val map = mutableMapOf<String, String>()
        listOf(c1, c2).flatMap { it.split(";") }.forEach { cookie ->
            val trimmed = cookie.trim()
            val name = trimmed.substringBefore("=").trim()
            if (name.isNotEmpty()) map[name] = trimmed
        }
        return map.values.joinToString("; ")
    }

    private fun extractDomain(url: String): String = UrlUtils.extractDomain(url)
}

data class AutoLoginResult(val success: Boolean, val resultCode: String, val message: String)
