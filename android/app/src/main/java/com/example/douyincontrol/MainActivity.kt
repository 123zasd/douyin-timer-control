package com.example.douyincontrol

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var btnStartService: Button
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvSchedule: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnOpenSettings: Button
    private lateinit var btnOpenOverlaySettings: Button
    private lateinit var btnSettings: Button
    private lateinit var btnResetSchedule: Button

    private var lastStatusUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(TAG, "onCreate start")
            super.onCreate(savedInstanceState)

            setContentView(R.layout.activity_main)

            val titleView = findViewById<TextView>(R.id.tv_title)
            if (titleView != null) {
                titleView.text = "抖音时间管控"
            }

            bindViews()
            setupListeners()
            checkPermissions()
            updateUI()

            Log.d(TAG, "onCreate complete")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate FAILED", e)
            // Don't crash - show a simple layout
            setContentView(android.R.layout.simple_list_item_1)
            val tv = findViewById<TextView>(android.R.id.text1)
            tv?.text = "初始化失败: ${e.message}"
        }
    }

    private fun bindViews() {
        try {
            btnStartService = findViewById(R.id.btn_start_service)
            tvServiceStatus = findViewById(R.id.tv_service_status)
            tvSchedule = findViewById(R.id.tv_schedule)
            tvTimer = findViewById(R.id.tv_timer)
            tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
            tvOverlayStatus = findViewById(R.id.tv_overlay_status)
            btnOpenSettings = findViewById(R.id.btn_open_settings)
            btnOpenOverlaySettings = findViewById(R.id.btn_open_overlay_settings)
            btnSettings = findViewById(R.id.btn_settings)
            btnResetSchedule = findViewById(R.id.btn_reset_schedule)
            Log.d(TAG, "Views bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "bindViews FAILED", e)
        }
    }

    private fun setupListeners() {
        try {
            btnStartService.setOnClickListener {
                Log.d(TAG, "btnStartService clicked")
                if (TimerService.isRunning) {
                    stopTimerService()
                } else {
                    startTimerService()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "btnStartService listener FAILED", e)
        }

        try {
            btnOpenSettings.setOnClickListener {
                Log.d(TAG, "btnOpenSettings clicked")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        } catch (e: Exception) {
            Log.e(TAG, "btnOpenSettings listener FAILED", e)
        }

        try {
            btnOpenOverlaySettings.setOnClickListener {
                Log.d(TAG, "btnOpenOverlaySettings clicked")
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "btnOpenOverlaySettings listener FAILED", e)
        }

        try {
            btnResetSchedule.setOnClickListener {
                Log.d(TAG, "btnResetSchedule clicked")
                AlertDialog.Builder(this)
                    .setTitle("重置定时")
                    .setMessage("确定要重置定时吗？")
                    .setPositiveButton("确定") { _, _ ->
                        val intent = Intent(this, TimerService::class.java)
                        intent.action = TimerService.ACTION_RESET
                        startService(intent)
                        Toast.makeText(this, "定时已重置", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "btnResetSchedule listener FAILED", e)
        }

        try {
            btnSettings.setOnClickListener {
                Log.d(TAG, "btnSettings clicked")
                showSettingsDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "btnSettings listener FAILED", e)
        }

        Log.d(TAG, "All listeners setup complete")
    }

    private fun checkPermissions() {
        try {
            val accessibilityEnabled = isAccessibilityServiceEnabled()
            tvAccessibilityStatus.text = if (accessibilityEnabled) {
                "✓ 无障碍服务已启用"
            } else {
                "✗ 请前往设置开启无障碍服务"
            }

            tvOverlayStatus.text = if (Settings.canDrawOverlays(this)) {
                "✓ 悬浮窗权限已授权"
            } else {
                "✗ 悬浮窗权限未授权"
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPermissions FAILED", e)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val serviceName = "$packageName/${AccessibilityControlService::class.java.name}"
            enabledServices.contains(serviceName)
        } catch (e: Exception) {
            Log.e(TAG, "isAccessibilityServiceEnabled FAILED", e)
            false
        }
    }

    private fun startTimerService() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val periodMinutes = prefs.getInt("period_minutes", 60)
            val allowMinutes = prefs.getInt("allow_minutes", 15)

            if (allowMinutes >= periodMinutes) {
                Toast.makeText(this, "可用时间必须小于周期时间", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_PERIOD, periodMinutes.toLong() * 60 * 1000)
                putExtra(TimerService.EXTRA_ALLOW, allowMinutes.toLong() * 60 * 1000)
            }
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "管控已启动", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "TimerService started")
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "startTimerService FAILED", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTimerService() {
        try {
            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
            startService(intent)
            Toast.makeText(this, "管控已停止", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "TimerService stopped")
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "stopTimerService FAILED", e)
        }
    }

    private fun updateUI() {
        try {
            val serviceRunning = TimerService.isRunning
            btnStartService.text = if (serviceRunning) "停止管控" else "开始管控"
            btnStartService.setTextColor(ContextCompat.getColor(this,
                if (serviceRunning) 0xff00aa00.toInt() else 0xffcc0000.toInt()))

            tvServiceStatus.text = if (serviceRunning) "运行中" else "已停止"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this,
                if (serviceRunning) 0xff00aa00.toInt() else 0xffcc0000.toInt()))

            updateScheduleDisplay()

            if (serviceRunning) {
                updateTimerDisplay()
            } else {
                tvTimer.text = "--:--"
                tvTimer.setTextColor(0xff888888.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateUI FAILED", e)
        }
    }

    private fun updateScheduleDisplay() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val periodMinutes = prefs.getInt("period_minutes", 60)
            val allowMinutes = prefs.getInt("allow_minutes", 15)
            tvSchedule.text = "每 ${periodMinutes} 分钟可用 ${allowMinutes} 分钟"
        } catch (e: Exception) {
            Log.e(TAG, "updateScheduleDisplay FAILED", e)
        }
    }

    private fun updateTimerDisplay() {
        try {
            val now = Calendar.getInstance()
            val currentMinute = now.get(Calendar.MINUTE)
            val currentSecond = now.get(Calendar.SECOND)
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val periodMinutes = prefs.getInt("period_minutes", 60)
            val allowMinutes = prefs.getInt("allow_minutes", 15)

            val minutesSincePeriodStart = currentMinute % periodMinutes
            val secondsInPeriod = minutesSincePeriodStart * 60 + currentSecond
            val allowedSeconds = allowMinutes * 60

            if (secondsInPeriod < allowedSeconds) {
                val remaining = allowedSeconds - secondsInPeriod
                val mins = (remaining / 60).toInt()
                val secs = (remaining % 60).toInt()
                tvTimer.text = String.format("%02d:%02d 可用", mins, secs)
                tvTimer.setTextColor(0xff00aa00.toInt())
            } else {
                val remaining = periodMinutes * 60 - secondsInPeriod
                val mins = (remaining / 60).toInt()
                val secs = (remaining % 60).toInt()
                tvTimer.text = String.format("%02d:%02d 后可用", mins, secs)
                tvTimer.setTextColor(0xffcc0000.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateTimerDisplay FAILED", e)
        }
    }

    private fun showSettingsDialog() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val period = prefs.getInt("period_minutes", 60)
            val allow = prefs.getInt("allow_minutes", 15)

            val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
            val dialog = AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(dialogView)
                .create()

            val periodInput = dialogView.findViewById<EditText>(R.id.et_period_minutes)
            val allowInput = dialogView.findViewById<EditText>(R.id.et_allow_minutes)
            val saveBtn = dialogView.findViewById<Button>(R.id.btn_save_settings)

            periodInput?.setText(period.toString())
            allowInput?.setText(allow.toString())

            saveBtn?.setOnClickListener {
                try {
                    val periodVal = periodInput?.text?.toString()?.toIntOrNull() ?: 60
                    val allowVal = allowInput?.text?.toString()?.toIntOrNull() ?: 15

                    if (allowVal > 0 && periodVal > allowVal && periodVal <= 1440) {
                        prefs.edit()
                            .putInt("period_minutes", periodVal)
                            .putInt("allow_minutes", allowVal)
                            .apply()
                        updateScheduleDisplay()
                        dialog.dismiss()
                        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        if (allowVal <= 0) {
                            allowInput?.error = "必须大于0"
                        }
                        if (periodVal <= allowVal) {
                            periodInput?.error = "必须大于可用时间"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "save settings FAILED", e)
                }
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "showSettingsDialog FAILED", e)
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            checkPermissions()
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "onResume FAILED", e)
        }
    }
}
