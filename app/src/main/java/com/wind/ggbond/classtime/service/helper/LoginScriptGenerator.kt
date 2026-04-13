package com.wind.ggbond.classtime.service.helper

import android.util.Base64
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginScriptGenerator @Inject constructor() {

    fun generateAutoLoginScript(username: String, password: String): String {
        val encodedUsername = Base64.encodeToString(username.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val encodedPassword = Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val params = JSONObject().apply {
            put("u", encodedUsername)
            put("p", encodedPassword)
        }
        val jsonParams = params.toString()

        return """
            (function() {
                try {
                    var _params = JSON.parse(${escapeJsString(jsonParams)});
                    var _d = function(b) {
                        try { return decodeURIComponent(escape(atob(b))); }
                        catch(e) { return atob(b); }
                    };
                    var _u = _d(_params.u);
                    var _p = _d(_params.p);

                    var form = document.getElementById('fm1');
                    if (!form) {
                        console.log('Login form not found');
                        return JSON.stringify({success: false});
                    }

                    var kaptcha = document.getElementById('kaptcha');
                    if (kaptcha && kaptcha.style.display !== 'none') {
                        console.log('Captcha detected');
                        return JSON.stringify({needsCaptcha: true});
                    }

                    var userInput = document.getElementById('username');
                    if (userInput) {
                        userInput.value = _u;
                        userInput.dispatchEvent(new Event('input', { bubbles: true }));
                        userInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    var ppassword = document.getElementById('ppassword');
                    if (ppassword) {
                        ppassword.value = _p;
                        ppassword.dispatchEvent(new Event('input', { bubbles: true }));
                        ppassword.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    var password = document.getElementById('password');
                    if (password) {
                        password.value = _p;
                        ppassword.dispatchEvent(new Event('input', { bubbles: true }));
                        ppassword.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    var cjgzs = document.getElementById('cjgzsType');
                    if (cjgzs) {
                        cjgzs.checked = true;
                    }

                    var loginBtn = document.getElementById('dl');
                    if (loginBtn) {
                        loginBtn.click();
                        return JSON.stringify({submitted: true});
                    } else {
                        return JSON.stringify({submitted: false});
                    }

                } catch (e) {
                    return JSON.stringify({error: e.message});
                }
            })();
        """.trimIndent()
    }

    private fun escapeJsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
