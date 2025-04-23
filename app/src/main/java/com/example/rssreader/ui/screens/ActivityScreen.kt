package com.example.rssreader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class ActivityUiState(
    val steps: Int = 0,
    val dailyGoal: Int = 5000
)

class ActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState(
        steps = 4900,
        dailyGoal = 10000
    ))
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    fun updateSteps(steps: Int) {
        _uiState.value = _uiState.value.copy(steps = steps)
    }

    fun updateDailyGoal(goal: Int) {
        _uiState.value = _uiState.value.copy(dailyGoal = goal)
    }
}

private fun formatNumber(number: Int): String {
    val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
        groupingSeparator = ','
    }
    return DecimalFormat("#,###", symbols).format(number)
}

//@Composable
//fun ActivityScreen(
//    viewModel: ActivityViewModel = viewModel()
//) {
//    val uiState by viewModel.uiState.collectAsState()
//
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(0.dp),
//        contentAlignment = Alignment.TopCenter
//    ) {
//        ActivityCard(
//            steps = uiState.steps,
//            goal = uiState.dailyGoal
//        )
//    }
//}

@Composable
fun ActivityCard(
    steps: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Активность",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formatNumber(steps),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "/${formatNumber(goal)} шагов",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFFFF9500),
                trackColor = Color(0xFFFFE5BF),
                strokeCap = StrokeCap.Round
            )
        }
    }
} 