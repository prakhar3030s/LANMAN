package com.example.lanman.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lanman.data.local.entity.SessionEntity

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE addressName = :addressName AND deviceId = :deviceId")
    fun getSession(addressName: String, deviceId: Int): SessionEntity?

    @Query("SELECT * FROM sessions WHERE addressName = :addressName")
    fun getSubDeviceSessions(addressName: String): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE addressName = :addressName AND deviceId = :deviceId")
    fun deleteSession(addressName: String, deviceId: Int)

    @Query("DELETE FROM sessions WHERE addressName = :addressName")
    fun deleteAllSessions(addressName: String)
}
