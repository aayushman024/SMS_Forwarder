package com.mnivesh.smsforwarder.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnivesh.smsforwarder.api.RetrofitInstance
import com.mnivesh.smsforwarder.api.SmsLogRequest
import com.mnivesh.smsforwarder.managers.AuthManager

class SmsUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "SmsUploadWorker"

    override suspend fun doWork(): Result {
        val sender = inputData.getString("sender") ?: return Result.failure()
        val message = inputData.getString("message") ?: return Result.failure()
        val timestamp = inputData.getLong("timestamp", 0L)

        val token = AuthManager(applicationContext).getToken()
        if (token.isNullOrEmpty()) return Result.failure()

        return try {
            val payload = SmsLogRequest(sender, message, timestamp)
            val response = RetrofitInstance.api.uploadSms("Bearer $token", payload)

            if (response.isSuccessful) {
                Result.success()
            } else {
                // 500+ retry, 400 fail permanently
                if (response.code() >= 500) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.retry()
        }
    }
}