package com.wind.ggbond.classtime.service.contract

enum class CookieExpiredReason {
    COOKIE_EMPTY,
    SESSION_EXPIRED,
    REDIRECTED_TO_LOGIN,
    HTTP_UNAUTHORIZED,
    HTTP_REDIRECT,
    VALIDATION_FAILED
}

class CookieExpiredException(
    message: String,
    val reason: CookieExpiredReason,
    val httpStatusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
