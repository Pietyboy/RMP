package com.example.rssreader.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
// Material library imports for pull-to-refresh
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
// Icon imports
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExitToApp
// DI import
import com.example.rssreader.di.ViewModelFactoryProvider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.StrokeCap

private const val TAG = "ActivityScreen"


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ActivityScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = viewModel(
        factory = ViewModelFactoryProvider.provideActivityViewModelFactory()
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var refreshing by remember { mutableStateOf(false) }
    
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            viewModel.checkPermissionsAndFetchData(context)
        }
    )
    
    LaunchedEffect(uiState) {
        if (uiState !is ActivityUiState.Loading && refreshing) {
            refreshing = false
        }
    }
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                viewModel.checkPermissionsAndFetchData(context)
            } catch (e: ApiException) {
                viewModel.handleError("Failed to sign in: ${e.message}")
            }
        } else {
            viewModel.handleError("Sign-in canceled or failed")
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.checkPermissionsAndFetchData(context)
        } else {
            viewModel.handleError("Permission request canceled or denied")
        }
    }
    
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            continueWithGoogleFitAuth(context, viewModel, signInLauncher)
        } else {
            viewModel.handleError("Activity recognition permission is required to track steps")
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.checkInitialState(context)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        when (val state = uiState) {
            is ActivityUiState.Initial -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Initializing...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            is ActivityUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Updating step data...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            is ActivityUiState.NeedsPermission -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            try {

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                                    activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                } else {
                                    continueWithGoogleFitAuth(context, viewModel, signInLauncher)
                                }
                            } catch (e: Exception) {
                                viewModel.handleError("Failed to start auth flow: ${e.message}")
                            }
                        }
                    ) {
                        Text("Connect to Google Fit")
                    }
                }
            }
            is ActivityUiState.Success -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            ActivityCard(
                                steps = state.steps,
                                dailyGoal = state.dailyGoal
                            )
                        }
                        
                        if (state.lastUpdated.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Last updated: ${state.lastUpdated}\n Steps ${state.dailyGoal}",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                        
                        item {
                            OutlinedButton(
                                onClick = {
                                    viewModel.signOut(context)
                                },
                                modifier = Modifier.padding(vertical = 16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Logout",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disconnect from Google Fit")
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
            is ActivityUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = {
                                viewModel.checkPermissionsAndFetchData(context)
                            }
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
        
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ActivityCard(
    steps: Int,
    dailyGoal: Int = 5000,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Активность",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFF2D55),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                val formattedSteps = String.format("%,d", steps)
                
                Text(
                    text = formattedSteps,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "/$dailyGoal шагов",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (steps / dailyGoal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFFFF9500),
                trackColor = Color(0xFFFFE5BF),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

private fun continueWithGoogleFitAuth(
    context: Context,
    viewModel: ActivityViewModel,
    signInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val account = GoogleSignIn.getLastSignedInAccount(context)
    
    if (account == null) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        
        val signInClient = GoogleSignIn.getClient(context, gso)
        signInLauncher.launch(signInClient.signInIntent)
    } else {
        val fitnessOptions = viewModel.getFitnessOptions()
        GoogleSignIn.requestPermissions(
            context as Activity,
            GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
            account,
            fitnessOptions
        )
    }
}

private const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001 