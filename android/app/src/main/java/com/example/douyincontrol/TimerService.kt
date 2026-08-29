package com.example.douyincontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask

class TimerService : Service() {

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val ACTION_RESET = "action_reset"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_ALLOW = "extra_allow"
        const val CHANNEL_ID = "timer_service_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning = false
            private set
    }

    private var timer: Timer? = null
    private var periodMillis = 60L * 60 * 1000L
    private var allowMillis = 15L * 60 * 1000L
    private var periodStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    periodMillis = intent.getLongExtra(EXTRA_PERIOD, 60L * 60 * 1000L)
                    allowMillis = intent.getLongExtra(EXTRA_ALLOW, 15L * 60 * 1000L)
                    startTimer()
                }
                ACTION_STOP -> stopTimer()
                ACTION_RESET -> resetTimer()
                else -> {
                    // Default: start with defaults
                    startTimer()
                }
            }
        } catch (e: Exception) {
            Log.e("TimerService", "onStartCommand failed", e)
        }
        return START_STICKY
    }

    private fun startTimer() {
        try {
            periodStartTime = System.currentTimeMillis()
            isRunning = true
            startNotification()

            timer?.cancel()
            timer = Timer("DouyinTimer", true)
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        val elapsed = System.currentTimeMillis() - periodStartTime
                        val secondsInPeriod = elapsed / 1000
                        val secondsAllowed = allowMillis / 1000

                        if (secondsInPeriod >= secondsAllowed) {
                            // In blocked window - trigger accessibility check
                            handler.post {
                                try {
                                    AccessibilityControlService.checkAndBlock()
                                } catch (e: Exception) {
                                    Log.e("TimerService", "checkAndBlock failed", e)
                                }
                            }
                        }

                        // Period reset
                        if (secondsInPeriod >= periodMillis / 1000) {
                            periodStartTime = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        Log.e("TimerService", "TimerTask failed", e)
                    }
                }
            }, 1000, 1000)

            Log.d("TimerService", "Timer started: period=${periodMillis}ms, allow=${allowMillis}ms")
        } catch (e: Exception) {
            Log.e("TimerService", "startTimer failed", e)
        }
    }

    private fun stopTimer() {
        try {
            timer?.cancel()
            timer = null
            isRunning = false
            stopForeground(true)
            stopSelf()
            Log.d("TimerService", "Timer stopped")
        } catch (e: Exception) {
            Log.e("TimerService", "stopTimer failed", e)
        }
    }

    private fun resetTimer() {
        try {
            periodStartTime = System.currentTimeMillis()
            Log.d("TimerService", "Timer reset")
        } catch (e: Exception) {
            Log.e("TimerService", "resetTimer failed", e)
        }
    }

    private fun startNotification() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("抖音管控已启动")
                .setContentText("按设定规则限制使用")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("TimerService", "startNotification failed", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定时服务通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示管控服务状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e("TimerService", "createNotificationChannel failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            timer?.cancel()
            timer = null
            isRunning = false
        } catch (e: Exception) {
            Log.e("TimerService", "onDestroy failed", e)
        }
    }
}
