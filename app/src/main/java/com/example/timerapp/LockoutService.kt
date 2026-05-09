package com.example.timerapp

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.*

class LockoutService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L // Check every second

    companion object {
        var isFocusModeActive = false
        var isServiceRunning = false
        
        fun startService(context: Context) {
            val intent = Intent(context, LockoutService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LockoutService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "lockout_channel")
            .setContentTitle("Lockout Mode Active")
            .setContentText("Focusing on work...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        startForeground(1, notification)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "lockout_channel",
                "App Lockout Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                if (isFocusModeActive) {
                    checkForegroundApp()
                } else {
                    hideOverlay()
                }
                handler.postDelayed(this, checkInterval)
            }
        })
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // Last minute
        
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (stats != null) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            if (sortedStats.isNotEmpty()) {
                val topApp = sortedStats[0].packageName
                if (isAppBlocked(topApp)) {
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        }
    }

    private fun isAppBlocked(packageName: String): Boolean {
        // Whitelist: Our app, Phone, Dialer, System UI
        val whitelist = listOf(
            packageName, // Should check if it's our own
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.systemui",
            "com.example.timerapp"
        )
        return packageName !in whitelist
    }

    private fun showOverlay() {
        if (overlayView != null) return

        handler.post {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            overlayView = LayoutInflater.from(this).inflate(R.layout.layout_lock_overlay, null)
            overlayView?.findViewById<Button>(R.id.btn_back_to_app)?.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // Ignore
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
    }
}
