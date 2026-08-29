package com.example.douyincontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class TimerService : Service() {

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val ACTION_RESET = "action_reset"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_ALLOW = "extra_allow"
        const val CHANNEL_ID = "timer_service_channel"
        const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set
    }

    private var timer: Timer? = null
    private var periodMillis = 60L * 60 * 1000L
    private var allowMillis = 15L * 60 * 1000L
    private var periodStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_STOP -> stopTimer()
            ACTION_RESET -> resetTimer()
        }
        return START_STICKY
    }

    private fun startTimer() {
        periodStartTime = System.currentTimeMillis()
        isRunning = true
        startNotification()

        timer = Timer("DouyinTimer", true).apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - periodStartTime
                    val secondsInPeriod = (elapsed / 1000).toInt()
                    val secondsAllowed = allowMillis / 1000

                    // 当进入拦截阶段时，触发无障碍服务检查
                    if (secondsInPeriod >= secondsAllowed) {
                        AccessibilityControlService.checkAndBlock()
                    }

                    // 周期重置
                    if (secondsInPeriod >= periodMillis / 1000) {
                        periodStartTime = System.currentTimeMillis()
                    }
                }
            }, 1000, 1000)
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        isRunning = false
        stopForeground(true)
        stopSelf()
    }

    private fun resetTimer() {
        periodStartTime = System.currentTimeMillis()
    }

    private fun startNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("抖音管控已启动")
            .setContentText("按设定规则限制使用")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定时服务通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示管控服务状态"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
