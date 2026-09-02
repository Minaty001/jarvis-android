package com.jarvis.backend

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Unauthorized(val message: String = "Authentication required") : ApiResult<Nothing>
    data class Forbidden(val message: String = "Access denied") : ApiResult<Nothing>
    data class ValidationError(val message: String, val details: String? = null) : ApiResult<Nothing>
    data class NetworkError(val message: String = "Network connection failed") : ApiResult<Nothing>
    data class Timeout(val message: String = "Request timed out") : ApiResult<Nothing>
    data class ServerError(val code: Int, val message: String = "Server error") : ApiResult<Nothing>
    data class Enrolled(val enrollmentSecret: String) : ApiResult<Nothing>
}
