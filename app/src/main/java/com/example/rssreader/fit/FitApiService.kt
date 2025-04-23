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
            

            // First try the History API (aggregate data)
            try {
                val historyData = getStepHistoryData(context, account, startTime, endTime)
                if (historyData.isNotEmpty() && historyData[0].steps > 0) {
                    return@withContext historyData
                } else {
                }
            } catch (e: Exception) {
            }
            
            // If history data doesn't have steps, try reading the session data
            val sessionData = getSessionStepData(context, account, startTime, endTime)
            if (sessionData.isNotEmpty() && sessionData[0].steps > 0) {
                return@withContext sessionData
            }
            
            // If all else fails, try getting sensor data directly
            try {
                val sensorData = getSensorStepData(context, account)
                if (sensorData > 0) {
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
        val dataRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()
            
        val response = Fitness.getHistoryClient(context, account)
            .readData(dataRequest)
            .await()

        val activityDataList = mutableListOf<ActivityData>()
        
        var totalSteps = 0
        
        response.buckets.flatMap { bucket ->
            bucket.dataSets
        }.forEach { dataSet ->
            dataSet.dataPoints.forEach { point ->
                val steps = point.getValue(Field.FIELD_STEPS).asInt()
                
                totalSteps += steps
            }
        }
        
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
            val dataSet = Fitness.getHistoryClient(context, account)
                .readData(
                    DataReadRequest.Builder()
                        .read(DataType.TYPE_STEP_COUNT_DELTA)
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build()
                )
                .await()
                

            var totalSteps = 0
            
            dataSet.dataSets.forEach { ds ->
                ds.dataPoints.forEach { point ->
                    val steps = point.getValue(Field.FIELD_STEPS).asInt()
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
        try {
            val stepCounterDataSource = DataSource.Builder()
                .setType(DataSource.TYPE_RAW)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setAppPackageName("com.google.android.gms")
                .setStreamName("step_count_delta")
                .build()
                
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
            val total = Fitness.getHistoryClient(context, account)
                .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                .await()
                
            var stepCount = 0
            if (total.dataPoints.isNotEmpty()) {
                stepCount = total.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()
                return listOf(ActivityData(startTime, endTime, stepCount))
            }
            
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting any step data", e)
            return emptyList()
        }
    }
    
    suspend fun getStepCount(context: Context, startTime: Long, endTime: Long): List<ActivityData> =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: throw Exception("Not signed in to Google Account")
                
                val dataRequest = DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build()
                
                val response = Fitness.getHistoryClient(context, account)
                    .readData(dataRequest)
                    .await()
                
                Log.d(TAG, "Received response for time range")
                
                val activityDataList = mutableListOf<ActivityData>()
                
                response.buckets.forEach { bucket ->
                    var dailySteps = 0
                    bucket.dataSets.forEach { dataSet ->
                        dataSet.dataPoints.forEach { point ->
                            val steps = point.getValue(Field.FIELD_STEPS).asInt()
                            dailySteps += steps
                        }
                    }
                    
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
                    activityDataList.add(ActivityData(startTime, endTime, 0))
                }
                
                activityDataList
            } catch (e: Exception) {
                Log.e(TAG, "Error getting step count", e)
                throw e
            }
        }
    
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