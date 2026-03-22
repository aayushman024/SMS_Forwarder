package com.mnivesh.smsforwarder.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
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

data class EmployeeDirectory(
    val name: String,
    val email: String,
    val phone: String,
    val department: String,
)

data class WhitelistRequest(
    val senderID: String,
    val applicableEmployees: List<String> // sending emails instead of objects
)

data class WhitelistResponse(
    val _id: String,
    val senderID: String,
    val applicableEmployees: List<String>,
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

    @GET("getEmployeePhoneDetails")
    suspend fun getEmployeePhoneDetails(
        @Header("Authorization") token: String,
    ): Response<List<EmployeeDirectory>>

    @GET("whitelist")
    suspend fun getWhitelist(
        @Header("Authorization") token: String
    ): Response<List<WhitelistResponse>>

    @POST("whitelist")
    suspend fun addWhitelistEntry(
        @Header("Authorization") token: String,
        @Body request: WhitelistRequest
    ): Response<WhitelistResponse>

    @DELETE("whitelist/{id}")
    suspend fun deleteWhitelistEntry(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<ResponseBody>
}