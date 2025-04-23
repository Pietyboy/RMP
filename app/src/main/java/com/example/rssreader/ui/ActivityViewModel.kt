package com.example.rssreader.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rssreader.fit.FitRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.FitnessOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAILY_STEP_GOAL = 10000

class ActivityViewModel(
    private val fitRepository: FitRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Initial)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun checkPermissionsAndFetchData(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = ActivityUiState.Loading

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    _uiState.value = ActivityUiState.NeedsPermission
                    return@launch
                }

                if (!fitRepository.hasPermissions(context)) {
                    _uiState.value = ActivityUiState.NeedsPermission
                    return@launch
                }

                val activityDataList = fitRepository.getDailyStepCount(context)
                val totalSteps = activityDataList.sumOf { it.steps }
                
                val lastUpdated = dateFormat.format(Date())

                activityDataList.forEachIndexed { index, data ->
                }

                _uiState.value = ActivityUiState.Success(
                    steps = totalSteps,
                    lastUpdated = lastUpdated,
                    dailyGoal = DAILY_STEP_GOAL
                )
            } catch (e: Exception) {
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

    fun checkInitialState(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            _uiState.value = ActivityUiState.NeedsPermission
            return
        }
        
        if (!fitRepository.hasPermissions(context)) {
            _uiState.value = ActivityUiState.NeedsPermission
        } else {
            checkPermissionsAndFetchData(context)
        }
    }

    fun handleError(message: String) {
        _uiState.value = ActivityUiState.Error(message)
    }

    fun getFitnessOptions(): FitnessOptions {
        return fitRepository.getFitnessOptions() as FitnessOptions
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = ActivityUiState.Loading
                
                val signInClient = GoogleSignIn.getClient(context, 
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .build()
                )
                
                signInClient.signOut().addOnCompleteListener {
                    _uiState.value = ActivityUiState.NeedsPermission
                }.addOnFailureListener { e ->
                    _uiState.value = ActivityUiState.Error("Failed to sign out: ${e.message}")
                }
                
            } catch (e: Exception) {
                _uiState.value = ActivityUiState.Error("Error signing out: ${e.message}")
            }
        }
    }

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