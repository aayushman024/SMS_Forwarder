package com.mnivesh.smsforwarder.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class LoginResponse(
    val token: String,
    val userName: String
)

data class SmsLogRequest(
    val sender: String,
    val message: String,
    val timestamp: Long
)

// data class from callyn backend
data class SmsLogResponse(
    val _id: String,
    val sender: String,
    val message: String,
    val timestamp: String,
    val uploadedBy: String
)

interface ApiService {
    @GET("auth/callback")
    suspend fun handleCallback(@Query("code") code: String): Response<LoginResponse>

    @POST("postSMSLogs")
    suspend fun uploadSms(
        @Header("Authorization") token: String,
        @Body sms: SmsLogRequest
    ): Response<ResponseBody>

    // fetch active logs
    @GET("getSMSLogs")
    suspend fun getSmsLogs(
        @Header("Authorization") token: String
    ): Response<List<SmsLogResponse>>
}