package com.mnivesh.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mnivesh.smsforwarder.managers.AuthManager

class MainActivity : ComponentActivity() {

    private val TAG = "AuthFlowDebug"
    private var navigateToHome by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "SMS permissions are required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d(TAG, "onCreate triggered")

        checkAndRequestPermissions()

        val authManager = AuthManager(this)

        val startDest = if (authManager.isLoggedIn()) {
            Log.d(TAG, "User is already logged in. Bypassing LoginScreen.")
            "home"
        } else {
            Log.d(TAG, "User not logged in. Showing LoginScreen.")
            "login"
        }

        intent?.let {
            Log.d(TAG, "onCreate Intent received: ${it.data}")
            handleIntent(it)
        }

        setContent {
            AppNavigation(
                startDestination = startDest,
                shouldNavigateHome = navigateToHome,
                onNavigated = { navigateToHome = false }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent triggered with data: ${intent.data}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data != null) {
            Log.d(TAG, "Handling Intent Data: Scheme=${data.scheme}, Host=${data.host}, Path=${data.path}")

            if (data.scheme == "smsforwarder" && data.host == "auth") {
                // Read everything directly from the URL parameters!
                val token = data.getQueryParameter("token")
                val name = data.getQueryParameter("name")
                val email = data.getQueryParameter("email")
                val department = data.getQueryParameter("department")

                Log.d(TAG, "Extracted SSO Token: ${token?.take(10)}... Name: $name")

                if (!token.isNullOrEmpty()) {
                    Log.d(TAG, "Login Success via Direct Token!")

                    AuthManager(this@MainActivity).apply {
                        saveToken(token)
                        saveUserName(name)
                        saveUserEmail(email)
                        saveDepartment(department)
                    }

                    Toast.makeText(this@MainActivity, "Welcome, $name", Toast.LENGTH_SHORT).show()
                    navigateToHome = true
                } else {
                    Log.e(TAG, "SSO Token was null in the callback!")
                    Toast.makeText(this@MainActivity, "Login Failed: No Token", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d(TAG, "Intent data did not match smsforwarder://auth")
            }
        } else {
            Log.d(TAG, "Intent data was null (Normal app launch)")
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPerms = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        val missingPerms = requiredPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPerms.toTypedArray())
        }
    }
}