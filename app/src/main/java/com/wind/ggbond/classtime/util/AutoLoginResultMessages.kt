package com.wind.ggbond.classtime.util

object AutoLoginResultMessages {
    fun getTitle(resultCode: String): String {
        return when (resultCode) {
            AutoLoginResultCode.OK -> "时课 自动更新成功"
            AutoLoginResultCode.NEED_CAPTCHA -> "时课 自动更新需要验证"
            AutoLoginResultCode.LOGIN_FAIL -> "时课 自动登录失败"
            AutoLoginResultCode.NO_CREDENTIAL -> "时课 未配置自动登录"
            AutoLoginResultCode.NETWORK_ERROR -> "时课 网络错误"
            else -> "时课 自动更新失败"
        }
    }

    fun getContent(resultCode: String, resultMessage: String = ""): String {
        return when (resultCode) {
            AutoLoginResultCode.NEED_CAPTCHA -> "需要在设置页完成验证码和登录"
            AutoLoginResultCode.LOGIN_FAIL -> "请检查账号密码是否正确"
            AutoLoginResultCode.NO_CREDENTIAL -> "请在设置页配置自动登录账号"
            AutoLoginResultCode.NETWORK_ERROR -> "请检查网络连接"
            else -> resultMessage.ifEmpty { "未知错误" }
        }
    }

    fun getMessage(resultCode: String): String {
        return getContent(resultCode)
    }
}
