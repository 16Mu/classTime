package com.wind.ggbond.classtime.ui.screen.scheduleimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import com.wind.ggbond.classtime.util.SecureCookieManager

/**
 * WebView 登录 ViewModel
 * 
 * ✅ 使用 SecureCookieManager 加密存储 Cookie
 */
@HiltViewModel
class WebViewLoginViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val secureCookieManager: SecureCookieManager
) : ViewModel() {
    
    private val _cookies = MutableStateFlow("")
    val cookies: StateFlow<String> = _cookies.asStateFlow()
    
    // 当前学校的域名（从loginUrl提取）
    private var currentDomain: String? = null
    
    init {
        loadCookies()
    }
    
    /**
     * 获取学校信息
     */
    fun getSchool(schoolId: String): Flow<SchoolEntity?> {
        return flow {
            emit(schoolRepository.getSchoolById(schoolId))
        }
    }
    
    /**
     * 设置当前学校（用于确定Cookie存储的域名）
     */
    fun setSchool(school: SchoolEntity) {
        currentDomain = extractDomain(school.loginUrl)
        loadCookies()
    }
    
    /**
     * 从URL提取域名
     */
    private fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host ?: "default"
        } catch (e: Exception) {
            "default"
        }
    }
    
    /**
     * 加载已保存的Cookie
     */
    private fun loadCookies() {
        viewModelScope.launch {
            val domain = currentDomain ?: "default"
            _cookies.value = secureCookieManager.getCookies(domain) ?: ""
        }
    }
    
    /**
     * 更新Cookie（仅更新内存状态）
     */
    fun updateCookies(cookies: String) {
        _cookies.value = cookies
    }
    
    /**
     * 保存Cookie（✅ 使用加密存储）
     */
    fun saveCookies(cookies: String) {
        viewModelScope.launch {
            val domain = currentDomain ?: "default"
            secureCookieManager.saveCookies(domain, cookies)
            _cookies.value = cookies
        }
    }

}

















