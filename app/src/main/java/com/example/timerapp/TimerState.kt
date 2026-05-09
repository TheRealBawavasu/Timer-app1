package com.example.timerapp

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

object TimerState {
    var focusTimeMinutes by mutableIntStateOf(30)
    var breakTimeMinutes by mutableIntStateOf(5)
    var isBreakEnabled by mutableStateOf(true)
    var isLockoutEnabled by mutableStateOf(false)
    
    // Focus Assistant Settings
    var isFocusAssistantEnabled by mutableStateOf(false)
    var postureSensitivity by mutableFloatStateOf(0.5f)
    var attentivenessStatus by mutableStateOf("Ready")
    var isUserInattentive by mutableStateOf(false)
    var isPostureBad by mutableStateOf(false)
    
    var isRunning by mutableStateOf(false)
    var isBreakMode by mutableStateOf(false)
    
    var timeLeftMillis by mutableLongStateOf(30 * 60000L)
    var lastUpdateTimeMillis by mutableLongStateOf(0L)
    
    var repository: SessionRepository? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null

    fun syncTime() {
        if (isRunning && lastUpdateTimeMillis > 0) {
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastUpdateTimeMillis
            timeLeftMillis = (timeLeftMillis - elapsed).coerceAtLeast(0L)
            lastUpdateTimeMillis = currentTime
            
            if (timeLeftMillis <= 0) {
                if (!isBreakMode) {
                    // Log focus session
                    val duration = focusTimeMinutes
                    scope?.let {
                        val repo = repository
                        if (repo != null) {
                            it.launch(kotlinx.coroutines.Dispatchers.IO) {
                                repo.insert(SessionEntity(timestamp = System.currentTimeMillis(), durationMinutes = duration))
                            }
                        }
                    }
                }

                if (isBreakEnabled && !isBreakMode) {
                    isBreakMode = true
                    timeLeftMillis = breakTimeMinutes * 60000L
                } else {
                    isRunning = false
                    isBreakMode = false
                    timeLeftMillis = focusTimeMinutes * 60000L
                    lastUpdateTimeMillis = 0
                }
            }
        }
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            lastUpdateTimeMillis = System.currentTimeMillis()
        }
    }

    fun pause() {
        if (isRunning) {
            syncTime()
            isRunning = false
            lastUpdateTimeMillis = 0
        }
    }

    fun reset() {
        isRunning = false
        isBreakMode = false
        timeLeftMillis = focusTimeMinutes * 60000L
        lastUpdateTimeMillis = 0
    }
}
