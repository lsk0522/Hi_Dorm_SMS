package com.hidorm.smsrelay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.hidorm.smsrelay.data.local.AppDatabase
import com.hidorm.smsrelay.data.repository.RelayRepository
import com.hidorm.smsrelay.modem.SmsSender

class HiDormRelayApp : Application() {

    companion object {
        const val CHANNEL_ID = "hi_dorm_sms_relay_channel"
        lateinit var instance: HiDormRelayApp
            private set
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    val smsSender by lazy { SmsSender(this) }
    val repository by lazy { RelayRepository(this, database.messageDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
