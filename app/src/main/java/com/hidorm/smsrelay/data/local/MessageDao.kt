package com.hidorm.smsrelay.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE taskId = :taskId LIMIT 1")
    suspend fun getByTaskId(taskId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPendingMessage(): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'QUEUED'")
    fun getPendingCountLiveData(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'QUEUED'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE status IN ('SENT', 'DELIVERED') AND createdAt >= :sinceTimestamp")
    fun getTodaySentCountLiveData(sinceTimestamp: Long): LiveData<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE status IN ('SENT', 'DELIVERED') AND createdAt >= :sinceTimestamp")
    suspend fun getTodaySentCount(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'FAILED' AND createdAt >= :sinceTimestamp")
    fun getTodayFailedCountLiveData(sinceTimestamp: Long): LiveData<Int>

    @Query("UPDATE messages SET status = :status, resultCode = :resultCode, errorMessage = :errorMessage, sentAt = :sentAt WHERE taskId = :taskId")
    suspend fun updateStatus(
        taskId: String,
        status: String,
        resultCode: String?,
        errorMessage: String?,
        sentAt: Long?
    )

    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentMessagesLiveData(limit: Int = 100): LiveData<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE createdAt < :olderThanTimestamp")
    suspend fun deleteOldMessages(olderThanTimestamp: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
