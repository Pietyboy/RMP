package com.example.rssreader.fit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MockFitApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mockServerBaseUrl = "http://10.0.2.2:3001" // For Android emulator
    // For physical device, use your computer's IP address:
    // private val mockServerBaseUrl = "http://192.168.1.X:3001"

    private val TAG = "MockFitApiService"

    suspend fun getAccessToken(authCode: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting access token with auth code: $authCode")
            Log.d(TAG, "Using server URL: $mockServerBaseUrl")
            
            val requestBody = JSONObject().apply {
                put("code", authCode)
                put("client_id", "mock_client_id")
                put("client_secret", "mock_client_secret")
                put("grant_type", "authorization_code")
            }.toString()

            val request = Request.Builder()
                .url("$mockServerBaseUrl/oauth2/v4/token")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Sending request to: ${request.url}")
            
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response body: $responseBody")

                if (!response.isSuccessful) {
                    throw Exception("Failed to get access token: ${response.code} - $responseBody")
                }

                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getString("access_token")
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Network error: Could not resolve host. Is the mock server running?", e)
                throw Exception("Could not connect to mock server. Please ensure it's running and accessible.")
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Network error: Connection refused. Is the mock server running on the correct port?", e)
                throw Exception("Could not connect to mock server. Please check if it's running on port 3001.")
            } catch (e: Exception) {
                Log.e(TAG, "Network error: ${e.message}", e)
                throw Exception("Network error: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            throw e
        }
    }

    suspend fun getUserInfo(accessToken: String): UserInfo = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting user info with token: $accessToken")
            val request = Request.Builder()
                .url("$mockServerBaseUrl/oauth2/v3/userinfo")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d(TAG, "User info response: $responseBody")

            if (!response.isSuccessful) {
                throw Exception("Failed to get user info: ${response.code}")
            }

            val jsonResponse = JSONObject(responseBody)
            UserInfo(
                id = jsonResponse.getString("id"),
                email = jsonResponse.getString("email"),
                name = jsonResponse.getString("name")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user info", e)
            throw e
        }
    }

    suspend fun getDataSources(accessToken: String): List<DataSource> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting data sources with token: $accessToken")
            val request = Request.Builder()
                .url("$mockServerBaseUrl/fitness/v1/users/me/dataSources")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d(TAG, "Data sources response: $responseBody")

            if (!response.isSuccessful) {
                throw Exception("Failed to get data sources: ${response.code}")
            }

            val jsonResponse = JSONObject(responseBody)
            val dataSourcesArray = jsonResponse.getJSONArray("dataSource")
            (0 until dataSourcesArray.length()).map { index ->
                val dataSource = dataSourcesArray.getJSONObject(index)
                DataSource(
                    id = dataSource.getString("dataStreamId"),
                    name = dataSource.getString("dataStreamName"),
                    type = dataSource.getString("type")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting data sources", e)
            throw e
        }
    }

    suspend fun getActivityData(accessToken: String, startTime: Long, endTime: Long): List<ActivityData> = 
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Requesting activity data with token: $accessToken")
                val requestBody = JSONObject().apply {
                    put("aggregateBy", JSONObject().apply {
                        put("dataTypeName", "com.google.step_count.delta")
                    })
                    put("bucketByTime", JSONObject().apply {
                        put("durationMillis", endTime - startTime)
                    })
                    put("startTimeMillis", startTime)
                    put("endTimeMillis", endTime)
                }.toString()

                val request = Request.Builder()
                    .url("$mockServerBaseUrl/fitness/v1/users/me/dataset:aggregate")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d(TAG, "Activity data response: $responseBody")

                if (!response.isSuccessful) {
                    throw Exception("Failed to get activity data: ${response.code}")
                }

                val jsonResponse = JSONObject(responseBody)
                val buckets = jsonResponse.getJSONArray("bucket")
                (0 until buckets.length()).map { index ->
                    val bucket = buckets.getJSONObject(index)
                    val dataset = bucket.getJSONArray("dataset").getJSONObject(0)
                    val point = dataset.getJSONArray("point").getJSONObject(0)
                    
                    ActivityData(
                        startTime = point.getLong("startTimeNanos") / 1000000,
                        endTime = point.getLong("endTimeNanos") / 1000000,
                        steps = point.getJSONArray("value").getJSONObject(0).getInt("intVal")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting activity data", e)
                throw e
            }
        }
}

data class UserInfo(
    val id: String,
    val email: String,
    val name: String
)

data class DataSource(
    val id: String,
    val name: String,
    val type: String
)

data class ActivityData(
    val startTime: Long,
    val endTime: Long,
    val steps: Int
) 