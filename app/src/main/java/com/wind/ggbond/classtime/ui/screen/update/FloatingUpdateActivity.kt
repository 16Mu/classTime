package com.wind.ggbond.classtime.ui.screen.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.service.AutoLoginResult
import com.wind.ggbond.classtime.service.CookieAutoUpdateService
import com.wind.ggbond.classtime.ui.theme.CourseScheduleTheme
import com.wind.ggbond.classtime.util.SecureCookieManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 悬浮窗自动更新Activity
 * 
 * Debug版本：显示可视化WebView调试界面
 * Release版本：1x1像素微小窗口，静默更新
 */
@AndroidEntryPoint
class FloatingUpdateActivity : ComponentActivity() {
    
    @Inject
    lateinit var scheduleRepository: ScheduleRepository
    
    @Inject
    lateinit var schoolRepository: SchoolRepository
    
    @Inject
    lateinit var logRepository: AutoUpdateLogRepository
    
    @Inject
    lateinit var secureCookieManager: SecureCookieManager
    
    @Inject
    lateinit var extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory
    
    @Inject
    lateinit var cookieAutoUpdateService: CookieAutoUpdateService
    
    companion object {
        private const val TAG = "FloatingUpdate"
        private const val EXTRA_FORCE_SILENT = "force_silent"
        
        /**
         * 启动更新Activity
         * @param context Context
         * @param forceSilent 是否强制静默模式（1x1像素），默认false会根据BuildConfig.DEBUG判断
         */
        fun start(context: Context, forceSilent: Boolean = false) {
            val intent = Intent(context, FloatingUpdateActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra(EXTRA_FORCE_SILENT, forceSilent)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 读取是否强制静默模式
        val forceSilent = intent.getBooleanExtra(EXTRA_FORCE_SILENT, false)
        val useSilentMode = !com.wind.ggbond.classtime.BuildConfig.DEBUG || forceSilent
        
        if (useSilentMode) {
            // ✅ 静默模式：1x1像素微小窗口（Release版本 或 强制静默）
            window.apply {
                setLayout(1, 1)
                setGravity(Gravity.TOP or Gravity.START)
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            
            setContent {
                CourseScheduleTheme {
                    SilentUpdateDialog(
                        onClose = { finish() },
                        cookieAutoUpdateService = cookieAutoUpdateService,
                        logRepository = logRepository,
                        onResult = { success, message ->
                            sendUpdateNotification(success, message)
                            finish()
                        }
                    )
                }
            }
        } else {
            // ✅ 调试模式：全屏调试界面（仅Debug版本且未强制静默）
            window.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setDimAmount(0.7f)
            }
        
            setContent {
                CourseScheduleTheme {
                    DebugWebViewDialog(
                        onClose = { finish() },
                        extractorFactory = extractorFactory,
                        scheduleRepository = scheduleRepository,
                        schoolRepository = schoolRepository
                    )
                }
            }
        }
    }
}

/**
 * 调试用的WebView对话框 - Debug版本
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugWebViewDialog(
    onClose: () -> Unit,
    extractorFactory: com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory,
    scheduleRepository: ScheduleRepository,
    schoolRepository: SchoolRepository
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    var statusText by remember { mutableStateOf("正在初始化...") }
    var currentUrl by remember { mutableStateOf("about:blank") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var schoolId by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        try {
            val schedule = scheduleRepository.getCurrentSchedule()
            if (schedule == null) {
                statusText = "❌ 未找到当前课表"
                return@LaunchedEffect
            }
            
            val sid = schedule.schoolName ?: ""
            if (sid.isEmpty()) {
                statusText = "❌ 课表缺少学校配置"
                return@LaunchedEffect
            }
            
            schoolId = sid
            val school = schoolRepository.getSchoolById(sid)
            if (school == null) {
                statusText = "❌ 未找到学校: $schoolId"
                return@LaunchedEffect
            }
            
            val cookieMgr = (context as FloatingUpdateActivity).secureCookieManager
            val cookies = cookieMgr.getCookies(sid)
            if (cookies.isNullOrEmpty()) {
                statusText = "❌ 无Cookie，请先手动登录"
                return@LaunchedEffect
            }
            
            statusText = "✅ 学校: ${school.name}\n📍 登录URL: ${school.loginUrl}\n🍪 Cookie已设置"
            
            webView?.let { wv ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                
                cookies.split(";").forEach { cookie ->
                    cookieManager.setCookie(school.loginUrl, cookie.trim())
                }
                cookieManager.flush()
                
                Log.d("DebugWebView", "加载登录页面: ${school.loginUrl}")
                Log.d("DebugWebView", "Cookie长度: ${cookies.length}字符")
                
                wv.loadUrl(school.loginUrl)
            }
            
        } catch (e: Exception) {
            statusText = "❌ 错误: ${e.message}"
            Log.e("DebugWebView", "初始化失败", e)
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔍 调试模式 - WebView加载",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (currentUrl != "about:blank") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "当前URL: $currentUrl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        }
                        
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                                Log.d("WebViewConsole", "${consoleMessage.message()} -- Line ${consoleMessage.lineNumber()}")
                                return true
                            }
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                currentUrl = url ?: "unknown"
                                Log.d("DebugWebView", "📄 开始加载: $url")
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("DebugWebView", "✅ 加载完成: $url")
                                
                                view?.postDelayed({
                                    Log.d("DebugWebView", "🔘 尝试触发课表查询...")
                                    view.evaluateJavascript("""
                                        (function() {
                                            try {
                                                console.log('🔘 查找查询按钮...');
                                                var searchButton = document.getElementById('search_go');
                                                if (searchButton) {
                                                    console.log('✅ 找到查询按钮，点击');
                                                    searchButton.click();
                                                    return JSON.stringify({success: true});
                                                } else {
                                                    console.log('⚠️ 未找到查询按钮');
                                                    return JSON.stringify({success: false});
                                                }
                                            } catch (e) {
                                                console.error('❌ 点击失败:', e);
                                                return JSON.stringify({error: e.message});
                                            }
                                        })();
                                    """.trimIndent()) { clickResult ->
                                        Log.d("DebugWebView", "🔘 查询按钮点击结果: $clickResult")
                                    }
                                }, 1000)
                                
                                view?.postDelayed({
                                    val extractor = extractorFactory.getExtractor(schoolId)
                                    
                                    if (extractor != null) {
                                        Log.d("DebugWebView", "✅ 使用专用提取器: ${extractor.schoolName}")
                                        val extractScript = extractor.generateExtractionScript()
                                        
                                        view.evaluateJavascript(extractScript) { result ->
                                            Log.d("DebugWebView", "📊 提取结果: ${result?.take(500)}")
                                            
                                            scope.launch {
                                                try {
                                                    val courses = extractor.parseCourses(result ?: "")
                                                    statusText = """
                                                        ✅ 页面已加载
                                                        📚 使用提取器: ${extractor.schoolName}
                                                        📊 提取到课程数: ${courses.size}
                                                        
                                                        美颜输出：
                                                        ${result?.take(500)}
                                        """.trimIndent()
                                                    
                                                    Log.d("DebugWebView", "✅ 成功提取 ${courses.size} 门课程")
                                                    courses.take(3).forEach { course ->
                                                        Log.d("DebugWebView", "  - ${course.courseName} (${course.teacher})")
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("DebugWebView", "解析失败", e)
                                                    statusText = "❌ 解析失败: ${e.message}"
                                                }
                                            }
                                        }
                                    }
                                }, 4000)
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                Log.e("DebugWebView", "❌ 加载错误: $description")
                                statusText = "❌ 加载错误: $description"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * 静默更新对话框 - Release版本
 * 
 * ✅ 支持自动登录流程：
 * - 检查自动登录是否启用
 * - 如果启用且有账号，先执行自动登录
 * - 登录成功后再执行课表更新
 * - 如果需要验证码，记录失败并通知用户
 * 
 * ✅ 使用CookieAutoUpdateService执行完整更新：
 * - 对比本地课表
 * - 检测调课
 * - 更新数据库
 * - 记录日志
 * 
 * ✅ 修复：完全隐藏UI，不显示任何内容
 */
@Composable
fun SilentUpdateDialog(
    onClose: () -> Unit,
    cookieAutoUpdateService: CookieAutoUpdateService,
    logRepository: AutoUpdateLogRepository,
    onResult: (success: Boolean, message: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(Unit) {
        try {
            Log.d("SilentUpdate", "🚀 开始执行课表更新（静默模式）")
            
            val autoLoginManager = com.wind.ggbond.classtime.util.AutoLoginManager(context)
            
            // 检查自动登录是否启用
            if (autoLoginManager.isAutoLoginEnabled()) {
                Log.d("SilentUpdate", "自动登录已启用，检查账号...")
                
                if (!autoLoginManager.hasCredentials()) {
                    // 未配置账号
                    Log.w("SilentUpdate", "未配置自动登录账号")
                    autoLoginManager.saveLastUpdateResult(
                        com.wind.ggbond.classtime.util.AutoLoginResultCode.NO_CREDENTIAL,
                        com.wind.ggbond.classtime.util.AutoLoginResultMessages.getMessage(
                            com.wind.ggbond.classtime.util.AutoLoginResultCode.NO_CREDENTIAL
                        )
                    )
                    onResult(false, "未配置自动登录账号")
                    return@LaunchedEffect
                }
                
                // 执行自动登录
                Log.d("SilentUpdate", "开始自动登录...")
                val loginStartTime = System.currentTimeMillis()
                val loginResult = performAutoLogin(context, autoLoginManager)
                val loginDuration = System.currentTimeMillis() - loginStartTime
                
                if (!loginResult.success) {
                    // 登录失败或需要验证码
                    Log.w("SilentUpdate", "自动登录失败: ${loginResult.message}")
                    autoLoginManager.saveLastUpdateResult(loginResult.resultCode, loginResult.message)
                    onResult(false, loginResult.message)
                    return@LaunchedEffect
                }
                
                Log.d("SilentUpdate", "自动登录成功，继续执行课表更新...")
            }
            
            // ✅ 使用CookieAutoUpdateService执行完整更新流程
            val (success, message) = cookieAutoUpdateService.performUpdate()
            
            Log.d("SilentUpdate", "更新结果: ${if (success) "成功" else "失败"} - $message")
            
            // 保存更新结果
            if (autoLoginManager.isAutoLoginEnabled()) {
                autoLoginManager.saveLastUpdateResult(
                    if (success) com.wind.ggbond.classtime.util.AutoLoginResultCode.OK 
                    else com.wind.ggbond.classtime.util.AutoLoginResultCode.UNKNOWN_ERROR,
                    message
                )
            }
            
            // ✅ 记录日志
            logRepository.logUpdate(
                triggerEvent = "自动更新",
                result = if (success) 
                    com.wind.ggbond.classtime.data.local.entity.UpdateResult.SUCCESS 
                else 
                    com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
                successMessage = if (success) message else null,
                failureReason = if (!success) message else null,
                durationMs = 0
            )
            
            onResult(success, message)
            
        } catch (e: Exception) {
            Log.e("SilentUpdate", "更新失败", e)
            
            logRepository.logUpdate(
                triggerEvent = "自动更新",
                result = com.wind.ggbond.classtime.data.local.entity.UpdateResult.FAILED,
                failureReason = "异常: ${e.message}",
                durationMs = 0
            )
            
            onResult(false, "更新失败: ${e.message}")
        }
    }
    
    // ✅ 修复：完全隐藏UI，使用透明且不可见的Box
    Box(
        modifier = Modifier
            .size(0.dp)
            .alpha(0f)
    )
}

/**
 * 执行自动登录流程
 */
private suspend fun performAutoLogin(
    context: Context,
    autoLoginManager: com.wind.ggbond.classtime.util.AutoLoginManager
): AutoLoginResult {
    return try {
        val username = autoLoginManager.getUsername() ?: ""
        val password = autoLoginManager.getPassword() ?: ""
        
        if (username.isBlank() || password.isBlank()) {
            return AutoLoginResult(
                success = false,
                resultCode = com.wind.ggbond.classtime.util.AutoLoginResultCode.NO_CREDENTIAL,
                message = com.wind.ggbond.classtime.util.AutoLoginResultMessages.getMessage(
                    com.wind.ggbond.classtime.util.AutoLoginResultCode.NO_CREDENTIAL
                )
            )
        }
        
        // 获取当前学校ID
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val schoolId = prefs.getString("current_school_id", null)
        
        if (schoolId.isNullOrBlank()) {
            Log.w("AutoLogin", "未找到当前学校ID")
            return AutoLoginResult(
                success = false,
                resultCode = com.wind.ggbond.classtime.util.AutoLoginResultCode.UNKNOWN_ERROR,
                message = "未找到当前学校信息"
            )
        }
        
        Log.d("AutoLogin", "开始自动登录: $schoolId - $username")
        
        // 使用 AutoLoginService 执行自动登录
        // 注意：这里需要通过 Hilt 注入 SchoolRepository
        // 临时方案：直接创建服务实例（后续可改为注入）
        val schoolRepository = (context as? FloatingUpdateActivity)?.schoolRepository
        
        if (schoolRepository == null) {
            Log.w("AutoLogin", "无法获取 SchoolRepository")
            return AutoLoginResult(
                success = false,
                resultCode = com.wind.ggbond.classtime.util.AutoLoginResultCode.UNKNOWN_ERROR,
                message = "系统错误：无法获取学校信息"
            )
        }
        
        // 获取SecureCookieManager用于登录后保存Cookie
        val secureCookieManager = (context as? FloatingUpdateActivity)?.secureCookieManager
        
        if (secureCookieManager == null) {
            Log.w("AutoLogin", "无法获取 SecureCookieManager")
            return AutoLoginResult(
                success = false,
                resultCode = com.wind.ggbond.classtime.util.AutoLoginResultCode.UNKNOWN_ERROR,
                message = "系统错误：无法获取Cookie管理器"
            )
        }
        
        val autoLoginService = com.wind.ggbond.classtime.service.AutoLoginService(
            context,
            schoolRepository,
            secureCookieManager
        )
        
        val result = autoLoginService.performAutoLogin(schoolId, username, password)
        
        Log.d("AutoLogin", "自动登录结果: ${result.resultCode} - ${result.message}")
        
        result
    } catch (e: Exception) {
        Log.e("AutoLogin", "自动登录异常", e)
        AutoLoginResult(
            success = false,
            resultCode = com.wind.ggbond.classtime.util.AutoLoginResultCode.UNKNOWN_ERROR,
            message = "自动登录异常: ${e.message}"
        )
    }
}

/**
 * 发送更新通知
 * 
 * 根据自动登录结果码决定是否发送通知：
 * - 成功：不发送通知（或发送静默通知）
 * - 需要验证码：发送错误通知，引导用户去设置页
 * - 登录失败/其他错误：发送错误通知
 */
fun FloatingUpdateActivity.sendUpdateNotification(success: Boolean, message: String) {
    try {
        val autoLoginManager = com.wind.ggbond.classtime.util.AutoLoginManager(this)
        
        // 只有在启用了自动登录时才发送自动登录相关的通知
        if (autoLoginManager.isAutoLoginEnabled()) {
            val resultCode = autoLoginManager.getLastUpdateResultCode() ?: ""
            
            when (resultCode) {
                com.wind.ggbond.classtime.util.AutoLoginResultCode.OK -> {
                    // 成功，可选发送成功通知
                    if (!message.contains("无课程更新")) {
                        // 只在有课程变更时发送通知
                        com.wind.ggbond.classtime.util.AutoLoginNotificationHelper
                            .sendAutoLoginSuccessNotification(this, message)
                    }
                }
                com.wind.ggbond.classtime.util.AutoLoginResultCode.NEED_CAPTCHA,
                com.wind.ggbond.classtime.util.AutoLoginResultCode.LOGIN_FAIL,
                com.wind.ggbond.classtime.util.AutoLoginResultCode.NO_CREDENTIAL,
                com.wind.ggbond.classtime.util.AutoLoginResultCode.NETWORK_ERROR -> {
                    // 失败，发送错误通知
                    com.wind.ggbond.classtime.util.AutoLoginNotificationHelper
                        .sendAutoLoginFailureNotification(this, resultCode, message)
                }
                else -> {
                    // 其他情况，发送通用失败通知
                    if (!success) {
                        com.wind.ggbond.classtime.util.CourseChangeNotificationHelper
                            .sendFailureNotification(this, message)
                    }
                }
            }
        } else {
            // 未启用自动登录，使用原有的通知逻辑
            if (success) {
                // 成功但不确定是否有变更，使用通用成功通知
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        "course_update",
                        "课表更新",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "课表更新通知"
                        enableVibration(true)
                        enableLights(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                
                val notification = NotificationCompat.Builder(this, "course_update")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("时课 课表更新成功")
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("课表已更新\n\n$message\n\n更新时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                
                notificationManager.notify(3001, notification)
            } else {
                // 失败时使用专门的失败通知
                com.wind.ggbond.classtime.util.CourseChangeNotificationHelper
                    .sendFailureNotification(this, message)
            }
        }
        
        Log.d("SilentUpdate", "通知已发送: ${if (success) "成功" else "失败"} - $message")
    } catch (e: Exception) {
        Log.e("SilentUpdate", "发送通知失败", e)
    }
}







