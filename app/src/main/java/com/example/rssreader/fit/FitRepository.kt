package com.example.rssreader.fit

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Fitness data
 */
interface FitRepository {
    /**
     * Check if the user has granted necessary permissions
     */
    fun hasPermissions(context: Context): Boolean
    
    /**
     * Get fitness options object for requesting permissions
     */
    fun getFitnessOptions(): Any
    
    /**
     * Get steps count for the current day
     */
    suspend fun getDailyStepCount(context: Context): List<ActivityData>
    
    /**
     * Get step counts for a specific time range
     */
    suspend fun getStepCount(context: Context, startTime: Long, endTime: Long): List<ActivityData>
    
    /**
     * Get user information
     */
    suspend fun getUserInfo(context: Context): UserInfo
} 