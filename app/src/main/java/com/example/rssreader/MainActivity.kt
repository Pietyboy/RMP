package com.example.rssreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.rssreader.ui.ActivityScreen
import com.example.rssreader.ui.theme.RSSReaderTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType

private const val TAG = "MainActivity"
private const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001

/**
 * Main Activity that hosts the ActivityScreen composable
 */
class MainActivity : ComponentActivity() {
    
    // Register the permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "ACTIVITY_RECOGNITION permission granted")
            // Permission granted, the ActivityScreen will handle the next steps
        } else {
            Log.e(TAG, "ACTIVITY_RECOGNITION permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for ACTIVITY_RECOGNITION permission on startup for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == 
                    PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "ACTIVITY_RECOGNITION permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) -> {
                    Log.d(TAG, "Show permission rationale for ACTIVITY_RECOGNITION")
                    // Could show an explanation here if needed
                }
                else -> {
                    // No need to request here, will be requested when Connect button is clicked
                    Log.d(TAG, "ACTIVITY_RECOGNITION permission not yet requested")
                }
            }
        }
        
        setContent {
            RSSReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityScreen()
                }
            }
        }
    }
    
    // Handle the result from the permission request
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            Log.d(TAG, "Received activity result for Fit permissions: $resultCode")
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Google Fit permissions granted")
            } else {
                Log.e(TAG, "Google Fit permissions denied")
            }
        }
    }
}