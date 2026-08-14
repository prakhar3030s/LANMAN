package com.example.lanman.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val addressName: String,
    val deviceId: Int,
    val sessionRecord: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionEntity

        if (addressName != other.addressName) return false
        if (deviceId != other.deviceId) return false
        if (!sessionRecord.contentEquals(other.sessionRecord)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = addressName.hashCode()
        result = 31 * result + deviceId
        result = 31 * result + sessionRecord.contentHashCode()
        return result
    }
}
