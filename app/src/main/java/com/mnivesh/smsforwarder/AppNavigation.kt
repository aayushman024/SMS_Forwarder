package com.mnivesh.smsforwarder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mnivesh.smsforwarder.screens.HomeScreen
import com.mnivesh.smsforwarder.screens.LoginScreen

@Composable
fun AppNavigation(
    startDestination: String,
    shouldNavigateHome: Boolean,
    onNavigated: () -> Unit
) {
    val navController = rememberNavController()

    // Observes state changes from MainActivity
    LaunchedEffect(shouldNavigateHome) {
        if (shouldNavigateHome) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
            onNavigated() // Reset the trigger
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(onLoginSuccess = {})
        }
        composable("home") {
            HomeScreen(
                onLogout = {
                    // Navigate back to login and clear everything else off the backstack
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}