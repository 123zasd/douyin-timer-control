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
        const val CHANNEL_ID = "timer_channel_v2"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning = false
            private set
    }

    private var timer: Timer? = null
    private var periodMillis = 60L * 60 * 1000L
    private var allowMillis = 15L * 60 * 1000L
    private var periodStartTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                periodMillis = intent.getLongExtra(EXTRA_PERIOD, 60L * 60 * 1000L)
                allowMillis = intent.getLongExtra(EXTRA_ALLOW, 15L * 60 * 1000L)
                startTimer()
            }
            ACTION_STOP -> stopTimer()
            ACTION_RESET -> resetTimer()
        }
        return START_STICKY
    }

    private fun startTimer() {
        periodStartTime = System.currentTimeMillis()
        isRunning = true

        mainHandler.post {
            startForeground(NOTIFICATION_ID, buildNotification("管控已启动"))
        }

        timer?.cancel()
        timer = Timer("DouyinTimer", true)
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val elapsed = System.currentTimeMillis() - periodStartTime
                    val secondsInPeriod = elapsed / 1000
                    val secondsAllowed = allowMillis / 1000

                    if (secondsInPeriod >= secondsAllowed) {
                        // 进入拦截阶段，通知无障碍服务
                        mainHandler.post {
                            try {
                                AccessibilityControlService.checkAndBlock()
                            } catch (e: Exception) {
                                Log.e("TimerService", "checkAndBlock error", e)
                            }
                        }
                    }

                    if (secondsInPeriod >= periodMillis / 1000) {
                        periodStartTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e("TimerService", "TimerTask error", e)
                }
            }
        }, 1000, 1000)

        Log.d("TimerService", "Started: period=${periodMillis}ms allow=${allowMillis}ms")
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun resetTimer() {
        periodStartTime = System.currentTimeMillis()
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("抖音管控")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "管控通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "抖音使用时间管控"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        isRunning = false
        super.onDestroy()
    }
}
