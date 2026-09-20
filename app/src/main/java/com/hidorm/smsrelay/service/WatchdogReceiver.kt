package com.hidorm.smsrelay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hidorm.smsrelay.HiDormRelayApp

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as HiDormRelayApp
        if (app.repository.isServiceEnabled && !SmsRelayForegroundService.isRunning) {
            Log.w("WatchdogReceiver", "서비스가 예기치 않게 종료됨 - 워치독에 의한 재시작")
            val serviceIntent = Intent(context, SmsRelayForegroundService::class.java).apply {
                action = SmsRelayForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
