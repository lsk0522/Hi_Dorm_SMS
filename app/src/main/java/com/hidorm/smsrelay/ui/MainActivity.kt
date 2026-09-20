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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.hidorm.smsrelay.HiDormRelayApp
import com.hidorm.smsrelay.R
import com.hidorm.smsrelay.data.remote.WsConnectionState
import com.hidorm.smsrelay.databinding.ActivityMainBinding
import com.hidorm.smsrelay.service.SmsRelayForegroundService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { application as HiDormRelayApp }
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val requiredPermissions = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
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
            showPermissionRestrictedGuideDialog()
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
        refreshPersistenceStatusUI()
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

        val openPermissions = {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        binding.btnPermissions.setOnClickListener { openPermissions() }
        binding.cardPersistenceSetup.setOnClickListener { openPermissions() }
        binding.btnOpenFullPermissions.setOnClickListener { openPermissions() }

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

        // 백그라운드 상시 가동 사전 설정 버튼 리스너
        binding.btnSetBatteryOpt.setOnClickListener {
            checkBatteryOptimization(forcePrompt = true)
        }

        binding.btnSetOverlay.setOnClickListener {
            openOverlaySettings()
        }

        binding.btnSetAppSettings.setOnClickListener {
            openAppSettings()
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
                        val isP2P = app.repository.serverUrl.contains("example.com")
                        binding.tvNetworkStatus.text = if (isP2P) {
                            "모드: P2P 무인 직결 포워딩 (정상 대기 중)"
                        } else {
                            "서버 연결: 연결 끊김 (Offline / 백업 폴링 대기)"
                        }
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
            promptPersistenceSetupIfNeeded()
        }
    }

    private fun showPermissionRestrictedGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ SMS 권한 허용 안내")
            .setMessage(
                "기숙사 문자 중계 데몬 가동을 위해 SMS 권한이 필수적입니다.\n\n" +
                "안드로이드 시스템에 의해 권한이 차단된 경우:\n" +
                "1. 아래 [설정 열기] 버튼을 눌러 앱 정보 화면으로 이동합니다.\n" +
                "2. [권한] ➔ [SMS]를 '허용'으로 변경합니다.\n\n" +
                "💡 만약 [SMS]가 회색으로 잠겨있다면:\n" +
                "앱 정보 우측 맨 위 [점 3개(⋮)] ➔ [제한된 설정 허용]을 먼저 누르고 지문/PIN을 인증하세요."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun promptPersistenceSetupIfNeeded() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true

        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (!isBatteryIgnored || !isOverlayGranted) {
            AlertDialog.Builder(this)
                .setTitle("백그라운드 상시 가동 설정")
                .setMessage("공기계 단말기가 화면이 꺼져 있어도 실시간 문자를 중계하려면 [배터리 사용량 제한 없음] 및 [다른 앱 위에 표시] 설정이 필수적입니다.\n\n대시보드의 '백그라운드 상시 가동 설정' 카드에서 설정을 진행해 주세요.")
                .setPositiveButton("지금 설정") { _, _ ->
                    if (!isBatteryIgnored) {
                        checkBatteryOptimization(forcePrompt = true)
                    } else if (!isOverlayGranted) {
                        openOverlaySettings()
                    }
                }
                .setNegativeButton("닫기", null)
                .show()
        }
    }

    private fun refreshPersistenceStatusUI() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true

        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (isBatteryIgnored) {
            binding.tvBatteryOptStatus.text = "✅ 제한 없음 (Doze 모드 해제됨)"
            binding.tvBatteryOptStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnSetBatteryOpt.text = "완료"
            binding.btnSetBatteryOpt.isEnabled = false
        } else {
            binding.tvBatteryOptStatus.text = "⚠️ 최적화됨 (화면 꺼지면 슬립 위험)"
            binding.tvBatteryOptStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_orange))
            binding.btnSetBatteryOpt.text = "해제하기"
            binding.btnSetBatteryOpt.isEnabled = true
        }

        if (isOverlayGranted) {
            binding.tvOverlayStatus.text = "✅ 다른 앱 위에 표시 허용됨"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnSetOverlay.text = "완료"
            binding.btnSetOverlay.isEnabled = false
        } else {
            binding.tvOverlayStatus.text = "⚠️ 미허용 (백그라운드 생존 보장 권장)"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_orange))
            binding.btnSetOverlay.text = "허용하기"
            binding.btnSetOverlay.isEnabled = true
        }

        if (isBatteryIgnored && isOverlayGranted) {
            binding.tvPersistenceSummary.text = "상시 가동 준비 완료"
            binding.tvPersistenceSummary.setBackgroundResource(R.drawable.apple_pill_green)
            binding.tvPersistenceSummary.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
        } else {
            binding.tvPersistenceSummary.text = "사전 설정 필요"
            binding.tvPersistenceSummary.setBackgroundResource(R.drawable.apple_pill_red)
            binding.tvPersistenceSummary.setTextColor(ContextCompat.getColor(this, R.color.apple_red))
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization(forcePrompt: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName) || forcePrompt) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
