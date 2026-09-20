package com.hidorm.smsrelay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["taskId"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,
    val recipientPhone: String,
    val content: String,
    val msgType: String = "SMS", // SMS or LMS
    val status: String,          // QUEUED, DISPATCHED, SENT, DELIVERED, FAILED
    val retryCount: Int = 0,
    val resultCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null
)
