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

    private var btnStart: Button? = null
    private var tvStatus: TextView? = null
    private var tvSchedule: TextView? = null
    private var tvTimer: TextView? = null
    private var tvAccess: TextView? = null
    private var tvOverlay: TextView? = null
    private var btnSettings: Button? = null
    private var btnReset: Button? = null
    private var btnAccessSettings: Button? = null
    private var btnOverlaySettings: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            bindAllViews()
            setupAllListeners()
            checkPermissions()
            updateUI()
            Log.d(TAG, "onCreate OK")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate", e)
            try {
                setContentView(android.R.layout.simple_list_item_1)
                val tv = findViewById<TextView>(android.R.id.text1)
                tv?.text = "初始化失败: ${e.message}"
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback layout also failed", e2)
            }
        }
    }

    private fun bindAllViews() {
        btnStart = findViewById(R.id.btn_start_service)
        tvStatus = findViewById(R.id.tv_service_status)
        tvSchedule = findViewById(R.id.tv_schedule)
        tvTimer = findViewById(R.id.tv_timer)
        tvAccess = findViewById(R.id.tv_accessibility_status)
        tvOverlay = findViewById(R.id.tv_overlay_status)
        btnSettings = findViewById(R.id.btn_settings)
        btnReset = findViewById(R.id.btn_reset_schedule)
        btnAccessSettings = findViewById(R.id.btn_open_settings)
        btnOverlaySettings = findViewById(R.id.btn_open_overlay_settings)
        Log.d(TAG, "Views bound")
    }

    private fun setupAllListeners() {
        btnStart?.setOnClickListener {
            Log.d(TAG, "btnStart clicked, isRunning=${TimerService.isRunning}")
            try {
                if (TimerService.isRunning) {
                    stopTimerService()
                } else {
                    startTimerService()
                }
            } catch (e: Exception) {
                Log.e(TAG, "start/stop service failed", e)
                Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccessSettings?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Log.e(TAG, "accessibility settings failed", e)
            }
        }

        btnOverlaySettings?.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "overlay settings failed", e)
            }
        }

        btnReset?.setOnClickListener {
            try {
                AlertDialog.Builder(this)
                    .setTitle("重置定时")
                    .setMessage("确定要重置定时吗？")
                    .setPositiveButton("确定") { _, _ ->
                        try {
                            val intent = Intent(this, TimerService::class.java)
                            intent.action = TimerService.ACTION_RESET
                            startService(intent)
                            Toast.makeText(this, "定时已重置", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "reset failed", e)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "reset dialog failed", e)
            }
        }

        btnSettings?.setOnClickListener {
            Log.d(TAG, "btnSettings clicked")
            try {
                showSettingsDialog()
            } catch (e: Exception) {
                Log.e(TAG, "settings dialog failed", e)
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }

        Log.d(TAG, "Listeners setup OK")
    }

    private fun checkPermissions() {
        try {
            val serviceEnabled = isAccessServiceEnabled()
            tvAccess?.text = if (serviceEnabled) "✓ 无障碍服务已启用" else "✗ 请前往设置开启无障碍服务"

            val overlayEnabled = Settings.canDrawOverlays(this)
            tvOverlay?.text = if (overlayEnabled) "✓ 悬浮窗权限已授权" else "✗ 悬浮窗权限未授权"
        } catch (e: Exception) {
            Log.e(TAG, "checkPermissions failed", e)
        }
    }

    private fun isAccessServiceEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val me = "$packageName/${AccessibilityControlService::class.java.name}"
            enabled.contains(me)
        } catch (e: Exception) {
            Log.e(TAG, "isAccessServiceEnabled failed", e)
            false
        }
    }

    private fun startTimerService() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val period = prefs.getInt("period_minutes", 60)
            val allow = prefs.getInt("allow_minutes", 15)

            if (allow >= period) {
                Toast.makeText(this, "可用时间必须小于周期", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_PERIOD, period.toLong() * 60 * 1000)
                putExtra(TimerService.EXTRA_ALLOW, allow.toLong() * 60 * 1000)
            }

            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "管控已启动", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Service started OK")
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "startTimerService failed", e)
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
            Log.d(TAG, "Service stopped OK")
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "stopTimerService failed", e)
        }
    }

    private fun updateUI() {
        try {
            val running = TimerService.isRunning
            btnStart?.text = if (running) "停止管控" else "开始管控"
            btnStart?.setTextColor(if (running) 0xFF00AA00.toInt() else 0xFFCC0000.toInt())
            tvStatus?.text = if (running) "运行中" else "已停止"
            tvStatus?.setTextColor(if (running) 0xFF00AA00.toInt() else 0xFFCC0000.toInt())

            updateSchedule()
            if (running) updateTimer() else tvTimer?.text = "--:--"
        } catch (e: Exception) {
            Log.e(TAG, "updateUI failed", e)
        }
    }

    private fun updateSchedule() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val period = prefs.getInt("period_minutes", 60)
            val allow = prefs.getInt("allow_minutes", 15)
            tvSchedule?.text = "每 ${period} 分钟可用 ${allow} 分钟"
        } catch (e: Exception) {
            Log.e(TAG, "updateSchedule failed", e)
        }
    }

    private fun updateTimer() {
        try {
            val now = Calendar.getInstance()
            val minute = now.get(Calendar.MINUTE)
            val second = now.get(Calendar.SECOND)
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val period = prefs.getInt("period_minutes", 60)
            val allow = prefs.getInt("allow_minutes", 15)

            val secsInPeriod = minute % period * 60 + second
            val allowedSecs = allow * 60

            if (secsInPeriod < allowedSecs) {
                val remain = allowedSecs - secsInPeriod
                tvTimer?.text = String.format("%02d:%02d 可用", remain / 60, remain % 60)
                tvTimer?.setTextColor(0xFF00AA00.toInt())
            } else {
                val remain = period * 60 - secsInPeriod
                tvTimer?.text = String.format("%02d:%02d 后可用", remain / 60, remain % 60)
                tvTimer?.setTextColor(0xFFCC0000.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateTimer failed", e)
        }
    }

    private fun showSettingsDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val period = prefs.getInt("period_minutes", 60)
        val allow = prefs.getInt("allow_minutes", 15)

        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val periodInput = view.findViewById<EditText>(R.id.et_period_minutes)
        val allowInput = view.findViewById<EditText>(R.id.et_allow_minutes)
        val saveBtn = view.findViewById<Button>(R.id.btn_save_settings)

        periodInput?.setText(period.toString())
        allowInput?.setText(allow.toString())

        val dialog = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(view)
            .create()

        saveBtn?.setOnClickListener {
            try {
                val p = periodInput?.text?.toString()?.toIntOrNull() ?: 60
                val a = allowInput?.text?.toString()?.toIntOrNull() ?: 15
                if (a in 1..p - 1 && p <= 1440) {
                    prefs.edit().putInt("period_minutes", p).putInt("allow_minutes", a).apply()
                    updateSchedule()
                    Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    if (a < 1) allowInput?.error = "必须>=1"
                    if (p <= a) periodInput?.error = "必须>可用时间"
                }
            } catch (e: Exception) {
                Log.e(TAG, "save settings failed", e)
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        try {
            checkPermissions()
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "onResume failed", e)
        }
    }
}
