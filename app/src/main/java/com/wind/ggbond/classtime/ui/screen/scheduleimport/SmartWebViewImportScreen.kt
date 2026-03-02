package com.wind.ggbond.classtime.ui.screen.scheduleimport

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.BuildConfig
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.Screen

/**
 * 智能WebView导入页面（推荐方式）⭐
 * 
 * 功能：
 * 1. 显示WebView供用户登录
 * 2. 自动注入JavaScript提取课程数据
 * 3. 解析并导入课程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartWebViewImportScreen(
    navController: NavController,
    schoolId: String,
    viewModel: SmartImportViewModel = hiltViewModel(),
    importViewModel: ImportScheduleViewModel = hiltViewModel()  // 获取ImportScheduleViewModel以传递数据
) {
    // ✅ 修复：检查schoolId是否为空
    if (schoolId.isBlank()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text("参数错误") },
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
                    Text("学校ID无效，请重新选择学校")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.navigateUp() }) {
                        Text("返回")
                    }
                }
            }
        }
        return
    }
    
    // ✅ 修复：使用remember包装Flow，避免重复收集导致的问题
    val school by remember(schoolId) { 
        viewModel.getSchool(schoolId) 
    }.collectAsState(initial = null)
    
    val parseState by viewModel.parseState.collectAsState()
    val parsedCourses by viewModel.parsedCourses.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val importedSemesterInfo by viewModel.importedSemesterInfo.collectAsState()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var pageInspectionResult by remember { mutableStateOf<String?>(null) }
    
    // ✅ 修复：添加加载超时检测
    var loadTimeout by remember { mutableStateOf(false) }
    LaunchedEffect(schoolId) {
        kotlinx.coroutines.delay(5000) // 5秒超时
        if (school == null) {
            loadTimeout = true
        }
    }
    
    // 如果学校数据未加载，显示加载界面
    if (school == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text(if (loadTimeout) "加载失败" else "正在加载...") },
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
                    if (loadTimeout) {
                        // ✅ 超时后显示错误信息和返回按钮
                        Text("未找到学校信息: $schoolId")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.navigateUp() }) {
                            Text("返回重新选择")
                        }
                    } else {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在加载学校信息...")
                    }
                }
            }
        }
        return
    }
    
    val schoolUrl = school!!.loginUrl
    val systemType = school!!.systemType
    
    // 不自动导航，通过对话框处理导入流程
    var showScheduleDialog by remember { mutableStateOf(false) }
    val importState by importViewModel.importState.collectAsState()
    
    // 监听解析状态
    LaunchedEffect(parseState) {
        if (parseState is ParseState.Success && parsedCourses.isNotEmpty()) {
            // ⭐ 保存当前学校ID供自动更新使用
            (context.applicationContext as? com.wind.ggbond.classtime.CourseScheduleApp)?.saveCurrentSchoolId(schoolId)
            
            // 检查是否有学期
            showScheduleDialog = true
        }
    }
    
    // 监听导入状态，成功后跳转到主界面
    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            // 导入成功，跳转到主界面并强制刷新数据（使用底部Tab的课表路由，确保导航栈正确清理）
            navController.navigate(Screen.Main.createRoute(refresh = true)) {
                popUpTo(BottomNavItem.Schedule.route) {
                    inclusive = true
                }
            }
        }
    }
    
    // 学期设置对话框
    if (showScheduleDialog) {
        ScheduleSetupDialogInWebView(
            onDismiss = {
                showScheduleDialog = false
                viewModel.resetParseState()
            },
            onConfirm = { name, startDate, totalWeeks ->
                showScheduleDialog = false
                android.util.Log.d("SmartWebView", "开始创建学期并导入课程，课程数量: ${parsedCourses.size}")
                
                // ⭐ 保存学校ID供自动更新使用
                try {
                    val app = context.applicationContext as? com.wind.ggbond.classtime.CourseScheduleApp
                    app?.saveCurrentSchoolId(schoolId)
                } catch (e: Exception) {
                    android.util.Log.e("SmartWebView", "保存学校ID失败", e)
                }
                
                // 保存课程并创建学期，同时传递schoolId
                importViewModel.previewCourses(parsedCourses, schoolId)
                importViewModel.createSemesterAndImport(name, startDate, totalWeeks)
                // 重置解析状态
                viewModel.resetParseState()
            },
            fallSemesterStartDate = school?.fallSemesterStartDate,
            springSemesterStartDate = school?.springSemesterStartDate,
            importedSemesterInfo = importedSemesterInfo
        )
    }
    
    // 节次数设置对话框
    if (importState is ImportState.NeedSectionSetup) {
        val maxSection = (importState as ImportState.NeedSectionSetup).maxSection
        SectionSetupDialog(
            maxSection = maxSection,
            onDismiss = {
                importViewModel.resetState()
            },
            onConfirm = { morning, afternoon ->
                importViewModel.updateSectionCountsAndRetry(morning, afternoon)
            }
        )
    }
    
    // 错误提示对话框
    if (importState is ImportState.Error) {
        val errorMessage = (importState as ImportState.Error).message
        AlertDialog(
            onDismissRequest = {
                importViewModel.resetState()
            },
            title = { Text("导入失败") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = {
                    importViewModel.resetState()
                }) {
                    Text("确定")
                }
            }
        )
    }
    
    // 显示导入进度
    if (importState is ImportState.Loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(school!!.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // Debug模式：显示"复制页面信息"按钮
                    if (BuildConfig.DEBUG) {
                        IconButton(
                            onClick = {
                                // 每次点击都重新执行检查（延迟1秒确保DOM完全渲染）
                                Toast.makeText(context, "正在检查页面结构...", Toast.LENGTH_SHORT).show()
                                
                                // 使用延迟执行的脚本（等待2秒确保异步加载完成）
                                val delayedInspectionScript = """
                                    setTimeout(function() {
                                        ${viewModel.getInspectionScript()}
                                    }, 2000);
                                """.trimIndent()
                                
                                webView?.evaluateJavascript(delayedInspectionScript) { result ->
                                    pageInspectionResult = result?.removeSurrounding("\"")?.replace("\\n", "\n")
                                    if (pageInspectionResult != null) {
                                        copyTextToClipboard(context, pageInspectionResult!!)
                                        Toast.makeText(context, "页面信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "检查失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, "复制页面信息")
                        }
                    }
                    
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    
                    TextButton(
                        onClick = {
                            // 提取课程数据
                            webView?.let { wv ->
                                val url = wv.url ?: ""
                                if (url.contains("kb") || url.contains("schedule") || url.contains("course")) {
                                    android.util.Log.d("SmartWebView", "开始提取课表数据")
                                    isLoading = true
                                    
                                    // 注入JavaScript提取数据
                                    val extractorFactory = com.wind.ggbond.classtime.util.extractor.SchoolExtractorFactory(
                                        cqepcExtractor = com.wind.ggbond.classtime.util.extractor.CQEPCExtractor(),
                                        cqustExtractor = com.wind.ggbond.classtime.util.extractor.CQUSTExtractor(),
                                        gzgsxyExtractor = com.wind.ggbond.classtime.util.extractor.GZGSXYExtractor(),
                                        shsdExtractor = com.wind.ggbond.classtime.util.extractor.SHSDExtractor(),
                                        zykjxyExtractor = com.wind.ggbond.classtime.util.extractor.ZYKJXYExtractor(),
                                        nmgcjdxExtractor = com.wind.ggbond.classtime.util.extractor.NMGCJDXExtractor(),
                                        sqgxyExtractor = com.wind.ggbond.classtime.util.extractor.SQGXYExtractor(),
                                        sqsfxyExtractor = com.wind.ggbond.classtime.util.extractor.SQSFXYExtractor(),
                                        ahykdxExtractor = com.wind.ggbond.classtime.util.extractor.AHYKDXExtractor(),
                                        ahkjxyExtractor = com.wind.ggbond.classtime.util.extractor.AHKJXYExtractor(),
                                        sdslzyxyExtractor = com.wind.ggbond.classtime.util.extractor.SDSLZYXYExtractor(),
                                        cdxxgcdxExtractor = com.wind.ggbond.classtime.util.extractor.CDXXGCDXExtractor(),
                                        whzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.WHZYJSXYExtractor(),
                                        hnlgdxExtractor = com.wind.ggbond.classtime.util.extractor.HNLGDXExtractor(),
                                        xahkxyExtractor = com.wind.ggbond.classtime.util.extractor.XAHKXYExtractor(),
                                        lnkjxyExtractor = com.wind.ggbond.classtime.util.extractor.LNKJXYExtractor(),
                                        lnzbzzzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.LNZBZZZYJSXYExtractor(),
                                        zzdxExtractor = com.wind.ggbond.classtime.util.extractor.ZZDXExtractor(),
                                        zzxysExtractor = com.wind.ggbond.classtime.util.extractor.ZZXYSExtractor(),
                                        cqykdxExtractor = com.wind.ggbond.classtime.util.extractor.CQYKDXExtractor(),
                                        cjdxExtractor = com.wind.ggbond.classtime.util.extractor.CJDXExtractor(),
                                        // === Agent 1: 正方教务 + URP教务 + 金智教务 ===
                                        dhlgdxExtractor = com.wind.ggbond.classtime.util.extractor.DHLGDXExtractor(),
                                        btzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.BTZYJSXYExtractor(),
                                        hebhdxyExtractor = com.wind.ggbond.classtime.util.extractor.HEBHDXYExtractor(),
                                        ksdxExtractor = com.wind.ggbond.classtime.util.extractor.KSDXExtractor(),
                                        scgsxyExtractor = com.wind.ggbond.classtime.util.extractor.SCGSXYExtractor(),
                                        scqhgdxExtractor = com.wind.ggbond.classtime.util.extractor.SCQHGDXExtractor(),
                                        tykjdxExtractor = com.wind.ggbond.classtime.util.extractor.TYKJDXExtractor(),
                                        nxsfxyExtractor = com.wind.ggbond.classtime.util.extractor.NXSFXYExtractor(),
                                        ahjzdxExtractor = com.wind.ggbond.classtime.util.extractor.AHJZDXExtractor(),
                                        sdsfxyExtractor = com.wind.ggbond.classtime.util.extractor.SDSFXYExtractor(),
                                        gxwgyxyExtractor = com.wind.ggbond.classtime.util.extractor.GXWGYXYExtractor(),
                                        whdxExtractor = com.wind.ggbond.classtime.util.extractor.WHDXExtractor(),
                                        hbcmxyExtractor = com.wind.ggbond.classtime.util.extractor.HBCMXYExtractor(),
                                        szxxzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.SZXXZYJSXYExtractor(),
                                        hbjtxzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.HBJTZYJSXYExtractor(),
                                        zjkjxyExtractor = com.wind.ggbond.classtime.util.extractor.ZJKJXYExtractor(),
                                        ycsfxyExtractor = com.wind.ggbond.classtime.util.extractor.YCSFXYExtractor(),
                                        gzsfdxExtractor = com.wind.ggbond.classtime.util.extractor.GZSFDXExtractor(),
                                        hznydxExtractor = com.wind.ggbond.classtime.util.extractor.HZNYDXExtractor(),
                                        gzsxyExtractor = com.wind.ggbond.classtime.util.extractor.GZSXYExtractor(),
                                        cdyszydxExtractor = com.wind.ggbond.classtime.util.extractor.CDYSZYDXExtractor(),
                                        hznydxByytExtractor = com.wind.ggbond.classtime.util.extractor.HZNYDXBYYTExtractor(),
                                        nmgkjdxExtractor = com.wind.ggbond.classtime.util.extractor.NMGKJDXExtractor(),
                                        hblgdxExtractor = com.wind.ggbond.classtime.util.extractor.HBLGDXExtractor(),
                                        hbnydxExtractor = com.wind.ggbond.classtime.util.extractor.HBNYDXExtractor(),
                                        ytdxExtractor = com.wind.ggbond.classtime.util.extractor.YTDXExtractor(),
                                        ahyxgdzkxxExtractor = com.wind.ggbond.classtime.util.extractor.AHYXGDZKXXExtractor(),
                                        ysdxExtractor = com.wind.ggbond.classtime.util.extractor.YSDXExtractor(),
                                        ccqcgygdzkxxExtractor = com.wind.ggbond.classtime.util.extractor.CCQCGYGDZKXXExtractor(),
                                        // === Agent 2: 强智教务系统 ===
                                        bjcmzyxyExtractor = com.wind.ggbond.classtime.util.extractor.BJCMZYXYExtractor(),
                                        nnsfdxExtractor = com.wind.ggbond.classtime.util.extractor.NNSFDXExtractor(),
                                        sddxExtractor = com.wind.ggbond.classtime.util.extractor.SDDXExtractor(),
                                        gdngsExtractor = com.wind.ggbond.classtime.util.extractor.GDNGSExtractor(),
                                        gzhsxyExtractor = com.wind.ggbond.classtime.util.extractor.GZHSXYExtractor(),
                                        cdgyxyExtractor = com.wind.ggbond.classtime.util.extractor.CDGYXYExtractor(),
                                        whxxcbzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.WHXXCBZYJSXYExtractor(),
                                        whgckjxyExtractor = com.wind.ggbond.classtime.util.extractor.WHGCKJXYExtractor(),
                                        hnzyyExtractor = com.wind.ggbond.classtime.util.extractor.HNZYYExtractor(),
                                        hysfxyExtractor = com.wind.ggbond.classtime.util.extractor.HYSFXYExtractor(),
                                        cqrwkjxyExtractor = com.wind.ggbond.classtime.util.extractor.CQRWKJXYExtractor(),
                                        ccdxExtractor = com.wind.ggbond.classtime.util.extractor.CCDXExtractor(),
                                        bjcmzyxyMobileExtractor = com.wind.ggbond.classtime.util.extractor.BJCMZYXYMobileExtractor(),
                                        whxxcbzyjsxyMobileExtractor = com.wind.ggbond.classtime.util.extractor.WHXXCBZYJSXYMobileExtractor(),
                                        hnrjzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.HNRJZYJSXYExtractor(),
                                        // === Agent 2: 青果教务系统 ===
                                        hbslsdExtractor = com.wind.ggbond.classtime.util.extractor.HBSLSDExtractor(),
                                        hbslsdOldExtractor = com.wind.ggbond.classtime.util.extractor.HBSLSDOldExtractor(),
                                        sdnzxyExtractor = com.wind.ggbond.classtime.util.extractor.SDNZXYExtractor(),
                                        xjdxExtractor = com.wind.ggbond.classtime.util.extractor.XJDXExtractor(),
                                        kmykdxExtractor = com.wind.ggbond.classtime.util.extractor.KMYKDXExtractor(),
                                        jhdxExtractor = com.wind.ggbond.classtime.util.extractor.JHDXExtractor(),
                                        hnnydxExtractor = com.wind.ggbond.classtime.util.extractor.HNNYDXExtractor(),
                                        hxxyExtractor = com.wind.ggbond.classtime.util.extractor.HXXYExtractor(),
                                        zjkjxyQingguoExtractor = com.wind.ggbond.classtime.util.extractor.ZJKJXYQingguoExtractor(),
                                        ytnsxyExtractor = com.wind.ggbond.classtime.util.extractor.YTNSXYExtractor(),
                                        xafyxyExtractor = com.wind.ggbond.classtime.util.extractor.XAFYXYExtractor(),
                                        zzsdjmglxyExtractor = com.wind.ggbond.classtime.util.extractor.ZZSDJMGLXYExtractor(),
                                        ldxyExtractor = com.wind.ggbond.classtime.util.extractor.LDXYExtractor(),
                                        // === Agent 3: 自研教务系统 + 乘方教务系统 + 其他系统 ===
                                        njzyydxExtractor = com.wind.ggbond.classtime.util.extractor.NJZYYDXExtractor(),
                                        hebgydxExtractor = com.wind.ggbond.classtime.util.extractor.HEBGYDXExtractor(),
                                        sddyykdxExtractor = com.wind.ggbond.classtime.util.extractor.SDDYYKDXExtractor(),
                                        sdypzyxyExtractor = com.wind.ggbond.classtime.util.extractor.SDYPZYXYExtractor(),
                                        sxgckjzydxExtractor = com.wind.ggbond.classtime.util.extractor.SXGCKJZYDXExtractor(),
                                        whzyjsxyCustomExtractor = com.wind.ggbond.classtime.util.extractor.WHZYJSXYCustomExtractor(),
                                        hnzyydxYjsExtractor = com.wind.ggbond.classtime.util.extractor.HNZYYDXYJSExtractor(),
                                        qzsfxyExtractor = com.wind.ggbond.classtime.util.extractor.QZSFXYExtractor(),
                                        tzzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.TZZYJSXYExtractor(),
                                        xajzkjdxExtractor = com.wind.ggbond.classtime.util.extractor.XAJZKJDXExtractor(),
                                        gzgcyyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.GZGCYYJSXYExtractor(),
                                        qddxExtractor = com.wind.ggbond.classtime.util.extractor.QDDXExtractor(),
                                        qqheyxyExtractor = com.wind.ggbond.classtime.util.extractor.QQHEYXYExtractor(),
                                        gdjtzyjsxyExtractor = com.wind.ggbond.classtime.util.extractor.GDJTZYJSXYExtractor(),
                                        gzzyydxExtractor = com.wind.ggbond.classtime.util.extractor.GZZYYDXExtractor(),
                                        whjgzyxyExtractor = com.wind.ggbond.classtime.util.extractor.WHJGZYXYExtractor(),
                                        jzlgzyxyExtractor = com.wind.ggbond.classtime.util.extractor.JZLGZYXYExtractor(),
                                        xaoyxyExtractor = com.wind.ggbond.classtime.util.extractor.XAOYXYExtractor(),
                                        dytydzkjxxExtractor = com.wind.ggbond.classtime.util.extractor.DYTYDZKJXXExtractor(),
                                        ccsfdxExtractor = com.wind.ggbond.classtime.util.extractor.CCSFDXExtractor(),
                                        jxslzyxyExtractor = com.wind.ggbond.classtime.util.extractor.JXSLZYXYExtractor(),
                                        cddxExtractor = com.wind.ggbond.classtime.util.extractor.CDDXExtractor(),
                                        sckjzyxyExtractor = com.wind.ggbond.classtime.util.extractor.SCKJZYXYExtractor()
                                    )
                                    val extractor = extractorFactory.getExtractor(schoolId)
                                    if (extractor != null) {
                                        val extractScript = extractor.generateExtractionScript()
                                        
                                        wv.evaluateJavascript(extractScript) { result ->
                                            isLoading = false
                                            if (result != null && result != "null") {
                                                viewModel.parseScheduleWithExtractor(schoolId, result)
                                            } else {
                                                Toast.makeText(context, "未找到课表数据", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        isLoading = false
                                        Toast.makeText(context, "该学校暂未适配自动提取功能", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "请先登录并进入课表页面", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("提取课程")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // WebView 容器 (Box支持加载指示器对齐)
            Box(
                modifier = Modifier.weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                        // ✅ 设置布局参数，确保正确填充
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                
                            // ✅ 安全配置
                            allowFileAccess = false
                            allowContentAccess = false
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                                allowFileAccessFromFileURLs = false
                                allowUniversalAccessFromFileURLs = false
                            }
                            
                            // ✅ 混合内容模式 - 允许HTTP和HTTPS混合使用
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            
                            // ✅ 启用缓存以提高加载速度
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            
                            // ✅ 设置超时和加载重试
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                            
                            // ✅ 优化视口设置，确保内容正确显示
                            useWideViewPort = true
                            loadWithOverviewMode = false  // 改为false，避免自动缩放
                            setSupportZoom(true)
                            builtInZoomControls = false  // 禁用内置缩放控件
                            displayZoomControls = false
                            
                            // ✅ 根据系统类型设置User-Agent
                            userAgentString = if (systemType.lowercase() == "qiangzhi") {
                                // 强制桌面版
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            } else {
                                // 使用默认User-Agent
                                android.webkit.WebSettings.getDefaultUserAgent(context)
                            }
                            
                            // ✅ 优化文本渲染
                            textZoom = 100
                            defaultTextEncodingName = "UTF-8"
                        }
                        
                        // ✅ 启用硬件加速
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        
                        // ✅ 设置背景色和填充
                        setBackgroundColor(Color.WHITE)
                        setPadding(0, 0, 0, 0)  // 移除默认padding
                        
                        // ✅ 设置滚动条样式
                        scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }
                                
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    isLoading = false
                                    
                                    android.util.Log.e("SmartWebView", "页面加载失败: $description (错误码: $errorCode, URL: $failingUrl)")
                                    
                                    // 检查是否是HTTP/2协议错误
                                    if (description?.contains("ERR_HTTP2_PROTOCOL_ERROR", ignoreCase = true) == true ||
                                        description?.contains("ERR_SPDY_PROTOCOL_ERROR", ignoreCase = true) == true) {
                                        // HTTP/2错误：尝试回退到HTTP
                                        if (failingUrl?.startsWith("https://") == true) {
                                            val httpUrl = failingUrl.replace("https://", "http://")
                                            android.util.Log.d("SmartWebView", "检测到HTTP/2错误，回退到HTTP: $httpUrl")
                                            Toast.makeText(
                                                context,
                                                "检测到协议错误，正在尝试其他方式加载...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            view?.loadUrl(httpUrl)
                                            return
                                        }
                                    }
                                    
                                    // 尝试将HTTP自动转换为HTTPS重试
                                    if (failingUrl?.startsWith("http://") == true && 
                                        description?.contains("ERR_HTTP2_PROTOCOL_ERROR", ignoreCase = true) != true) {
                                        val httpsUrl = failingUrl.replace("http://", "https://")
                                        android.util.Log.d("SmartWebView", "HTTP加载失败，尝试HTTPS: $httpsUrl")
                                        view?.loadUrl(httpsUrl)
                                    } else {
                                        // 最终失败，显示错误信息
                                        Toast.makeText(
                                            context,
                                            "页面加载失败: $description\n请检查网络或联系管理员",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                
                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    android.util.Log.w(
                                        "SmartWebView",
                                        "HTTP错误: ${errorResponse?.statusCode} - ${request?.url}"
                                    )
                                }
                                
                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: android.webkit.SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    // 警告：生产环境中应该谨慎处理SSL错误
                                    android.util.Log.w("SmartWebView", "SSL证书错误: ${error?.toString()}")
                                    // 可以选择继续加载（不安全）或取消
                                    handler?.proceed() // 继续加载，忽略SSL错误（仅用于教务系统兼容）
                                    Toast.makeText(
                                        context,
                                        "检测到不安全的连接，已继续加载",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    
                                    // ⭐ 保存Cookie供后续自动更新使用
                                    url?.let { pageUrl ->
                                        val cookies = android.webkit.CookieManager.getInstance().getCookie(pageUrl)
                                        if (!cookies.isNullOrEmpty()) {
                                            try {
                                                val domain = java.net.URI(pageUrl).host ?: ""
                                                if (domain.isNotEmpty()) {
                                                    val cookieManager = com.wind.ggbond.classtime.util.SecureCookieManager(context)
                                                    cookieManager.saveCookies(
                                                        domain = domain,
                                                        cookies = cookies,
                                                        lifetimeMs = java.util.concurrent.TimeUnit.DAYS.toMillis(30)
                                                    )
                                                    android.util.Log.d("SmartWebView", "✓ Cookie已保存，可用于自动更新: $domain")
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("SmartWebView", "保存Cookie失败", e)
                                            }
                                        }
                                    }
                                    
                                // ✅ 确保页面正确显示，不进行自动缩放
                                view?.evaluateJavascript("""
                                    (function() {
                                        var meta = document.createElement('meta');
                                        meta.name = 'viewport';
                                        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                                        document.getElementsByTagName('head')[0].appendChild(meta);
                                    })();
                                """.trimIndent(), null)
                                    
                                    // ⭐ 开发模式：自动注入页面检查脚本
                                    if (BuildConfig.DEBUG) {
                                        url?.let {
                                            val inspectionScript = viewModel.getInspectionScript()
                                            view?.evaluateJavascript(inspectionScript) { result ->
                                                android.util.Log.d("PageInspection", "页面结构检查结果: $result")
                                                // 保存检查结果到状态变量，供复制按钮使用
                                                pageInspectionResult = result?.removeSurrounding("\"")?.replace("\\n", "\n")
                                            }
                                        }
                                    }
                                }
                            }
                            
                            loadUrl(schoolUrl)
                            webView = this
                        }
                    },
                    onRelease = { webView ->
                    // ✅ 防止内存泄漏
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
            
            // 解析状态显示（悬浮在底部，不遮挡WebView）
            when (parseState) {
                is ParseState.Parsing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("正在解析课程数据...")
                            }
                        }
                    }
                }
                is ParseState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "解析失败",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = (parseState as ParseState.Error).message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                // 仅在debug模式下显示复制调试信息按钮
                                // 即使debugInfo为空，也显示按钮以便复制错误信息
                                if (BuildConfig.DEBUG) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            // 如果有调试信息，复制调试信息；否则复制错误消息
                                            val textToCopy = if (debugInfo.isNotEmpty()) {
                                                debugInfo.joinToString("\n")
                                            } else {
                                                (parseState as ParseState.Error).message
                                            }
                                            copyTextToClipboard(context, textToCopy)
                                            Toast.makeText(context, "信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (debugInfo.isNotEmpty()) "复制调试信息" else "复制错误信息")
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
            } // 关闭 WebView 的 Box
        } // 关闭 Column
    }
}

/**
 * 复制文本到剪贴板
 */
private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = android.content.ClipData.newPlainText("页面信息", text)
    clipboard.setPrimaryClip(clip)
}

/**
 * WebView 中的学期设置对话框
 * 
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调，参数为(课表昵称, 开始日期, 总周数)
 * @param fallSemesterStartDate 秋季学期的默认开始日期（学校配置）
 * @param springSemesterStartDate 春季学期的默认开始日期（学校配置）
 * @param importedSemesterInfo 导入时提取到的学期信息（如果有则不需要用户设置日期）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSetupDialogInWebView(
    onDismiss: () -> Unit,
    onConfirm: (String, java.time.LocalDate, Int) -> Unit,
    fallSemesterStartDate: String? = null,  // 秋季学期的默认开始日期
    springSemesterStartDate: String? = null,  // 春季学期的默认开始日期
    importedSemesterInfo: com.wind.ggbond.classtime.data.model.ImportedSemesterInfo? = null  // 导入时提取到的学期信息
) {
    // 检查是否有完整的学期日期信息（如果有则不需要用户设置日期）
    val hasCompleteDateInfo = importedSemesterInfo?.hasCompleteDateInfo() == true
    // 智能生成默认学期名称
    val currentDate = java.time.LocalDate.now()
    val month = currentDate.monthValue
    val year = currentDate.year
    val defaultSemesterName = if (month >= 2 && month <= 7) {
        "${year - 1}-${year}学年第二学期"
    } else {
        "${year}-${year + 1}学年第一学期"
    }
    
    // 智能生成默认开始日期：优先使用导入的学期信息，其次使用学校配置，最后使用智能推断
    val defaultStartDate = if (importedSemesterInfo?.startDate != null) {
        // 优先使用导入时提取到的开始日期
        importedSemesterInfo.startDate
    } else if (month >= 2 && month <= 7) {
        // 春季学期
        if (springSemesterStartDate != null) {
            try {
                java.time.LocalDate.parse(springSemesterStartDate)
            } catch (e: Exception) {
                // 解析失败，使用默认的3月第一周的周一
                val marchFirst = java.time.LocalDate.of(year, 3, 1)
                var firstMonday = marchFirst
                while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                    firstMonday = firstMonday.plusDays(1)
                }
                firstMonday
            }
        } else {
            // 默认3月第一周的周一
            val marchFirst = java.time.LocalDate.of(year, 3, 1)
            var firstMonday = marchFirst
            while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                firstMonday = firstMonday.plusDays(1)
            }
            firstMonday
        }
    } else {
        // 秋季学期
        if (fallSemesterStartDate != null) {
            try {
                java.time.LocalDate.parse(fallSemesterStartDate)
            } catch (e: Exception) {
                // 解析失败，使用默认的9月第一周的周一
                val septFirst = java.time.LocalDate.of(year, 9, 1)
                var firstMonday = septFirst
                while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                    firstMonday = firstMonday.plusDays(1)
                }
                firstMonday
            }
        } else {
            // 默认9月第一周的周一
            val septFirst = java.time.LocalDate.of(year, 9, 1)
            var firstMonday = septFirst
            while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                firstMonday = firstMonday.plusDays(1)
            }
            firstMonday
        }
    }
    
    // 默认总周数：优先使用导入的学期信息
    val defaultTotalWeeks = importedSemesterInfo?.calculateTotalWeeks() ?: 20
    
    var semesterName by remember { mutableStateOf(defaultSemesterName) }
    var startDate by remember { mutableStateOf(defaultStartDate) }
    var totalWeeks by remember { mutableStateOf(defaultTotalWeeks) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasCompleteDateInfo) "设置课表昵称" else "设置学期信息") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 如果有完整的日期信息，显示提示
                if (hasCompleteDateInfo) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "已自动获取学期日期信息",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "开始: ${startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "共${totalWeeks}周",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // 课表昵称输入框（始终显示）
                OutlinedTextField(
                    value = semesterName,
                    onValueChange = { semesterName = it },
                    label = { Text("课表昵称") },
                    placeholder = { Text("例如：大三下学期") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // 如果没有完整的日期信息，显示日期和周数设置
                if (!hasCompleteDateInfo) {
                    // 开始日期 - 使用日期选择器
                    OutlinedTextField(
                        value = startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                        onValueChange = { },
                        label = { Text("第一天上课日期") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        readOnly = true,
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarMonth, "选择日期")
                            }
                        }
                    )
                    
                    // 总周数 - 提供快速选择
                    var showWeeksOptions by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showWeeksOptions,
                        onExpandedChange = { showWeeksOptions = it }
                    ) {
                        OutlinedTextField(
                            value = "${totalWeeks}周",
                            onValueChange = { },
                            label = { Text("学期总周数") },
                            readOnly = true,
                            trailingIcon = { 
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showWeeksOptions) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = showWeeksOptions,
                            onDismissRequest = { showWeeksOptions = false }
                        ) {
                            listOf(16, 18, 20, 22, 24).forEach { weeks ->
                                DropdownMenuItem(
                                    text = { Text("${weeks}周") },
                                    onClick = {
                                        totalWeeks = weeks
                                        showWeeksOptions = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // 显示计算的结束日期
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "结束日期",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(semesterName, startDate, totalWeeks)
                },
                enabled = semesterName.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
    
    // 日期选择器对话框
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ⭐ 旧的通用提取逻辑已移除
// 现在使用学校专用提取器，每个学校有独立的提取脚本
// 参见: util/extractor/ 目录下的各个提取器实现
// SectionSetupDialog 已在 ImportScheduleScreen.kt 中定义
