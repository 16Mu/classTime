package com.wind.ggbond.classtime.ui.screen.scheduleimport

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

/**
 * WebView 登录页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
    navController: NavController,
    schoolId: String,
    viewModel: WebViewLoginViewModel = hiltViewModel()
) {
    val school by viewModel.getSchool(schoolId).collectAsState(initial = null)
    val cookies by viewModel.cookies.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }
    
    // 如果学校数据未加载，显示加载界面
    if (school == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text("正在加载...") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在加载学校信息...")
                }
            }
        }
        return
    }
    
    val schoolEntity = school
    if (schoolEntity == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中...")
        }
        return
    }
    
    val schoolUrl = schoolEntity.loginUrl
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(schoolEntity.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    TextButton(
                        onClick = {
                            // ✅ 使用ViewModel中已收集的Cookie（加密存储）
                            viewModel.saveCookies(cookies)
                            navController.navigateUp()
                        }
                    ) {
                        Text("完成")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            
                            // ✅ 安全配置 - 防止文件访问和XSS攻击
                            allowFileAccess = false
                            allowContentAccess = false
                            // ✅ 混合内容模式 - 允许HTTP资源（教务系统需要）
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            
                            // ✅ 设置初始缩放为100%
                            setInitialScale(100)
                        }
                        
                        // ✅ 启用硬件加速，提升渲染性能
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        
                        // ✅ 设置背景色，确保WebView可见
                        setBackgroundColor(android.graphics.Color.WHITE)
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                // 自动提取Cookie
                                url?.let {
                                    val cookies = CookieManager.getInstance().getCookie(it)
                                    cookies?.let { cookie ->
                                        viewModel.updateCookies(cookie)
                                    }
                                }
                            }
                        }
                        
                        loadUrl(schoolUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { webView ->
                    // ✅ 防止内存泄漏：清理WebView
                    webView.stopLoading()
                    webView.webViewClient = WebViewClient()
                    webView.webChromeClient = null
                    webView.removeAllViews()
                    webView.destroy()
                }
            )
            
            // 加载指示器
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

