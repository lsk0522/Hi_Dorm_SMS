package com.hidorm.smsrelay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hidorm.smsrelay.HiDormRelayApp
import com.hidorm.smsrelay.data.remote.WsConnectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HiDormRelayApp
    private val repository = app.repository
    private val dao = app.database.messageDao()
    private val smsSender = app.smsSender

    private val todayStart = repository.getTodayStartTimestamp()

    val pendingCount: LiveData<Int> = dao.getPendingCountLiveData()
    val todaySentCount: LiveData<Int> = dao.getTodaySentCountLiveData(todayStart)
    val todayFailedCount: LiveData<Int> = dao.getTodayFailedCountLiveData(todayStart)

    val wsConnectionState: StateFlow<WsConnectionState> = repository.wsManager.connectionState

    private val _logs = MutableLiveData<String>()
    val logs: LiveData<String> = _logs

    private val logHistory = StringBuilder()

    init {
        repository.wsManager.onLogListener = { logMsg ->
            appendLog(logMsg)
        }
        appendLog("[시스템] Hi_Dorm SMS 브릿지 준비 완료")
    }

    fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val entry = "[$time] $message\n"
        logHistory.insert(0, entry)
        if (logHistory.length > 3000) {
            logHistory.setLength(3000)
        }
        _logs.postValue(logHistory.toString())
    }

    fun sendTestSms(destination: String, text: String, onComplete: (Boolean, String) -> Unit) {
        if (destination.isBlank() || text.isBlank()) {
            onComplete(false, "수신 번호와 내용을 모두 입력해주세요.")
            return
        }

        viewModelScope.launch {
            appendLog("[테스트] 발송 시도: $destination")
            val taskId = "test_${UUID.randomUUID().toString().take(8)}"
            val result = smsSender.sendSms(destination, text, taskId)
            if (result.isSuccess) {
                appendLog("[테스트] 발송 성공! (${result.statusString})")
                onComplete(true, "문자 발송 성공")
            } else {
                appendLog("[테스트] 발송 실패: ${result.errorMessage}")
                onComplete(false, result.errorMessage ?: "발송 실패")
            }
        }
    }

    fun getDailyLimit(): Int = repository.dailyLimit
    fun isServiceEnabled(): Boolean = repository.isServiceEnabled
}
