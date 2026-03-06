package com.mnivesh.smsforwarder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mnivesh.smsforwarder.managers.AuthManager
import com.mnivesh.smsforwarder.workers.SmsUploadWorker
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val authManager = AuthManager(context)
        if (!authManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in. Ignoring SMS.")
            return
        }

        // load whitelist from prefs
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val whitelistSet = prefs.getStringSet("whitelist_senders", emptySet()) ?: emptySet()

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Group messages by sender (to handle multi-part long SMS messages correctly)
        val messagesBySender = messages.groupBy { it.originatingAddress ?: "Unknown" }

        for ((sender, smsParts) in messagesBySender) {
            // Reconstruct the full message by joining all the chunks
            val fullMessageBody = smsParts.joinToString(separator = "") { it.messageBody ?: "" }

            // Just grab the timestamp from the first chunk
            val timestamp = smsParts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            // check if sender matches any whitelisted tag
            val isWhitelisted = whitelistSet.any { tag ->
                tag.isNotEmpty() && sender.contains(tag, ignoreCase = true)
            }

            if (isWhitelisted) {
                Log.d(TAG, "Match found for $sender. Queueing upload.")
                val data = workDataOf(
                    "sender" to sender,
                    "message" to fullMessageBody,
                    "timestamp" to timestamp
                )

                // 1. Force WorkManager to only run this when the network is actually available
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val uploadWork = OneTimeWorkRequestBuilder<SmsUploadWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                    .build()

                // 2. Create a mathematically unique ID for this specific upload
                val uniqueWorkName = "sms_upload_${sender}_${System.currentTimeMillis()}"

                // 3. Enqueue it as a UNIQUE piece of work so it never overwrites previous pending messages
                WorkManager.getInstance(context).enqueueUniqueWork(
                    uniqueWorkName,
                    ExistingWorkPolicy.REPLACE,
                    uploadWork
                )
            }
        }
    }
}