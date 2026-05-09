package com.example.timerapp

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.timerapp.ui.theme.AppTheme
import com.example.timerapp.ui.theme.TimerAppTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        setContent {
            var currentAppTheme by remember { mutableStateOf(AppTheme.Default) }
            var showOnboarding by remember { mutableStateOf(false) }
            
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            
            // Initialize Database
            val database = remember { SessionDatabase.getDatabase(context) }
            val repository = remember { SessionRepository(database.sessionDao()) }
            
            LaunchedEffect(Unit) {
                TimerState.repository = repository
                TimerState.scope = scope
            }

            // Check for first launch / missing permissions
            LaunchedEffect(Unit) {
                if (!hasUsageStatsPermission(context) || !hasOverlayPermission(context)) {
                    showOnboarding = true
                }
            }
            
            // Sync lockout service lifecycle
            LaunchedEffect(TimerState.isLockoutEnabled) {
                if (TimerState.isLockoutEnabled) {
                    LockoutService.startService(context)
                } else {
                    LockoutService.stopService(context)
                }
            }

            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    TimerState.isFocusAssistantEnabled = true
                }
            }

            TimerAppTheme(appTheme = currentAppTheme) {
                var currentScreen by remember { mutableStateOf("timer") }
                
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        "timer" -> {
                            TimerScreen(
                                onOpenSettings = { currentScreen = "settings" },
                                onOpenHistory = { currentScreen = "history" }
                            )
                        }
                        "settings" -> {
                            SettingsScreen(
                                currentTheme = currentAppTheme,
                                onThemeChange = { currentAppTheme = it },
                                onBack = { currentScreen = "timer" },
                                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                            )
                        }
                        "history" -> {
                            HistoryScreen(
                                repository = repository,
                                onBack = { currentScreen = "timer" }
                            )
                        }
                    }
                }

                if (showOnboarding) {
                    OnboardingDialog(
                        onDismiss = { showOnboarding = false },
                        onGrantUsage = { requestUsageStatsPermission(context) },
                        onGrantOverlay = { requestOverlayPermission(context) }
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "lockout_channel",
                "Timer App Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for focus mode and app lockout"
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to Timer App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("To use the Productivity Lockout feature, we need two special permissions:")
                Text("1. Usage Access: To see which apps are open.", style = MaterialTheme.typography.bodySmall)
                Text("2. Overlay: To block distracting apps.", style = MaterialTheme.typography.bodySmall)
                Text("You can grant them now or later in settings.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got it") }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onGrantUsage) { Text("Grant Usage") }
                TextButton(onClick = onGrantOverlay) { Text("Grant Overlay") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimeInputDialog by remember { mutableStateOf(false) }
    
    // Smooth progress animation
    val initialTimeMillis = (if (TimerState.isBreakMode) TimerState.breakTimeMinutes else TimerState.focusTimeMinutes) * 60000L
    val progress by animateFloatAsState(
        targetValue = if (initialTimeMillis > 0) TimerState.timeLeftMillis.toFloat() / initialTimeMillis.toFloat() else 0f,
        label = "TimerProgress",
    )

    // Local UI tick (the service handles the official sync)
    LaunchedEffect(TimerState.isRunning) {
        while (TimerState.isRunning) {
            delay(500L)
            TimerState.syncTime()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { 
                    Column {
                        Text(if (TimerState.isBreakMode) "Break Time" else "Focus Time")
                        if (TimerState.isFocusAssistantEnabled) {
                            Text(
                                text = "Assistant: ${TimerState.attentivenessStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (TimerState.isUserInattentive || TimerState.isPostureBad) 
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (TimerState.isFocusAssistantEnabled) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .size(120.dp, 160.dp)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    FocusAssistant(modifier = Modifier.fillMaxSize())
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(300.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round,
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (TimerState.isBreakMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round,
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { 
                        if (!TimerState.isRunning && !TimerState.isBreakMode) showTimeInputDialog = true 
                    }
                ) {
                    val hours = (TimerState.timeLeftMillis / 3600000)
                    val minutes = (TimerState.timeLeftMillis % 3600000) / 60000
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%02d".format(hours),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 72.sp
                                )
                            )
                            Text(
                                text = "hour",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 72.sp
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%02d".format(minutes),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 72.sp
                                )
                            )
                            Text(
                                text = "min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    if (!TimerState.isRunning && !TimerState.isBreakMode) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap to edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { TimerState.reset() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }

                Button(
                    onClick = { 
                        if (TimerState.isRunning) TimerState.pause() else TimerState.start()
                    },
                    modifier = Modifier.size(80.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = if (TimerState.isBreakMode) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors()
                ) {
                    Icon(
                        imageVector = if (TimerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (TimerState.isRunning) "Pause" else "Start",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }

    if (showTimeInputDialog) {
        TimeInputDialog(
            initialHours = (TimerState.focusTimeMinutes / 60),
            initialMinutes = (TimerState.focusTimeMinutes % 60),
            onDismiss = { showTimeInputDialog = false },
            onConfirm = { h, m ->
                TimerState.focusTimeMinutes = h * 60 + m
                TimerState.reset() // Update current time left
                showTimeInputDialog = false
            }
        )
    }
}

@Composable
fun TimeInputDialog(
    initialHours: Int,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hoursText by remember { mutableStateOf(initialHours.toString()) }
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Focus Duration") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hoursText = it },
                    label = { Text("Hours") },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Text(":", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineLarge)
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minutesText = it },
                    label = { Text("Mins") },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val h = hoursText.toIntOrNull() ?: 0
                val m = minutesText.toIntOrNull() ?: 0
                onConfirm(h, m.coerceIn(0, 59))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBack: () -> Unit,
    onRequestCamera: () -> Unit = {}
) {
    var showBreakDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val hasUsage = hasUsageStatsPermission(context)
    val hasOverlay = hasOverlayPermission(context)
    val permissionsGranted = hasUsage && hasOverlay

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("Settings") },
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
            // Theme Section
            Text("Theme", style = MaterialTheme.typography.titleLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = currentTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.name) }
                    )
                }
            }

            HorizontalDivider()

            // Timer Section
            Text("Timer Settings", style = MaterialTheme.typography.titleLarge)

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Break Mode")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = TimerState.isBreakEnabled, 
                        onCheckedChange = { TimerState.isBreakEnabled = it }
                    )
                }
                if (TimerState.isBreakEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        onClick = { showBreakDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Break Duration", style = MaterialTheme.typography.labelMedium)
                                Text("${TimerState.breakTimeMinutes} min", style = MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("Tap to change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        "Maximum break duration is 15 minutes",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            HorizontalDivider()

            // Lockout Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Productivity Lockout", style = MaterialTheme.typography.titleLarge)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!permissionsGranted) {
                                if (!hasUsage) requestUsageStatsPermission(context)
                                else if (!hasOverlay) requestOverlayPermission(context)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Strict Lockout",
                            color = if (permissionsGranted) MaterialTheme.colorScheme.onBackground 
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                        )
                        if (!permissionsGranted) {
                            Text(
                                text = "Tap to grant permissions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value = if (TimerState.isLockoutEnabled && permissionsGranted) 1f else 0f,
                        onValueChange = { 
                            if (permissionsGranted) {
                                TimerState.isLockoutEnabled = it > 0.5f
                            } else {
                                if (!hasUsage) requestUsageStatsPermission(context)
                                else requestOverlayPermission(context)
                            }
                        },
                        valueRange = 0f..1f,
                        steps = 0,
                        modifier = Modifier.width(64.dp),
                        enabled = permissionsGranted
                    )
                }
                
                Text(
                    "Blocks distracting apps except during break periods. Requires special permissions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.alpha(if (permissionsGranted) 1f else 0.5f)
                )

                if (!permissionsGranted) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { requestUsageStatsPermission(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (hasUsage) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (hasUsage) "Usage Access: Granted" else "Grant Usage Access")
                    }

                    Button(
                        onClick = { requestOverlayPermission(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (hasOverlay) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (hasOverlay) "Overlay: Granted" else "Grant Overlay Permission")
                    }
                }
            }

            HorizontalDivider()

            // Focus Assistant Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                
                Text("Focus Assistant", style = MaterialTheme.typography.titleLarge)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Posture & Attentiveness")
                        Text(
                            "Monitors you via camera and nudges if you slump or look away.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = TimerState.isFocusAssistantEnabled,
                        onCheckedChange = { 
                            if (hasCamera) {
                                TimerState.isFocusAssistantEnabled = it
                            } else {
                                onRequestCamera()
                            }
                        }
                    )
                }

                if (TimerState.isFocusAssistantEnabled) {
                    Column {
                        Text("Sensitivity", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = TimerState.postureSensitivity,
                            onValueChange = { TimerState.postureSensitivity = it },
                            valueRange = 0f..1f
                        )
                    }
                }

                if (!hasCamera) {
                    Button(
                        onClick = { onRequestCamera() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Camera Permission")
                    }
                }
            }
        }
    }

    if (showBreakDialog) {
        var tempBreakTime by remember { mutableStateOf(TimerState.breakTimeMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            title = { Text("Set Break Duration") },
            text = {
                OutlinedTextField(
                    value = tempBreakTime,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) tempBreakTime = it },
                    label = { Text("Minutes (Max 15)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val b = tempBreakTime.toIntOrNull() ?: 0
                    TimerState.breakTimeMinutes = b.coerceIn(1, 15)
                    showBreakDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showBreakDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun requestUsageStatsPermission(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun requestOverlayPermission(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
fun TimerPreview() {
    TimerAppTheme {
        TimerScreen(onOpenSettings = {}, onOpenHistory = {})
    }
}
