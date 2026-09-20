package com.hidorm.smsrelay.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.hidorm.smsrelay.R
import com.hidorm.smsrelay.data.remote.WsConnectionState
import com.hidorm.smsrelay.databinding.ActivityMainBinding
import com.hidorm.smsrelay.service.SmsRelayForegroundService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val requiredPermissions = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            checkBatteryOptimization()
        } else {
            Toast.makeText(this, "SMS 발송 및 상주를 위해 모든 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryInfo(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        syncServiceSwitch()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (ignored: Exception) {}
    }

    private fun initViews() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val openHistory = {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnHistory.setOnClickListener { openHistory() }
        binding.btnViewHistory.setOnClickListener { openHistory() }

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startRelayService()
            } else {
                stopRelayService()
            }
        }

        binding.btnSendTest.setOnClickListener {
            val phone = binding.etTestPhone.text?.toString()?.trim() ?: ""
            val message = binding.etTestMessage.text?.toString()?.trim() ?: ""
            binding.btnSendTest.isEnabled = false
            viewModel.sendTestSms(phone, message) { success, msg ->
                binding.btnSendTest.isEnabled = true
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRunSimulation.setOnClickListener {
            binding.btnRunSimulation.isEnabled = false
            Toast.makeText(this, "7대 엣지케이스 시뮬레이션을 시작합니다. 로그를 확인하세요.", Toast.LENGTH_SHORT).show()
            viewModel.runSimulationSuite { allPassed ->
                binding.btnRunSimulation.isEnabled = true
                val msg = if (allPassed) "모든 시뮬레이션 케이스 통과! (ALL PASSED)" else "일부 시뮬레이션 경고/실패"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncServiceSwitch() {
        val running = SmsRelayForegroundService.isRunning
        binding.switchService.isChecked = running
        updateServiceStatusUI(running)
    }

    private fun startRelayService() {
        val intent = Intent(this, SmsRelayForegroundService::class.java).apply {
            action = SmsRelayForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        updateServiceStatusUI(true)
        viewModel.appendLog("[제어] 서비스 시작 명령 전달")
    }

    private fun stopRelayService() {
        val intent = Intent(this, SmsRelayForegroundService::class.java).apply {
            action = SmsRelayForegroundService.ACTION_STOP
        }
        startService(intent)
        updateServiceStatusUI(false)
        viewModel.appendLog("[제어] 서비스 중지 명령 전달")
    }

    private fun updateServiceStatusUI(isRunning: Boolean) {
        if (isRunning) {
            binding.layoutStatusBadge.setBackgroundResource(R.drawable.apple_pill_green)
            binding.viewStatusDot.setBackgroundResource(R.drawable.circle_green)
            binding.tvStatusText.text = "DAEMON RUNNING"
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
        } else {
            binding.layoutStatusBadge.setBackgroundResource(R.drawable.apple_pill_red)
            binding.viewStatusDot.setBackgroundResource(R.drawable.circle_red)
            binding.tvStatusText.text = "DAEMON STOPPED"
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.apple_red))
        }
    }

    private fun observeViewModel() {
        val limit = viewModel.getDailyLimit()
        binding.pbDailyQuota.max = limit

        viewModel.todaySentCount.observe(this) { sent ->
            val count = sent ?: 0
            binding.tvSuccessCount.text = count.toString()
            binding.pbDailyQuota.progress = count
            binding.tvDailyQuotaText.text = "$count / ${limit}건"
        }

        viewModel.todayFailedCount.observe(this) { failed ->
            binding.tvFailCount.text = (failed ?: 0).toString()
        }

        viewModel.pendingCount.observe(this) { pending ->
            binding.tvQueueCount.text = (pending ?: 0).toString()
        }

        viewModel.logs.observe(this) { logText ->
            binding.tvLogs.text = logText
        }

        lifecycleScope.launch {
            viewModel.wsConnectionState.collectLatest { state ->
                when (state) {
                    WsConnectionState.CONNECTED -> {
                        binding.tvNetworkStatus.text = "서버 연결: WebSocket 연결됨 (Online)"
                    }
                    WsConnectionState.CONNECTING -> {
                        binding.tvNetworkStatus.text = "서버 연결: 재연결 중..."
                    }
                    WsConnectionState.DISCONNECTED -> {
                        binding.tvNetworkStatus.text = "서버 연결: 연결 끊김 (Offline / 백업 폴링 대기)"
                    }
                }
            }
        }
    }

    private fun updateBatteryInfo(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargeText = if (isCharging) "충전 중 (⚡)" else "배터리 사용 중"
        binding.tvBatteryStatus.text = "배터리: $pct% ($chargeText)"
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            checkBatteryOptimization()
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (ignored: Exception) {}
            }
        }
    }
}
