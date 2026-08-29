package com.example.douyincontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class AccessibilityControlService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        private var sessionTimer: Timer? = null
        private var sessionStartTime = 0L
        private var isBlocking = false
        private var prefs: SharedPreferences? = null

        fun checkAndBlock() {
            // 由 TimerService 调用，检查当前是否在使用抖音
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "无障碍服务已连接")
        prefs = applicationContext.getSharedPreferences("douyin_prefs", Context.MODE_PRIVATE)
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "窗口状态变化: $packageName")

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (packageName.contains("douyin", ignoreCase = true) ||
                    packageName == DOUYIN_PACKAGE) {
                    onDouyinFocused()
                }
            }
        }
    }

    private fun onDouyinFocused() {
        if (!TimerService.isRunning) return

        val allowMinutes = prefs?.getInt("allow_minutes", 15) ?: 15
        Log.d(TAG, "抖音会话开始，限制时长: ${allowMinutes}分钟")

        if (!isBlocking) {
            isBlocking = true
            sessionStartTime = System.currentTimeMillis()

            sessionTimer = Timer("SessionTracker", true).apply {
                scheduleAtFixedRate(object : java.util.TimerTask() {
                    override fun run() {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        val allowMillis = (allowMinutes * 60 * 1000L)

                        if (elapsed >= allowMillis) {
                            Log.d(TAG, "抖音使用超时，执行拦截")
                            blockApp()
                            isBlocking = false
                            sessionTimer?.cancel()
                            sessionTimer = null
                        }
                    }
                }, 1000, 1000)
            }
        }
    }

    private fun blockApp() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "已返回首页")
        } catch (e: Exception) {
            Log.e(TAG, "拦截失败", e)
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
            .setContentText("正在监控应用使用情况")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1002, notification)
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务中断")
        sessionTimer?.cancel()
        sessionTimer = null
    }

    override fun onDestroy() {
        sessionTimer?.cancel()
        super.onDestroy()
    }
}
