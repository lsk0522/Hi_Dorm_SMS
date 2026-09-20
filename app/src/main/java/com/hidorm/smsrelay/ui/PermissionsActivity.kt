package com.hidorm.smsrelay.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hidorm.smsrelay.R
import com.hidorm.smsrelay.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasSend = permissions[Manifest.permission.SEND_SMS] == true
        val hasReceive = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (!hasSend || !hasReceive) {
            showRestrictedSettingsDialog()
        }
        refreshStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        initButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun initButtons() {
        binding.btnGrantSms.setOnClickListener {
            smsPermissionLauncher.launch(
                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }

        binding.btnGrantNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Android 12 이하는 알림 권한이 기본 허용됩니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBatteryOpt.setOnClickListener {
            requestBatteryOptimization()
        }

        binding.btnOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnExactAlarm.setOnClickListener {
            requestExactAlarmPermission()
        }

        binding.btnUnlockRestrictedSettings.setOnClickListener {
            openAppDetailsSettings()
        }

        binding.btnOpenAppDetails.setOnClickListener {
            openAppDetailsSettings()
        }

        binding.btnOpenDeveloperSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "개발자 옵션을 열 수 없습니다. [설정 > 휴대전화 정보 > 빌드번호 7번 터치]로 활성화하세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshStatus() {
        // 1. SMS 권한
        val hasSendSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val smsGranted = hasSendSms && hasReceiveSms

        if (smsGranted) {
            binding.tvSendSmsStatus.text = "✅ 문자 송수신 권한 허용됨"
            binding.tvSendSmsStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnGrantSms.text = "완료"
            binding.btnGrantSms.isEnabled = false
        } else {
            binding.tvSendSmsStatus.text = "⚠️ 미허용 (문자 중계 불가)"
            binding.tvSendSmsStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_red))
            binding.btnGrantSms.text = "허용하기"
            binding.btnGrantSms.isEnabled = true
        }

        // 2. 알림 권한
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (notificationGranted) {
            binding.tvNotificationStatus.text = "✅ 포그라운드 상주 알림 허용됨"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnGrantNotification.text = "완료"
            binding.btnGrantNotification.isEnabled = false
        } else {
            binding.tvNotificationStatus.text = "⚠️ 미허용 (백그라운드 생존율 저하)"
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.apple_orange))
            binding.btnGrantNotification.text = "허용하기"
            binding.btnGrantNotification.isEnabled = true
        }

        // 3. 배터리 최적화
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true

        if (isBatteryIgnored) {
            binding.tvBatteryOptDetail.text = "✅ 제한 없음 (Doze 딥슬립 차단됨)"
            binding.tvBatteryOptDetail.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnBatteryOpt.text = "완료"
            binding.btnBatteryOpt.isEnabled = false
        } else {
            binding.tvBatteryOptDetail.text = "⚠️ 최적화됨 (화면 꺼지면 슬립 위험)"
            binding.tvBatteryOptDetail.setTextColor(ContextCompat.getColor(this, R.color.apple_orange))
            binding.btnBatteryOpt.text = "해제하기"
            binding.btnBatteryOpt.isEnabled = true
        }

        // 4. 다른 앱 위에 표시
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (isOverlayGranted) {
            binding.tvOverlayDetail.text = "✅ 다른 앱 위에 표시 허용됨"
            binding.tvOverlayDetail.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnOverlay.text = "완료"
            binding.btnOverlay.isEnabled = false
        } else {
            binding.tvOverlayDetail.text = "⚠️ 미허용 (백그라운드 기동 보장 권장)"
            binding.tvOverlayDetail.setTextColor(ContextCompat.getColor(this, R.color.apple_orange))
            binding.btnOverlay.text = "허용하기"
            binding.btnOverlay.isEnabled = true
        }

        // 5. 정확한 알람
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (isAlarmGranted) {
            binding.tvAlarmDetail.text = "✅ 워치독 정밀 알람 허용됨"
            binding.tvAlarmDetail.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
            binding.btnExactAlarm.text = "완료"
            binding.btnExactAlarm.isEnabled = false
        } else {
            binding.tvAlarmDetail.text = "⚠️ 시스템 제약 (워치독 주기 편차 발생 가능)"
            binding.tvAlarmDetail.setTextColor(ContextCompat.getColor(this, R.color.apple_orange))
            binding.btnExactAlarm.text = "허용하기"
            binding.btnExactAlarm.isEnabled = true
        }

        // 종합 건전성 평가
        val allPassed = smsGranted && notificationGranted && isBatteryIgnored && isOverlayGranted
        if (allPassed) {
            binding.tvHealthSummary.text = "모든 필수 권한과 절전 해제가 완료되었습니다. 24시간 365일 무중단 가동이 안전하게 보장됩니다."
            binding.layoutOverallBadge.setBackgroundResource(R.drawable.apple_pill_green)
            binding.tvOverallBadgeText.text = "✅ 24/7 상주 준비 완벽"
            binding.tvOverallBadgeText.setTextColor(ContextCompat.getColor(this, R.color.apple_green))
        } else {
            binding.tvHealthSummary.text = "일부 권한 또는 절전 모드 예외가 꺼져 있습니다. 화면이 꺼졌을 때 문자가 지연되거나 누락될 수 있습니다."
            binding.layoutOverallBadge.setBackgroundResource(R.drawable.apple_pill_red)
            binding.tvOverallBadgeText.text = "⚠️ 사전 설정 미완료"
            binding.tvOverallBadgeText.setTextColor(ContextCompat.getColor(this, R.color.apple_red))
        }
        // 제한된 설정 가이드 노출 분기
        binding.layoutRestrictedSettingsHelp.visibility = if (smsGranted) View.GONE else View.VISIBLE
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRestrictedSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ 구글 SMS 권한 차단 해제 방법")
            .setMessage(
                "안드로이드 13/14+ 보안 정책에 의해 외부 설치 앱의 SMS 권한이 [제한된 설정]으로 잠겨 있을 수 있습니다.\n\n" +
                "1. 아래 [설정 풀러 가기] 터치\n" +
                "2. 앱 정보 우측 상단 [점 3개(⋮)] 터치\n" +
                "3. [제한된 설정 허용] 터치 후 지문/PIN 인증\n" +
                "4. [권한] ➔ [SMS] 항목을 '허용'으로 변경\n\n" +
                "위 과정을 거치면 정상적으로 권한이 허용됩니다."
            )
            .setPositiveButton("설정 풀러 가기") { _, _ ->
                openAppDetailsSettings()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
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

    private fun requestOverlayPermission() {
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

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
