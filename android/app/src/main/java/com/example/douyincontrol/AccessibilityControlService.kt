package com.example.douyincontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask

class AccessibilityControlService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        private var sessionTimer: Timer? = null
        private var sessionStartTime = 0L
        private var isBlocking = false
        private var prefsInstance: SharedPreferences? = null

        @Volatile
        var isServiceConnected = false
            private set

        fun checkAndBlock() {
            // Guard: only block if service is connected and running
            if (!isServiceConnected || prefsInstance == null) {
                Log.w(TAG, "checkAndBlock called before service connected, ignoring")
                return
            }
            // The actual blocking is handled by the service's TimerTask
            // This is just a no-op guard call
        }
    }

    override fun onServiceConnected() {
        isServiceConnected = true
        prefsInstance = applicationContext.getSharedPreferences("douyin_prefs", Context.MODE_PRIVATE)
        Log.d(TAG, "AccessibilityService connected, prefs initialized")

        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (packageName == DOUYIN_PACKAGE || packageName.contains("douyin", ignoreCase = true)) {
                    onDouyinFocused()
                }
            }
        }
    }

    private fun onDouyinFocused() {
        if (!TimerService.isRunning) return

        val prefs = prefsInstance ?: return
        val allowMinutes = prefs.getInt("allow_minutes", 15)
        Log.d(TAG, "Douyin focused, allow $allowMinutes minutes")

        if (isBlocking) return
        isBlocking = true
        sessionStartTime = System.currentTimeMillis()

        sessionTimer?.cancel()
        sessionTimer = Timer("SessionTracker", true)
        sessionTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val elapsed = System.currentTimeMillis() - sessionStartTime
                val allowMillis = allowMinutes * 60 * 1000L

                if (elapsed >= allowMillis) {
                    Log.d(TAG, "Time limit reached, blocking Douyin")
                    blockApp()
                    isBlocking = false
                    sessionTimer?.cancel()
                    sessionTimer = null
                }
            }
        }, 1000, 1000)
    }

    private fun blockApp() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "Returned to home screen")
        } catch (e: Exception) {
            Log.e(TAG, "Block failed", e)
        }
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            "accessibility_channel",
            "无障碍服务通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "管控服务运行状态"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, "accessibility_channel")
            .setContentTitle("抖音管控服务")
            .setContentText("正在监控抖音使用")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1002, notification)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        sessionTimer?.cancel()
        sessionTimer = null
        isBlocking = false
    }

    override fun onDestroy() {
        isServiceConnected = false
        prefsInstance = null
        sessionTimer?.cancel()
        sessionTimer = null
        isBlocking = false
        super.onDestroy()
    }
}
