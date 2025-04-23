package com.example.rssreader.fit

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SensorRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FitApiService {
    private val TAG = "FitApiService"
    
    // Define the fitness options we need - include read AND write
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .build()
    
    // Check if the user has granted permissions
    fun hasPermissions(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)
    }
    
    // Get fitness options for requesting permissions
    fun getFitnessOptions(): FitnessOptions {
        return fitnessOptions
    }
    
    // Get daily steps
    suspend fun getDailyStepCount(context: Context): List<ActivityData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting daily step count data")
            
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: throw Exception("Not signed in to Google Account")
            
            // Time range for today (midnight to now)
            val endTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = endTime
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            Log.d(TAG, "Requesting steps from ${startTime} to ${endTime}")
            
            // First try the History API (aggregate data)
            try {
                val historyData = getStepHistoryData(context, account, startTime, endTime)
                if (historyData.isNotEmpty() && historyData[0].steps > 0) {
                    Log.d(TAG, "Successfully retrieved step data from History API")
                    return@withContext historyData
                } else {
                    Log.d(TAG, "No steps found in History API, will try session data next")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from History API, will try session data", e)
            }
            
            // If history data doesn't have steps, try reading the session data
            val sessionData = getSessionStepData(context, account, startTime, endTime)
            if (sessionData.isNotEmpty() && sessionData[0].steps > 0) {
                Log.d(TAG, "Successfully retrieved step data from Sessions API")
                return@withContext sessionData
            }
            
            // If all else fails, try getting sensor data directly
            try {
                val sensorData = getSensorStepData(context, account)
                if (sensorData > 0) {
                    Log.d(TAG, "Using step count from sensor data: $sensorData")
                    return@withContext listOf(ActivityData(startTime, endTime, sensorData))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from Sensors API", e)
            }
            
            // If we still have no data, check if any data exists for today from any source
            val lastResortData = getAnyStepData(context, account, startTime, endTime)
            if (lastResortData.isNotEmpty()) {
                return@withContext lastResortData
            }
            
            // If we got here, there's truly no step data
            return@withContext listOf(ActivityData(startTime, endTime, 0))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daily step count", e)
            throw e
        }
    }
    
    private suspend fun getStepHistoryData(
        context: Context, 
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        startTime: Long, 
        endTime: Long
    ): List<ActivityData> {
        // Build the data request for history/aggregate data
        val dataRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()
            
        // Make the request to Google Fit API
        val response = Fitness.getHistoryClient(context, account)
            .readData(dataRequest)
            .await()
            
        Log.d(TAG, "Received response from History API: ${response.buckets.size} buckets")
        
        // Process the data
        val activityDataList = mutableListOf<ActivityData>()
        
        var totalSteps = 0
        
        response.buckets.flatMap { bucket ->
            Log.d(TAG, "Processing bucket: ${bucket.getStartTime(TimeUnit.MILLISECONDS)} - ${bucket.getEndTime(TimeUnit.MILLISECONDS)}")
            bucket.dataSets
        }.forEach { dataSet ->
            Log.d(TAG, "Processing dataset: ${dataSet.dataType.name}, points: ${dataSet.dataPoints.size}")
            dataSet.dataPoints.forEach { point ->
                val pointStartTime = point.getStartTime(TimeUnit.MILLISECONDS)
                val pointEndTime = point.getEndTime(TimeUnit.MILLISECONDS)
                val steps = point.getValue(Field.FIELD_STEPS).asInt()
                
                Log.d(TAG, "DataPoint: $pointStartTime - $pointEndTime, Steps: $steps")
                totalSteps += steps
            }
        }
        
        // Create a single activity data object with the total step count
        activityDataList.add(
            ActivityData(
                startTime = startTime,
                endTime = endTime,
                steps = totalSteps
            )
        )
        
        return activityDataList
    }
    
    private suspend fun getSessionStepData(
        context: Context, 
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        startTime: Long,
        endTime: Long
    ): List<ActivityData> {
        try {
            // Read from the step count delta type directly
            val dataSet = Fitness.getHistoryClient(context, account)
                .readData(
                    DataReadRequest.Builder()
                        .read(DataType.TYPE_STEP_COUNT_DELTA)
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build()
                )
                .await()
                
            Log.d(TAG, "Session data response: ${dataSet.dataSets.size} datasets")
            
            var totalSteps = 0
            
            dataSet.dataSets.forEach { ds ->
                Log.d(TAG, "Dataset: ${ds.dataType.name}, points: ${ds.dataPoints.size}")
                ds.dataPoints.forEach { point ->
                    val steps = point.getValue(Field.FIELD_STEPS).asInt()
                    Log.d(TAG, "Session data point: ${point.getStartTime(TimeUnit.MILLISECONDS)} - ${point.getEndTime(TimeUnit.MILLISECONDS)}, Steps: $steps")
                    totalSteps += steps
                }
            }
            
            return listOf(ActivityData(startTime, endTime, totalSteps))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session step data", e)
            return emptyList()
        }
    }
    
    private suspend fun getSensorStepData(
        context: Context,
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount
    ): Int {
        // Try to read from sensors directly
        try {
            val stepCounterDataSource = DataSource.Builder()
                .setType(DataSource.TYPE_RAW)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setAppPackageName("com.google.android.gms")
                .setStreamName("step_count_delta")
                .build()
                
            // First try to read today's total
            val total = Fitness.getHistoryClient(context, account)
                .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                .await()
                
            var stepCount = 0
            if (total.dataPoints.isNotEmpty()) {
                stepCount = total.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()
                Log.d(TAG, "Found $stepCount steps from daily total")
            }
            
            return stepCount
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sensor step data", e)
            return 0
        }
    }
    
    private suspend fun getAnyStepData(
        context: Context,
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        startTime: Long,
        endTime: Long
    ): List<ActivityData> {
        try {
            // Try to get summary data for today (cumulative step count)
            val total = Fitness.getHistoryClient(context, account)
                .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                .await()
                
            var stepCount = 0
            if (total.dataPoints.isNotEmpty()) {
                stepCount = total.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()
                Log.d(TAG, "Daily total step count: $stepCount")
                return listOf(ActivityData(startTime, endTime, stepCount))
            }
            
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting any step data", e)
            return emptyList()
        }
    }
    
    // Get step count for a specific time range (with week/month breakdowns)
    suspend fun getStepCount(context: Context, startTime: Long, endTime: Long): List<ActivityData> = 
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Requesting step count from $startTime to $endTime")
                
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: throw Exception("Not signed in to Google Account")
                
                // Build the data request with daily buckets
                val dataRequest = DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build()
                
                // Make the request to Google Fit API
                val response = Fitness.getHistoryClient(context, account)
                    .readData(dataRequest)
                    .await()
                
                Log.d(TAG, "Received response for time range")
                
                // Process the data - create an ActivityData for each day bucket
                val activityDataList = mutableListOf<ActivityData>()
                
                response.buckets.forEach { bucket ->
                    var dailySteps = 0
                    bucket.dataSets.forEach { dataSet ->
                        dataSet.dataPoints.forEach { point ->
                            val steps = point.getValue(Field.FIELD_STEPS).asInt()
                            dailySteps += steps
                        }
                    }
                    
                    // Only add non-zero entries to make the list cleaner
                    if (dailySteps > 0) {
                        activityDataList.add(
                            ActivityData(
                                startTime = bucket.getStartTime(TimeUnit.MILLISECONDS),
                                endTime = bucket.getEndTime(TimeUnit.MILLISECONDS),
                                steps = dailySteps
                            )
                        )
                    }
                }
                
                if (activityDataList.isEmpty()) {
                    // If after processing, there's still no data, add a zero entry
                    activityDataList.add(ActivityData(startTime, endTime, 0))
                }
                
                activityDataList
            } catch (e: Exception) {
                Log.e(TAG, "Error getting step count", e)
                throw e
            }
        }
    
    // Get user information
    suspend fun getUserInfo(context: Context): UserInfo = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: throw Exception("Not signed in to Google Account")
            
            UserInfo(
                id = account.id ?: "",
                email = account.email ?: "",
                name = account.displayName ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user info", e)
            throw e
        }
    }
} 