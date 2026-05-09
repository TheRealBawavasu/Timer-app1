package com.example.timerapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: SessionRepository,
    onBack: () -> Unit
) {
    val sessions by repository.allSessions.collectAsState(initial = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("Session History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Activity (Last 7 Days)", style = MaterialTheme.typography.titleLarge)
            
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No sessions recorded yet.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    DotPlot(
                        sessions = sessions,
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    )
                }
            }

            HorizontalDivider()

            Text("All Sessions", style = MaterialTheme.typography.titleLarge)
            
            sessions.forEach { session ->
                SessionItem(session)
            }
        }
    }
}

@Composable
fun DotPlot(
    sessions: List<SessionEntity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // X-axis: Time (Last 7 days)
        // Y-axis: Duration (Minutes)
        val maxDuration = sessions.maxByOrNull { it.durationMinutes }?.durationMinutes?.toFloat() ?: 60f
        val maxY = (maxDuration + 10f).coerceAtLeast(60f)

        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)

        // Draw Axes
        drawLine(
            color = secondaryColor,
            start = Offset(0f, height),
            end = Offset(width, height),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = secondaryColor,
            start = Offset(0f, 0f),
            end = Offset(0f, height),
            strokeWidth = 2.dp.toPx()
        )

        // Draw Dots
        sessions.filter { it.timestamp >= sevenDaysAgo }.forEach { session ->
            val xRatio = (session.timestamp - sevenDaysAgo).toFloat() / (now - sevenDaysAgo).toFloat()
            val yRatio = session.durationMinutes.toFloat() / maxY

            val x = xRatio * width
            val y = height - (yRatio * height)

            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun SessionItem(session: SessionEntity) {
    val date = remember(session.timestamp) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(date, style = MaterialTheme.typography.bodyMedium)
            Text("Focus Session", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            "${session.durationMinutes} min",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}
