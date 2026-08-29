package com.example.douyincontrol

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.douyincontrol.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var lastStatusUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setSupportActionBar(binding.toolbar)
            supportActionBar?.subtitle = "抖音时间管控"

            setupViews()
            checkPermissions()
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate failed", e)
        }
    }

    private fun setupViews() {
        binding.btnStartService.setOnClickListener {
            try {
                if (TimerService.isRunning) {
                    stopTimerService()
                } else {
                    startTimerService()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "btnStartService click failed", e)
                Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Open accessibility settings failed", e)
            }
        }

        binding.btnOpenOverlaySettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Open overlay settings failed", e)
            }
        }

        binding.btnResetSchedule.setOnClickListener {
            showResetConfirmationDialog()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun checkPermissions() {
        try {
            val accessibilityEnabled = isAccessibilityServiceEnabled()
            binding.tvAccessibilityStatus.text = if (accessibilityEnabled) {
                "✓ 无障碍服务已启用"
            } else {
                "✗ 请前往设置开启无障碍服务"
            }

            if (!Settings.canDrawOverlays(this)) {
                binding.tvOverlayStatus.text = "✗ 悬浮窗权限未授权"
            } else {
                binding.tvOverlayStatus.text = "✓ 悬浮窗权限已授权"
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "checkPermissions failed", e)
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
            Log.e("MainActivity", "isAccessibilityServiceEnabled failed", e)
            false
        }
    }

    private fun startTimerService() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val periodMinutes = prefs.getInt("period_minutes", 60).toLong()
            val allowMinutes = prefs.getInt("allow_minutes", 15).toLong()

            if (allowMinutes >= periodMinutes) {
                Toast.makeText(this, "可用时间必须小于周期时间", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_PERIOD, periodMinutes * 60 * 1000L)
                putExtra(TimerService.EXTRA_ALLOW, allowMinutes * 60 * 1000L)
            }
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "管控已启动", Toast.LENGTH_SHORT).show()
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "startTimerService failed", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTimerService() {
        try {
            val intent = Intent(this, TimerService::class.java)
            intent.action = TimerService.ACTION_STOP
            startService(intent)
            Toast.makeText(this, "管控已停止", Toast.LENGTH_SHORT).show()
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "stopTimerService failed", e)
        }
    }

    private fun updateUI() {
        try {
            val serviceRunning = TimerService.isRunning
            binding.btnStartService.apply {
                text = if (serviceRunning) "停止管控" else "开始管控"
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (serviceRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                ))
            }

            binding.tvServiceStatus.apply {
                text = if (serviceRunning) "运行中" else "已停止"
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (serviceRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                ))
            }

            updateScheduleDisplay()

            if (serviceRunning && System.currentTimeMillis() - lastStatusUpdate > 500) {
                updateTimerDisplay()
                lastStatusUpdate = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "updateUI failed", e)
        }
    }

    private fun updateScheduleDisplay() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val periodMinutes = prefs.getInt("period_minutes", 60)
            val allowMinutes = prefs.getInt("allow_minutes", 15)
            binding.tvSchedule.text = "每 ${periodMinutes} 分钟可用 ${allowMinutes} 分钟"
        } catch (e: Exception) {
            Log.e("MainActivity", "updateScheduleDisplay failed", e)
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
                binding.tvTimer.apply {
                    text = String.format("%02d:%02d 可用", mins, secs)
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                }
            } else {
                val remaining = periodMinutes * 60 - secondsInPeriod
                val mins = (remaining / 60).toInt()
                val secs = (remaining % 60).toInt()
                binding.tvTimer.apply {
                    text = String.format("%02d:%02d 后可用", mins, secs)
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "updateTimerDisplay failed", e)
        }
    }

    private fun showResetConfirmationDialog() {
        try {
            AlertDialog.Builder(this)
                .setTitle("重置定时")
                .setMessage("确定要重置定时吗？当前周期计时将重新开始。")
                .setPositiveButton("确定") { _, _ ->
                    try {
                        val intent = Intent(this, TimerService::class.java)
                        intent.action = TimerService.ACTION_RESET
                        startService(intent)
                        Snackbar.make(binding.root, "定时已重置", Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "reset failed", e)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "showResetConfirmationDialog failed", e)
        }
    }

    private fun showSettingsDialog() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)

            val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
            val dialog = AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(dialogView)
                .create()

            val periodInput = dialogView.findViewById<android.widget.EditText>(R.id.et_period_minutes)
            val allowInput = dialogView.findViewById<android.widget.EditText>(R.id.et_allow_minutes)
            val saveBtn = dialogView.findViewById<android.widget.Button>(R.id.btn_save_settings)

            periodInput?.setText(prefs.getInt("period_minutes", 60).toString())
            allowInput?.setText(prefs.getInt("allow_minutes", 15).toString())

            saveBtn?.setOnClickListener {
                try {
                    val periodText = periodInput?.text?.toString() ?: ""
                    val allowText = allowInput?.text?.toString() ?: ""
                    val period = periodText.toIntOrNull() ?: 60
                    val allow = allowText.toIntOrNull() ?: 15

                    if (allow > 0 && period > allow && period <= 1440) {
                        prefs.edit()
                            .putInt("period_minutes", period)
                            .putInt("allow_minutes", allow)
                            .apply()
                        updateScheduleDisplay()
                        dialog.dismiss()
                        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        if (allow <= 0) {
                            allowInput?.error = "必须大于0"
                        }
                        if (period <= allow) {
                            periodInput?.error = "必须大于可用时间"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "save settings failed", e)
                }
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("MainActivity", "showSettingsDialog failed", e)
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            checkPermissions()
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "onResume failed", e)
        }
    }
}
