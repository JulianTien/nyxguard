package com.scf.nyxguard.network

import com.scf.nyxguard.LocaleManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** 简化 Retrofit 回调，统一处理错误 */
fun <T> Call<T>.enqueue(
    onSuccess: (T) -> Unit,
    onError: ((String) -> Unit)? = null
) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            val body = response.body()
            if (response.isSuccessful && body != null) {
                @Suppress("UNCHECKED_CAST")
                onSuccess(adaptSuccessBody(body) as T)
            } else {
                onError?.invoke(parseErrorMessage(response) ?: localizedServerError(response.code()))
            }
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            onError?.invoke(localizedNetworkError(t.message.orEmpty()))
        }
    })
}

private fun adaptSuccessBody(body: Any): Any {
    if (body is JsonObject && !body.has("data")) {
        return JsonObject().apply {
            add("data", body.deepCopy())
        }
    }
    return body
}

private fun <T> parseErrorMessage(response: Response<T>): String? {
    val raw = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        Gson().fromJson(raw, ApiErrorResponse::class.java)
    }.getOrNull()?.let { error ->
        error.detail ?: error.message
    } ?: raw
}

private fun localizedServerError(code: Int): String {
    return if (LocaleManager.isChinese()) {
        "服务器错误: $code"
    } else {
        "Server error: $code"
    }
}

private fun localizedNetworkError(message: String): String {
    return if (LocaleManager.isChinese()) {
        "网络连接失败: $message"
    } else {
        "Network connection failed: $message"
    }
}
