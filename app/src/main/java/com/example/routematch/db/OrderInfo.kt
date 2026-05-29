package com.example.routematch.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Platform identifier: "美团" or "饿了么"
    val platform: String,

    // SECURITY: Address fields are stored AES-GCM encrypted via CryptoUtil
    // These are Base64-encoded ciphertext strings (IV + encrypted data)
    val pickupAddressEncrypted: String,

    // SECURITY: Same encryption applied to delivery address
    val deliveryAddressEncrypted: String,

    // GPS coordinates (stored in plaintext for algorithmic matching)
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null,

    // Order metadata
    val amount: Double = 0.0,       // 订单金额 (元)
    val distance: Double = 0.0,     // 配送距离 (米)

    // Timestamps
    val receivedTime: Long = System.currentTimeMillis(),

    // Whether the order has been announced via TTS
    val isAnnounced: Boolean = false,

    // Whether the order matched as "on the way"
    val isMatched: Boolean = false,

    // Match score (0.0 - 1.0), higher = more on the way
    val matchScore: Double = 0.0
)
