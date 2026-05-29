package com.example.routematch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.routematch.db.OrderDatabase
import com.example.routematch.db.OrderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// SECURITY: System overlay window - only shows order count
// Detailed addresses require PIN verification
class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var tvBadge: TextView
    private lateinit var tvText: TextView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { OrderDatabase.getInstance(this) }

    // Window layout params
    private var params: WindowManager.LayoutParams? = null

    // Touch tracking for drag
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Broadcast receiver for order updates
    private val orderUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshCount()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // SECURITY: Environment integrity check
        if (!SecurityUtils.isEnvironmentSafe(this)) {
            Log.w(TAG, "SECURITY: Unsafe environment detected (Xposed/Frida/Emulator)")
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // Show persistent notification (not foreground - more compatible)
            showPersistentNotification()

            createFloatingView()
            registerReceiver(
                orderUpdateReceiver,
                IntentFilter("com.example.routematch.ORDER_UPDATE")
            )
            refreshCount()
            Log.d(TAG, "FloatingService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FloatingService", e)
        }
    }

    private fun showPersistentNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "route_match_channel"
                val manager = getSystemService(android.app.NotificationManager::class.java)
                if (manager != null) {
                    val channel = android.app.NotificationChannel(
                        channelId,
                        "顺路单助手",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "后台运行状态"
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)

                    val notification = android.app.Notification.Builder(this, channelId)
                        .setContentTitle("顺路单助手")
                        .setContentText("后台运行中")
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setOngoing(true)
                        .build()

                    // Use NOTIFICATION service, not startForeground
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    nm?.notify(2, notification)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification not supported", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_UPDATE") {
            refreshCount()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(orderUpdateReceiver)
        removeFloatingView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null)
        tvBadge = floatingView.findViewById(R.id.tvBadge)
        tvText = floatingView.findViewById(R.id.tvFloatingText)

        // Window type: TYPE_APPLICATION_OVERLAY for API 26+, TYPE_PHONE for older
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Touch listener for dragging and click
        floatingView.setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }

        // Click listener to show order details with PIN
        floatingView.setOnClickListener {
            showPinVerification()
        }

        windowManager.addView(floatingView, params)
    }

    private fun removeFloatingView() {
        try {
            if (::floatingView.isInitialized) {
                windowManager.removeView(floatingView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating view", e)
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialX = params?.x ?: 0
                initialY = params?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                    isDragging = true
                    params?.x = initialX + dx
                    params?.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Update layout error", e)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    floatingView.performClick()
                }
                return true
            }
        }
        return false
    }

    /**
     * Refresh the order count displayed on the floating window.
     */
    private fun refreshCount() {
        scope.launch {
            try {
                val countValue = db.orderDao().getMatchedCountSync()
                updateDisplay(countValue)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing count", e)
            }
        }
    }

    private fun updateDisplay(count: Int) {
        runOnUiThread {
            tvBadge.text = count.toString()
            tvText.text = if (count > 0) {
                getString(R.string.floating_order_count, count)
            } else {
                getString(R.string.floating_no_orders)
            }
        }
    }

    private fun runOnUiThread(action: Runnable) {
        android.os.Handler(mainLooper).post(action)
    }

    /**
     * Show PIN verification dialog.
     * SECURITY: Requires PIN before showing order details
     */
    private fun showPinVerification() {
        val pinCode = SecurityPrefs.getPin(this)
        if (pinCode == null) {
            // No PIN set, open Settings
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        // Use themed context for Material Components in Service
        val themedContext = ContextThemeWrapper(this, R.style.Theme_RouteMatch)

        // Show PIN dialog
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.pin_dialog, null)
        val etPin = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPinInput)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPinError)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPinCancel)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPinConfirm)

        val builder = android.app.AlertDialog.Builder(themedContext)
        builder.setView(dialogView)
        val dialog = builder.create()

        dialog.window?.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val input = etPin.text?.toString() ?: ""
            if (input == pinCode) {
                dialog.dismiss()
                showOrderDetails()
            } else {
                tvError.visibility = View.VISIBLE
                etPin.text?.clear()
            }
        }

        dialog.show()
    }

    /**
     * Show order details after PIN verification.
     * SECURITY: Decrypts addresses for display
     */
    private fun showOrderDetails() {
        scope.launch {
            try {
                val allOrders = db.orderDao().getAllOrdersSync()

                runOnUiThread {
                    showOrdersDialog(allOrders)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading orders", e)
            }
        }
    }

    private fun showOrdersDialog(orders: List<OrderInfo>) {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_RouteMatch)

        if (orders.isEmpty()) {
            val dialog = android.app.AlertDialog.Builder(themedContext)
                .setTitle("顺路单")
                .setMessage("暂无订单记录")
                .setPositiveButton("确定", null)
                .create()
            dialog.window?.setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
            )
            dialog.show()
            return
        }

        // Build a simple text display of recent orders
        val sb = StringBuilder()
        orders.take(10).forEachIndexed { index, order ->
            if (index > 0) sb.append("\n\n")
            // SECURITY: Decrypt addresses for display after PIN verification
            val pickup = try {
                CryptoUtil.decrypt(this, order.pickupAddressEncrypted)
            } catch (e: Exception) { "***" }
            val delivery = try {
                CryptoUtil.decrypt(this, order.deliveryAddressEncrypted)
            } catch (e: Exception) { "***" }

            sb.append("【${order.platform}】")
            sb.append("\n金额: ¥${"%.1f".format(order.amount)}")
            sb.append("\n距离: ${order.distance.toInt()}米")
            sb.append("\n取货: $pickup")
            sb.append("\n送达: $delivery")
            if (order.isMatched) {
                sb.append("\n顺路度: ${"%.0f".format(order.matchScore * 100)}%")
            }
        }

        val dialog = android.app.AlertDialog.Builder(themedContext)
            .setTitle("顺路单 (${orders.size})")
            .setMessage(sb.toString())
            .setPositiveButton("确定", null)
            .create()
        dialog.window?.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )
        dialog.show()
    }

    companion object {
        private const val TAG = "FloatingService"

        /**
         * Check if overlay permission is granted.
         */
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }
}

// Helper for secure preferences
internal object SecurityPrefs {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_PIN = "pin_code"
    private const val KEY_MAX_DETOUR = "max_detour"
    private const val KEY_MAX_ANGLE = "max_angle"
    private const val KEY_JITTER_ENABLED = "jitter_enabled"
    private const val KEY_TTS_ENABLED = "tts_enabled"
    private const val KEY_MANUAL_LAT = "manual_lat"
    private const val KEY_MANUAL_LNG = "manual_lng"
    private const val KEY_MANUAL_ENABLED = "manual_location"

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getPin(context: Context): String? {
        val pin = getPrefs(context).getString(KEY_PIN, null)
        return if (pin.isNullOrBlank()) null else pin
    }

    fun setPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_PIN, pin).apply()
    }

    fun removePin(context: Context) {
        getPrefs(context).edit().remove(KEY_PIN).apply()
    }

    fun isPinSet(context: Context): Boolean = getPin(context) != null

    fun getMaxDetour(context: Context): Double {
        return getPrefs(context).getFloat(KEY_MAX_DETOUR, 1000f).toDouble()
    }

    fun setMaxDetour(context: Context, meters: Double) {
        getPrefs(context).edit().putFloat(KEY_MAX_DETOUR, meters.toFloat()).apply()
    }

    fun getMaxAngle(context: Context): Double {
        return getPrefs(context).getFloat(KEY_MAX_ANGLE, 45f).toDouble()
    }

    fun setMaxAngle(context: Context, degrees: Double) {
        getPrefs(context).edit().putFloat(KEY_MAX_ANGLE, degrees.toFloat()).apply()
    }

    fun isJitterEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_JITTER_ENABLED, true)
    }

    fun setJitterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_JITTER_ENABLED, enabled).apply()
    }

    fun isTtsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TTS_ENABLED, true)
    }

    fun setTtsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
    }

    fun getManualLat(context: Context): Double? {
        return if (getPrefs(context).contains(KEY_MANUAL_LAT)) {
            getPrefs(context).getFloat(KEY_MANUAL_LAT, 0f).toDouble()
        } else null
    }

    fun getManualLng(context: Context): Double? {
        return if (getPrefs(context).contains(KEY_MANUAL_LNG)) {
            getPrefs(context).getFloat(KEY_MANUAL_LNG, 0f).toDouble()
        } else null
    }

    fun setManualLocation(context: Context, lat: Double, lng: Double) {
        getPrefs(context).edit()
            .putFloat(KEY_MANUAL_LAT, lat.toFloat())
            .putFloat(KEY_MANUAL_LNG, lng.toFloat())
            .apply()
    }

    fun isManualLocationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MANUAL_ENABLED, false)
    }

    fun setManualLocationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MANUAL_ENABLED, enabled).apply()
    }

    fun clearManualLocation(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_MANUAL_LAT)
            .remove(KEY_MANUAL_LNG)
            .putBoolean(KEY_MANUAL_ENABLED, false)
            .apply()
    }
}
