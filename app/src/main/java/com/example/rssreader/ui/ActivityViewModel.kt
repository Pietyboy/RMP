package com.example.rssreader.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rssreader.fit.FitRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "ActivityViewModel"
private const val DAILY_STEP_GOAL = 10000

/**
 * ViewModel for the Activity screen that handles step count data from Google Fit
 */
class ActivityViewModel(
    private val fitRepository: FitRepository
) : ViewModel() {
    
    // UI state exposed to the UI
    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Initial)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    /**
     * Check for permissions and fetch step data if permissions are granted
     */
    fun checkPermissionsAndFetchData(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = ActivityUiState.Loading
                Log.d(TAG, "Starting data fetch process")

                // Check for ACTIVITY_RECOGNITION permission on Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Missing ACTIVITY_RECOGNITION permission")
                    _uiState.value = ActivityUiState.NeedsPermission
                    return@launch
                }

                // Check for Google Fit permissions
                if (!fitRepository.hasPermissions(context)) {
                    Log.d(TAG, "Missing fitness permissions")
                    _uiState.value = ActivityUiState.NeedsPermission
                    return@launch
                }

                Log.d(TAG, "Permissions verified, fetching daily step count")
                
                val activityDataList = fitRepository.getDailyStepCount(context)
                val totalSteps = activityDataList.sumOf { it.steps }
                
                // Get current time for last updated
                val lastUpdated = dateFormat.format(Date())
                
                Log.d(TAG, "STEP DATA: Successfully processed all data")
                Log.d(TAG, "STEP DATA: Number of data entries: ${activityDataList.size}")
                activityDataList.forEachIndexed { index, data ->
                    Log.d(TAG, "STEP DATA: Entry $index - Start: ${data.startTime}, End: ${data.endTime}, Steps: ${data.steps}")
                }
                Log.d(TAG, "STEP DATA: Total steps: $totalSteps, last updated: $lastUpdated")
                
                _uiState.value = ActivityUiState.Success(
                    steps = totalSteps,
                    lastUpdated = lastUpdated,
                    dailyGoal = DAILY_STEP_GOAL
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during data fetch process", e)
                val errorMessage = when {
                    e.message?.contains("403") == true -> "Permission denied. Please check your Google Cloud Console settings."
                    e.message?.contains("401") == true -> "Authentication failed. Please sign in again."
                    e.message?.contains("ACTIVITY_RECOGNITION") == true -> "Activity recognition permission is required to track steps."
                    else -> "Error: ${e.message}"
                }
                _uiState.value = ActivityUiState.Error(errorMessage)
            }
        }
    }

    /**
     * Check initial state on app start
     */
    fun checkInitialState(context: Context) {
        Log.d(TAG, "Checking initial state")
        
        // Check for ACTIVITY_RECOGNITION permission on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Initial check: Missing ACTIVITY_RECOGNITION permission")
            _uiState.value = ActivityUiState.NeedsPermission
            return
        }
        
        if (!fitRepository.hasPermissions(context)) {
            Log.d(TAG, "Initial check: Need permissions")
            _uiState.value = ActivityUiState.NeedsPermission
        } else {
            Log.d(TAG, "Initial check: Already have permissions, fetching data")
            checkPermissionsAndFetchData(context)
        }
    }

    /**
     * Handle error messages
     */
    fun handleError(message: String) {
        Log.e(TAG, "Error handled: $message")
        _uiState.value = ActivityUiState.Error(message)
    }
    
    /**
     * Get FitnessOptions for Google Fit authentication
     */
    fun getFitnessOptions(): FitnessOptions {
        return fitRepository.getFitnessOptions() as FitnessOptions
    }
    
    /**
     * Sign out from Google account
     */
    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = ActivityUiState.Loading
                
                val signInClient = GoogleSignIn.getClient(context, 
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .build()
                )
                
                // Sign out from Google account
                signInClient.signOut().addOnCompleteListener {
                    Log.d(TAG, "User signed out successfully")
                    _uiState.value = ActivityUiState.NeedsPermission
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error signing out", e)
                    _uiState.value = ActivityUiState.Error("Failed to sign out: ${e.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during sign out", e)
                _uiState.value = ActivityUiState.Error("Error signing out: ${e.message}")
            }
        }
    }
    
    /**
     * Factory for creating ActivityViewModel with dependencies
     */
    class Factory(private val fitRepository: FitRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ActivityViewModel::class.java)) {
                return ActivityViewModel(fitRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * Sealed class representing different UI states for the Activity screen
 */
sealed class ActivityUiState {
    data object Initial : ActivityUiState()
    data object Loading : ActivityUiState()
    data object NeedsPermission : ActivityUiState()
    data class Success(
        val steps: Int,
        val lastUpdated: String = "",
        val dailyGoal: Int = 10000
    ) : ActivityUiState()
    data class Error(val message: String) : ActivityUiState()
} 