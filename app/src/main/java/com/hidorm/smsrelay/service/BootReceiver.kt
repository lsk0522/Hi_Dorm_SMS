package com.hidorm.smsrelay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hidorm.smsrelay.HiDormRelayApp

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as HiDormRelayApp
            if (app.repository.isServiceEnabled) {
                Log.i("BootReceiver", "단말기 재부팅 감지: SMS Relay 서비스 자동 시작")
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
}
