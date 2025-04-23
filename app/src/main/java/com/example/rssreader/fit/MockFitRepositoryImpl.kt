package com.example.rssreader.fit

import android.content.Context
import com.google.android.gms.fitness.FitnessOptions

/**
 * Mock implementation of the FitRepository for testing
 */
class MockFitRepositoryImpl(private val mockFitApiService: MockFitApiService) : FitRepository {
    
    override fun hasPermissions(context: Context): Boolean {
        // Always return true for testing
        return true
    }
    
    override fun getFitnessOptions(): FitnessOptions {
        return FitnessOptions.builder().build()
    }
    
    override suspend fun getDailyStepCount(context: Context): List<ActivityData> {
        return listOf(ActivityData(System.currentTimeMillis() - 86400000, System.currentTimeMillis(), 7865))
    }
    
    override suspend fun getStepCount(context: Context, startTime: Long, endTime: Long): List<ActivityData> {
        return mockFitApiService.getActivityData(
            accessToken = "mock_token",
            startTime = startTime,
            endTime = endTime
        )
    }
    
    override suspend fun getUserInfo(context: Context): UserInfo {
        return mockFitApiService.getUserInfo("mock_token")
    }
} 