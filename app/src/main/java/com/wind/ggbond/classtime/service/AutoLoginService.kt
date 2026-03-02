package com.wind.ggbond.classtime.service

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.util.AutoLoginResultCode
import com.wind.ggbond.classtime.util.SecureCookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 自动登录服务
 * 
 * 负责通过 WebView 自动填充账号密码并登录
 * 登录成功后自动将Cookie保存到SecureCookieManager供后续课表获取使用
 * 通过监测页面跳转（而非JS返回值）来判断登录是否成功
 */
class AutoLoginService(
    private val context: Context,
    private val schoolRepository: SchoolRepository,
    private val secureCookieManager: SecureCookieManager // 用于保存登录后的Cookie
) {
    
    companion object {
        private const val TAG = "AutoLoginService"
        private const val LOGIN_TIMEOUT_MS = 30000L  // 30秒超时
    }
    
    /**
     * 执行自动登录
     * 
     * @param schoolId 学校ID
     * @param username 用户名
     * @param password 密码
     * @return 登录结果（成功/失败/需要验证码）
     */
    suspend fun performAutoLogin(
        schoolId: String,
        username: String,
        password: String
    ): AutoLoginResult {
        return withContext(Dispatchers.Main) {
            try {
                val school = schoolRepository.getSchoolById(schoolId)
                    ?: return@withContext AutoLoginResult(
                        success = false,
                        resultCode = AutoLoginResultCode.UNKNOWN_ERROR,
                        message = "学校信息不存在"
                    )
                
                val loginUrl = school.loginUrl
                if (loginUrl.isNullOrBlank()) {
                    return@withContext AutoLoginResult(
                        success = false,
                        resultCode = AutoLoginResultCode.UNKNOWN_ERROR,
                        message = "登录URL不存在"
                    )
                }
                
                Log.d(TAG, "开始自动登录: $schoolId - $loginUrl")
                
                // 通过 WebView 执行自动登录
                val result = performLoginViaWebView(loginUrl, username, password)
                
                Log.d(TAG, "自动登录结果: ${result.resultCode} - ${result.message}")
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "自动登录异常", e)
                AutoLoginResult(
                    success = false,
                    resultCode = AutoLoginResultCode.UNKNOWN_ERROR,
                    message = "自动登录异常: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 通过 WebView 执行自动登录
     * 
     * 核心流程：
     * 1. 加载登录页面（首次onPageFinished）→ 注入JS脚本填充表单并点击登录
     * 2. 等待页面跳转（第二次onPageFinished）→ 判断是否跳转到非登录页面
     * 3. 跳转成功 → 提取Cookie保存到SecureCookieManager → 返回登录成功
     * 4. 仍在登录页 → 返回登录失败
     */
    private suspend fun performLoginViaWebView(
        loginUrl: String,
        username: String,
        password: String
    ): AutoLoginResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                // 创建临时WebView用于登录
                val webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                    }
                }
                
                // 统一的WebView清理方法，防止内存泄漏
                val cleanupWebView = {
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.clearHistory()
                        webView.destroy()
                        Log.d(TAG, "WebView 已清理")
                    } catch (e: Exception) {
                        Log.e(TAG, "清理WebView失败", e)
                    }
                }
                
                // 确保CookieManager接受Cookie
                CookieManager.getInstance().setAcceptCookie(true)
                
                // 登录状态标记
                var loginCompleted = false
                // 是否为首次页面加载（即登录页本身）
                var isFirstPageLoad = true
                // 是否已提交表单（JS脚本执行完毕）
                var formSubmitted = false
                
                // 协程取消时清理WebView
                continuation.invokeOnCancellation {
                    if (!loginCompleted) {
                        loginCompleted = true
                        webView.post { cleanupWebView() }
                    }
                }
                
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // 已完成则忽略后续回调
                        if (loginCompleted) return
                        
                        Log.d(TAG, "页面加载完成: $url (首次加载=$isFirstPageLoad, 已提交=$formSubmitted)")
                        
                        if (isFirstPageLoad) {
                            // ========== 首次加载：这是登录页面，注入自动登录脚本 ==========
                            isFirstPageLoad = false
                            
                            // 延迟1秒确保DOM完全加载后再注入脚本
                            view?.postDelayed({
                                if (loginCompleted) return@postDelayed
                                
                                val autoLoginScript = generateAutoLoginScript(username, password)
                                view.evaluateJavascript(autoLoginScript) { result ->
                                    Log.d(TAG, "自动登录脚本执行结果: $result")
                                    
                                    if (loginCompleted) return@evaluateJavascript
                                    
                                    val resultStr = result ?: "{}"
                                    
                                    when {
                                        // 检测到验证码 → 直接返回失败
                                        resultStr.contains("\"needsCaptcha\":true") -> {
                                            Log.w(TAG, "检测到需要验证码")
                                            loginCompleted = true
                                            cleanupWebView()
                                            continuation.resume(
                                                AutoLoginResult(
                                                    success = false,
                                                    resultCode = AutoLoginResultCode.NEED_CAPTCHA,
                                                    message = "需要在设置页完成验证码和登录"
                                                )
                                            )
                                        }
                                        // 表单已提交 → 等待页面跳转（下次onPageFinished判断结果）
                                        resultStr.contains("\"submitted\":true") -> {
                                            Log.d(TAG, "表单已提交，等待页面跳转...")
                                            formSubmitted = true
                                        }
                                        // 未找到登录表单元素 → 返回失败
                                        else -> {
                                            Log.w(TAG, "未找到登录表单元素")
                                            loginCompleted = true
                                            cleanupWebView()
                                            continuation.resume(
                                                AutoLoginResult(
                                                    success = false,
                                                    resultCode = AutoLoginResultCode.LOGIN_FAIL,
                                                    message = "登录页面结构不匹配，请检查学校配置"
                                                )
                                            )
                                        }
                                    }
                                }
                            }, 1000)
                            
                        } else if (formSubmitted) {
                            // ========== 第二次加载：表单提交后的页面跳转结果 ==========
                            // 判断是否仍然在登录页面
                            val isLoginPage = url?.contains("login", ignoreCase = true) == true ||
                                              url?.contains("cas", ignoreCase = true) == true
                            
                            if (!isLoginPage) {
                                // 已跳转到非登录页面 → 登录成功
                                Log.d(TAG, "登录成功，已跳转到: $url")
                                loginCompleted = true
                                
                                // 从CookieManager提取新Cookie并保存到SecureCookieManager
                                saveCookiesAfterLogin(loginUrl, url)
                                
                                cleanupWebView()
                                continuation.resume(
                                    AutoLoginResult(
                                        success = true,
                                        resultCode = AutoLoginResultCode.OK,
                                        message = "自动登录成功"
                                    )
                                )
                            } else {
                                // 仍在登录页面 → 账号密码错误或其他原因
                                Log.w(TAG, "登录失败，仍在登录页面: $url")
                                loginCompleted = true
                                cleanupWebView()
                                continuation.resume(
                                    AutoLoginResult(
                                        success = false,
                                        resultCode = AutoLoginResultCode.LOGIN_FAIL,
                                        message = "登录失败，请检查账号密码"
                                    )
                                )
                            }
                        }
                    }
                    
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        
                        if (loginCompleted) return
                        
                        Log.e(TAG, "WebView加载错误: $description")
                        loginCompleted = true
                        
                        cleanupWebView()
                        continuation.resume(
                            AutoLoginResult(
                                success = false,
                                resultCode = AutoLoginResultCode.NETWORK_ERROR,
                                message = "网络错误: $description"
                            )
                        )
                    }
                }
                
                // 加载登录页面
                Log.d(TAG, "加载登录页面: $loginUrl")
                webView.loadUrl(loginUrl)
                
                // 设置超时（30秒内未完成则视为超时）
                webView.postDelayed({
                    if (!loginCompleted) {
                        Log.w(TAG, "自动登录超时")
                        loginCompleted = true
                        
                        cleanupWebView()
                        continuation.resume(
                            AutoLoginResult(
                                success = false,
                                resultCode = AutoLoginResultCode.NETWORK_ERROR,
                                message = "自动登录超时"
                            )
                        )
                    }
                }, LOGIN_TIMEOUT_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "WebView初始化异常", e)
                continuation.resume(
                    AutoLoginResult(
                        success = false,
                        resultCode = AutoLoginResultCode.UNKNOWN_ERROR,
                        message = "WebView初始化异常: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * 登录成功后从CookieManager提取Cookie并保存到SecureCookieManager
     * 
     * @param loginUrl 登录页面URL（用于提取域名作为Cookie存储的key）
     * @param currentUrl 当前页面URL（登录后跳转的页面，可能携带额外Cookie）
     */
    private fun saveCookiesAfterLogin(loginUrl: String, currentUrl: String?) {
        try {
            val cookieManager = CookieManager.getInstance()
            val domain = extractDomain(loginUrl)
            
            // 从登录域名获取Cookie
            val loginCookies = cookieManager.getCookie(loginUrl) ?: ""
            // 从跳转后的页面获取Cookie（可能包含额外的会话Cookie）
            val currentCookies = if (!currentUrl.isNullOrBlank()) {
                cookieManager.getCookie(currentUrl) ?: ""
            } else {
                ""
            }
            
            // 合并Cookie（去重）
            val allCookies = mergeCookies(loginCookies, currentCookies)
            
            if (allCookies.isNotEmpty()) {
                // 保存到SecureCookieManager供后续课表获取使用
                secureCookieManager.saveCookies(domain, allCookies)
                Log.d(TAG, "登录Cookie已保存到SecureCookieManager: domain=$domain")
            } else {
                Log.w(TAG, "登录后未获取到Cookie")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存登录Cookie失败", e)
        }
    }
    
    /**
     * 合并两组Cookie字符串，按name去重（后者优先）
     */
    private fun mergeCookies(cookies1: String, cookies2: String): String {
        // 解析Cookie为 name=value 的Map
        val cookieMap = mutableMapOf<String, String>()
        
        // 先加入第一组
        cookies1.split(";").forEach { cookie ->
            val trimmed = cookie.trim()
            val name = trimmed.substringBefore("=").trim()
            if (name.isNotEmpty()) {
                cookieMap[name] = trimmed
            }
        }
        
        // 再加入第二组（覆盖同名Cookie）
        cookies2.split(";").forEach { cookie ->
            val trimmed = cookie.trim()
            val name = trimmed.substringBefore("=").trim()
            if (name.isNotEmpty()) {
                cookieMap[name] = trimmed
            }
        }
        
        return cookieMap.values.joinToString("; ")
    }
    
    /**
     * 从URL中提取域名
     */
    private fun extractDomain(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * 生成自动登录脚本
     * 
     * 基于你提供的HTML结构，自动填充账号密码并登录
     */
    private fun generateAutoLoginScript(username: String, password: String): String {
        // 转义特殊字符（覆盖所有JS危险字符，防止注入）
        val escapedUsername = escapeForJavaScript(username)
        val escapedPassword = escapeForJavaScript(password)
        
        return """
            (function() {
                try {
                    // 1. 检查是否是登录页面
                    var form = document.getElementById('fm1');
                    if (!form) {
                        console.log('❌ 未找到登录表单');
                        return JSON.stringify({success: false});
                    }
                    
                    // 2. 检查是否出现验证码
                    var kaptcha = document.getElementById('kaptcha');
                    if (kaptcha && kaptcha.style.display !== 'none') {
                        console.log('⚠️ 检测到验证码');
                        return JSON.stringify({needsCaptcha: true});
                    }
                    
                    // 3. 填充账号
                    var userInput = document.getElementById('username');
                    if (userInput) {
                        userInput.value = '$escapedUsername';
                        userInput.dispatchEvent(new Event('input', { bubbles: true }));
                        userInput.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('✅ 账号已填充');
                    }
                    
                    // 4. 填充密码（可见框）
                    var ppassword = document.getElementById('ppassword');
                    if (ppassword) {
                        ppassword.value = '$escapedPassword';
                        ppassword.dispatchEvent(new Event('input', { bubbles: true }));
                        ppassword.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('✅ 密码框已填充');
                    }
                    
                    // 5. 填充密码（隐藏框）
                    var password = document.getElementById('password');
                    if (password) {
                        password.value = '$escapedPassword';
                        password.dispatchEvent(new Event('input', { bubbles: true }));
                        password.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('✅ 隐藏密码框已填充');
                    }
                    
                    // 6. 勾选协议（如存在）
                    var cjgzs = document.getElementById('cjgzsType');
                    if (cjgzs) {
                        cjgzs.checked = true;
                        console.log('✅ 协议已勾选');
                    }
                    
                    // 7. 点击登录按钮（直接点击，由WebViewClient监测页面跳转判断结果）
                    var loginBtn = document.getElementById('dl');
                    if (loginBtn) {
                        console.log('准备点击登录按钮');
                        loginBtn.click();
                        console.log('登录按钮已点击，等待页面跳转');
                        return JSON.stringify({submitted: true});
                    } else {
                        console.log('未找到登录按钮');
                        return JSON.stringify({submitted: false});
                    }
                    
                } catch (e) {
                    console.error('❌ 自动登录脚本异常:', e);
                    return JSON.stringify({error: e.message});
                }
            })();
        """.trimIndent()
    }
    
    /**
     * 将字符串转义为安全的JavaScript单引号字符串内容
     * 覆盖：反斜杠、单引号、双引号、换行符、回车符、制表符、反引号、美元符号
     */
    private fun escapeForJavaScript(input: String): String {
        return input
            .replace("\\", "\\\\")     // 反斜杠必须最先转义
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("`", "\\`")       // 反引号（模板字符串）
            .replace("\$", "\\\$")     // 美元符号（模板字符串）
    }
}

/**
 * 自动登录结果
 */
data class AutoLoginResult(
    val success: Boolean,
    val resultCode: String,
    val message: String
)
