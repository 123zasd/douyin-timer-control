package com.example.douyincontrol

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.subtitle = "抖音时间管控"

        setupViews()
        checkPermissions()
        updateUI()
    }

    private fun setupViews() {
        binding.btnStartService.setOnClickListener {
            if (TimerService.isRunning) {
                stopTimerService()
            } else {
                startTimerService()
            }
        }

        binding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnOpenOverlaySettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }

        binding.btnResetSchedule.setOnClickListener {
            showResetConfirmationDialog()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun checkPermissions() {
        // Check accessibility service status
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        binding.tvAccessibilityStatus.text = if (accessibilityEnabled) {
            "✓ 无障碍服务已启用"
        } else {
            "✗ 请前往设置开启无障碍服务"
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            binding.tvOverlayStatus.text = "✗ 悬浮窗权限未授权"
        } else {
            binding.tvOverlayStatus.text = "✓ 悬浮窗权限已授权"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val serviceName = "$packageName/${AccessibilityControlService::class.java.name}"
        return enabledServices.contains(serviceName)
    }

    private fun startTimerService() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val periodMinutes = prefs.getInt("period_minutes", 60).toLong() * 60 * 1000L
        val allowMinutes = prefs.getInt("allow_minutes", 15).toLong() * 60 * 1000L

        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_PERIOD, periodMinutes)
            putExtra(TimerService.EXTRA_ALLOW, allowMinutes)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "管控已启动", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopTimerService() {
        val intent = Intent(this, TimerService::class.java)
        intent.action = TimerService.ACTION_STOP
        startService(intent)
        Toast.makeText(this, "管控已停止", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun updateUI() {
        val serviceRunning = TimerService.isRunning
        binding.btnStartService.apply {
            text = if (serviceRunning) "停止管控" else "开始管控"
            setTextColor(ContextCompat.getColor(this@MainActivity,
                if (serviceRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            ))
        }

        // Update status
        binding.tvServiceStatus.apply {
            text = if (serviceRunning) "运行中" else "已停止"
            setTextColor(ContextCompat.getColor(this@MainActivity,
                if (serviceRunning) android.R.color.holo_green_dark else android.R.color.darker_gray
            ))
        }

        // Update schedule display
        updateScheduleDisplay()

        // Update timer display if running
        if (serviceRunning && System.currentTimeMillis() - lastStatusUpdate > 500) {
            updateTimerDisplay()
            lastStatusUpdate = System.currentTimeMillis()
        }
    }

    private fun updateScheduleDisplay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val periodMinutes = prefs.getInt("period_minutes", 60)
        val allowMinutes = prefs.getInt("allow_minutes", 15)
        binding.tvSchedule.text = "每 ${periodMinutes} 分钟可用 ${allowMinutes} 分钟"
    }

    private fun updateTimerDisplay() {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val periodMinutes = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("period_minutes", 60)
        val allowMinutes = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("allow_minutes", 15)

        // Calculate time within current period
        val minutesSincePeriodStart = currentMinute % periodMinutes
        val secondsInPeriod = minutesSincePeriodStart * 60 + now.get(Calendar.SECOND)
        val allowedSeconds = allowMinutes * 60

        if (secondsInPeriod < allowedSeconds) {
            // In allowed window
            val remaining = allowedSeconds - secondsInPeriod
            val mins = remaining / 60
            val secs = remaining % 60
            binding.tvTimer.apply {
                text = getString(R.string.format_allowed, mins, secs)
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
            }
        } else {
            // In blocked window
            val remaining = (periodMinutes - minutesSincePeriodStart) * 60 - now.get(Calendar.SECOND)
            val mins = remaining / 60
            val secs = remaining % 60
            binding.tvTimer.apply {
                text = getString(R.string.format_blocked, mins, secs)
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
            }
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("重置定时")
            .setMessage("确定要重置定时吗？当前周期计时将重新开始。")
            .setPositiveButton("确定") { _, _ ->
                val intent = Intent(this, TimerService::class.java)
                intent.action = TimerService.ACTION_RESET
                startService(intent)
                Snackbar.make(binding.root, "定时已重置", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSettingsDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(R.layout.dialog_settings)
            .create()

        dialog.setOnShowListener {
            val periodInput = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(
                R.id.et_period_minutes)
            val allowInput = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(
                R.id.et_allow_minutes)

            periodInput?.setText(prefs.getInt("period_minutes", 60).toString())
            allowInput?.setText(prefs.getInt("allow_minutes", 15).toString())

            dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_settings)
                ?.setOnClickListener {
                    val period = periodInput?.text?.toIntOrNull() ?: 60
                    val allow = allowInput?.text?.toIntOrNull() ?: 15
                    if (period >= allow && allow > 0 && period <= 1440) {
                        prefs.edit()
                            .putInt("period_minutes", period)
                            .putInt("allow_minutes", allow)
                            .apply()
                        updateScheduleDisplay()
                        dialog.dismiss()
                        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        periodInput?.error = "周期必须大于等于可用时间"
                        allowInput?.error = "可用时间必须大于0"
                    }
                }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateUI()
    }
}
