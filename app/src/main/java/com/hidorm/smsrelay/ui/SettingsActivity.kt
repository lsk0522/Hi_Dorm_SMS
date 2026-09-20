package com.hidorm.smsrelay.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hidorm.smsrelay.HiDormRelayApp
import com.hidorm.smsrelay.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val app by lazy { application as HiDormRelayApp }
    private val repository by lazy { app.repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.cardPermissionsWizard.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        loadSettings()

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        binding.etServerUrl.setText(repository.serverUrl)
        binding.etWsUrl.setText(repository.wsUrl)
        binding.etDeviceId.setText(repository.deviceId)
        binding.etApiKey.setText(repository.apiKey)
        binding.etSendDelay.setText(repository.sendDelayMs.toString())
        binding.etDailyLimit.setText(repository.dailyLimit.toString())

        // 직결 자동 포워딩 설정 로드
        binding.switchForwarding.isChecked = repository.isForwardingEnabled
        binding.etTriggerSender.setText(repository.triggerSenderNumber)
        binding.etTargetRecipient.setText(repository.targetRecipientNumber)
        binding.etForwardKeyword.setText(repository.forwardKeywordFilter)
        binding.switchIncludePrefix.isChecked = repository.includeSenderPrefix
    }

    private fun saveSettings() {
        val serverUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
        val wsUrl = binding.etWsUrl.text?.toString()?.trim() ?: ""
        val deviceId = binding.etDeviceId.text?.toString()?.trim() ?: ""
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        val sendDelay = binding.etSendDelay.text?.toString()?.toLongOrNull() ?: 2000L
        val dailyLimit = binding.etDailyLimit.text?.toString()?.toIntOrNull() ?: 450

        // 포워딩 항목
        val isForwarding = binding.switchForwarding.isChecked
        val triggerSender = binding.etTriggerSender.text?.toString()?.trim() ?: ""
        val targetRecipient = binding.etTargetRecipient.text?.toString()?.trim() ?: ""
        val forwardKeyword = binding.etForwardKeyword.text?.toString()?.trim() ?: ""
        val includePrefix = binding.switchIncludePrefix.isChecked

        repository.serverUrl = serverUrl
        repository.wsUrl = wsUrl
        repository.deviceId = deviceId
        repository.apiKey = apiKey
        repository.sendDelayMs = sendDelay
        repository.dailyLimit = dailyLimit

        repository.isForwardingEnabled = isForwarding
        repository.triggerSenderNumber = triggerSender
        repository.targetRecipientNumber = targetRecipient
        repository.forwardKeywordFilter = forwardKeyword
        repository.includeSenderPrefix = includePrefix

        Toast.makeText(this, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
