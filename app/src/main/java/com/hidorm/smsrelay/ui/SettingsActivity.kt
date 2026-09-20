package com.hidorm.smsrelay.ui

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
    }

    private fun saveSettings() {
        val serverUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
        val wsUrl = binding.etWsUrl.text?.toString()?.trim() ?: ""
        val deviceId = binding.etDeviceId.text?.toString()?.trim() ?: ""
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        val sendDelay = binding.etSendDelay.text?.toString()?.toLongOrNull() ?: 2000L
        val dailyLimit = binding.etDailyLimit.text?.toString()?.toIntOrNull() ?: 450

        if (serverUrl.isBlank() || deviceId.isBlank()) {
            Toast.makeText(this, "필수 항목(서버 URL, 기기 ID)을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        repository.serverUrl = serverUrl
        repository.wsUrl = wsUrl
        repository.deviceId = deviceId
        repository.apiKey = apiKey
        repository.sendDelayMs = sendDelay
        repository.dailyLimit = dailyLimit

        Toast.makeText(this, "설정이 저장되었습니다. 서비스 재시작 시 적용됩니다.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
