package com.example.lanman.domain.model

data class Contact(
    val id: String,
    val displayName: String,
    val publicKeyBundle: ByteArray?,
    val lastKnownIp: String?,
    val lastSeenAt: Long?,
    val addedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Contact

        if (id != other.id) return false
        if (displayName != other.displayName) return false
        if (publicKeyBundle != null) {
            if (other.publicKeyBundle == null) return false
            if (!publicKeyBundle.contentEquals(other.publicKeyBundle)) return false
        } else if (other.publicKeyBundle != null) return false
        if (lastKnownIp != other.lastKnownIp) return false
        if (lastSeenAt != other.lastSeenAt) return false
        if (addedAt != other.addedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (publicKeyBundle?.contentHashCode() ?: 0)
        result = 31 * result + (lastKnownIp?.hashCode() ?: 0)
        result = 31 * result + (lastSeenAt?.hashCode() ?: 0)
        result = 31 * result + addedAt.hashCode()
        return result
    }
}
