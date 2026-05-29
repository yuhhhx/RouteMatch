package com.example.routematch

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.routematch.db.OrderDatabase
import com.example.routematch.db.OrderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// SECURITY: Uses NotificationListenerService (NOT AccessibilityService, NOT MediaProjection)
// No screenshot or screen recording capabilities
class NotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val orderDb by lazy { OrderDatabase.getInstance(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // SECURITY: Apply jitter to processing delay
        val delay = SecurityUtils.applyJitter(500)
        android.os.Handler(mainLooper).postDelayed({
            processNotification(sbn)
        }, delay)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not used
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE, "") ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT, "") ?: ""
        val packageName = sbn.packageName

        // Detect and parse order notifications
        val platform = detectPlatform(title, packageName) ?: return
        val orderData = parseOrderNotification(platform, title, text) ?: return

        Log.d(TAG, "Order detected: [$platform] ${orderData}")

        // Save encrypted order to database
        scope.launch {
            try {
                // SECURITY: Encrypt address fields before storing
                val encryptedPickup = CryptoUtil.encrypt(this@NotificationListener, orderData.pickup)
                val encryptedDelivery = CryptoUtil.encrypt(this@NotificationListener, orderData.delivery)

                val order = OrderInfo(
                    platform = platform,
                    pickupAddressEncrypted = encryptedPickup,
                    deliveryAddressEncrypted = encryptedDelivery,
                    pickupLat = orderData.pickupLat,
                    pickupLng = orderData.pickupLng,
                    deliveryLat = orderData.deliveryLat,
                    deliveryLng = orderData.deliveryLng,
                    amount = orderData.amount,
                    distance = orderData.distance
                )
                orderDb.orderDao().insert(order)
                Log.d(TAG, "Order saved to encrypted database")

                // Trigger TTS announcement
                if (TTSHelper.isEnabled()) {
                    TTSHelper.announceOrder(
                        platform = platform,
                        amount = orderData.amount,
                        distance = orderData.distance
                    )
                }

                // Trigger notification to update floating window
                sendBroadcast(OrderUpdateIntent)
                Log.d(TAG, "Order update broadcast sent")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save order", e)
            }
        }
    }

    /**
     * Detect the order platform from notification content.
     */
    private fun detectPlatform(title: String, packageName: String): String? {
        val normalizedTitle = title.lowercase()
        val normalizedPkg = packageName.lowercase()

        return when {
            normalizedPkg.contains("meituan") || normalizedTitle.contains("美团") ||
                normalizedTitle.contains("外卖") && normalizedPkg.contains("sankuai") -> "美团"
            normalizedPkg.contains("ele") || normalizedTitle.contains("饿了么") ||
                normalizedTitle.contains("蜂鸟") -> "饿了么"
            else -> null
        }
    }

    /**
     * Parse order details from notification title and text.
     * Uses regex patterns to extract address, amount, distance.
     */
    private fun parseOrderNotification(
        platform: String,
        title: String,
        text: String
    ): OrderData? {
        val combined = "$title $text"

        // Extract amount (金额)
        val amount = parseAmount(combined)

        // Extract distance (配送距离)
        val distance = parseDistance(combined)

        // Extract pickup address (商家/取货地址)
        val pickup = parsePickupAddress(platform, combined)

        // Extract delivery address (送达地址)
        val delivery = parseDeliveryAddress(platform, combined)

        if (pickup.isBlank() && delivery.isBlank()) {
            Log.w(TAG, "Could not parse address from notification: $combined")
            return null
        }

        return OrderData(
            pickup = pickup.ifBlank { "未知取货地址" },
            delivery = delivery.ifBlank { "未知送达地址" },
            pickupLat = null,  // Would require geocoding (not available offline)
            pickupLng = null,
            deliveryLat = null,
            deliveryLng = null,
            amount = amount,
            distance = distance
        )
    }

    private fun parseAmount(text: String): Double {
        // Match patterns like: 金额: ¥25.5, 金额：25.5元, ¥25.5, 25.5元
        val patterns = listOf(
            Regex("""金[额額][：:]\s*¥?\s*([\d.]+)"""),
            Regex("""¥\s*([\d.]+)"""),
            Regex("""([\d.]+)\s*元"""),
            Regex("""总计[：:]\s*¥?\s*([\d.]+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }
        return 0.0
    }

    private fun parseDistance(text: String): Double {
        // Match: 距离: 2.5km, 配送距离 3.2 公里, 1.8km
        val patterns = listOf(
            Regex("""距[离離][：:]\s*([\d.]+)\s*k[米m]"""),
            Regex("""配送[：:]\s*([\d.]+)\s*k[米m]"""),
            Regex("""([\d.]+)\s*公里"""),
            Regex("""([\d.]+)\s*k[米m]""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val km = match.groupValues[1].toDoubleOrNull() ?: 0.0
                return km * 1000.0 // Convert to meters
            }
        }
        return 0.0
    }

    private fun parsePickupAddress(platform: String, text: String): String {
        val patterns = listOf(
            Regex("""商[家門][：:]\s*(.+?)(?:[。，,．\n]|$)"""),
            Regex("""[取提][货餐][：:]\s*(.+?)(?:[。，,．\n]|$)"""),
            Regex("""(?:从|在)\s*(.+?)(?:[送发配])""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    private fun parseDeliveryAddress(platform: String, text: String): String {
        val patterns = listOf(
            Regex("""(?:送达|配送|送到)(?:地址)?[：:]\s*(.+?)(?:[。，,．\n]|$)"""),
            Regex("""(?:送至|送到)\s*(.+?)(?:[。，,．\n]|$)"""),
            Regex("""地[址点][：:]\s*(.+?)(?:[。，,．\n]|$)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    data class OrderData(
        val pickup: String,
        val delivery: String,
        val pickupLat: Double?,
        val pickupLng: Double?,
        val deliveryLat: Double?,
        val deliveryLng: Double?,
        val amount: Double,
        val distance: Double
    )

    companion object {
        private const val TAG = "NotificationListener"

        // Intent action for updating the floating window
        val OrderUpdateIntent = android.content.Intent().apply {
            action = "com.example.routematch.ORDER_UPDATE"
        }

        /**
         * Check if the notification listener is enabled in system settings.
         */
        fun isListenerEnabled(context: Context): Boolean {
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return enabledListeners?.contains(context.packageName) == true
        }
    }
}
