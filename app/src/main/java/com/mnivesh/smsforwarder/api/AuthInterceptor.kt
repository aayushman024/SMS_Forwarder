package com.mnivesh.smsforwarder.api

import android.content.Context
import android.content.Intent
import com.mnivesh.smsforwarder.MainActivity
import com.mnivesh.smsforwarder.managers.AuthManager
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject

class AuthInterceptor(private val context: Context) : Interceptor {
    private val authManager = AuthManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val authHeader = originalRequest.header("Authorization")

        val response = chain.proceed(originalRequest)
        if (response.code() != 401 || authHeader.isNullOrBlank()) {
            return response
        }

        synchronized(this) {
            val currentToken = authManager.getToken()
            val previousToken = authHeader.removePrefix("Bearer ").trim()

            if (currentToken != null && currentToken != previousToken) {
                response.close()
                return chain.proceed(newRequestWithToken(originalRequest, currentToken))
            }

            val refreshToken = authManager.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                val refreshedToken = performRefresh(refreshToken)
                if (!refreshedToken.isNullOrBlank()) {
                    response.close()
                    return chain.proceed(newRequestWithToken(originalRequest, refreshedToken))
                }
            }

            response.close()
            logoutAndRedirect()
            return response
        }
    }

    private fun newRequestWithToken(request: Request, token: String): Request {
        return request.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $token")
            .build()
    }

    private fun performRefresh(refreshToken: String): String? {
        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("refreshToken", refreshToken)
        }

        val mediaType = MediaType.parse("application/json; charset=utf-8")
        val body = RequestBody.create(mediaType, json.toString())

        val request = Request.Builder()
            .url("https://app-store-dqg8bnf4d8cberf7.centralindia-01.azurewebsites.net/mobile/refresh")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val responseBody = response.body()?.string() ?: return null
                val jsonObject = JSONObject(responseBody)
                val newAccessToken = jsonObject.optString("accessToken")
                val newRefreshToken = jsonObject.optString("refreshToken")

                if (newAccessToken.isNotBlank()) {
                    authManager.saveToken(newAccessToken)
                    if (newRefreshToken.isNotBlank()) {
                        authManager.saveRefreshToken(newRefreshToken)
                    }
                    newAccessToken
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun logoutAndRedirect() {
        authManager.logout()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
