package com.wind.ggbond.classtime.service.helper

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginScriptGenerator @Inject constructor() {

    fun generateAutoLoginScript(username: String, password: String): String {
        val escapedUsername = escapeForJavaScript(username)
        val escapedPassword = escapeForJavaScript(password)

        return """
            (function() {
                try {
                    var form = document.getElementById('fm1');
                    if (!form) {
                        console.log('❌ 未找到登录表单');
                        return JSON.stringify({success: false});
                    }
                    
                    var kaptcha = document.getElementById('kaptcha');
                    if (kaptcha && kaptcha.style.display !== 'none') {
                        console.log('⚠️ 检测到验证码');
                        return JSON.stringify({needsCaptcha: true});
                    }
                    
                    var userInput = document.getElementById('username');
                    if (userInput) {
                        userInput.value = '$escapedUsername';
                        userInput.dispatchEvent(new Event('input', { bubbles: true }));
                        userInput.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('✅ 账号已填充');
                    }
                    
                    var ppassword = document.getElementById('ppassword');
                    if (ppassword) {
                        ppassword.value = '$escapedPassword';
                        ppassword.dispatchEvent(new Event('input', { bubbles: true }));
                        ppassword.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('✅ 密码框已填充');
                    }
                    
                    var password = document.getElementById('password');
                    if (password) {
                        password.value = '$escapedPassword';
                        password.dispatchEvent(new Event('input', { bubbles: true }));
                        password.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('✅ 隐藏密码框已填充');
                    }
                    
                    var cjgzs = document.getElementById('cjgzsType');
                    if (cjgzs) {
                        cjgzs.checked = true;
                        console.log('✅ 协议已勾选');
                    }
                    
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

    fun escapeForJavaScript(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
    }
}
