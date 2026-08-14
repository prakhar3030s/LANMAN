package com.example.lanman.domain.model

data class Profile(
    val id: String,
    val displayName: String,
    val publicKeyBundle: ByteArray?,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        if (id != other.id) return false
        if (displayName != other.displayName) return false
        if (publicKeyBundle != null) {
            if (other.publicKeyBundle == null) return false
            if (!publicKeyBundle.contentEquals(other.publicKeyBundle)) return false
        } else if (other.publicKeyBundle != null) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (publicKeyBundle?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
