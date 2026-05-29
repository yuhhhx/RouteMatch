package com.example.routematch

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import java.security.MessageDigest

// SECURITY: Environment integrity verification module
// Detects rooted devices, Xposed/Frida frameworks, and emulator environments
object SecurityUtils {

    private const val TAG = "SecurityUtils"

    // Frida default port
    private const val FRIDA_DEFAULT_PORT = 27042

    // Known Xposed packages
    private val XPOSED_PACKAGES = listOf(
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.samsung.android.mobilesecurity"
    )

    // Known root binaries
    private val ROOT_BINARIES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/magisk",
        "/data/local/tmp/magisk"
    )

    // Emulator build properties
    private val EMULATOR_PROPS = listOf(
        "ro.kernel.qemu",
        "ro.boot.qemu",
        "ro.hardware.ranchu",
        "ro.product.cpu.abi2"
    )

    /**
     * Full environment integrity check.
     * Returns true if the environment is safe (no tampering detected).
     */
    fun isEnvironmentSafe(context: Context): Boolean {
        if (detectXposed()) return false
        if (detectFrida()) return false
        if (detectEmulator()) return false
        if (detectRoot()) return false
        return true
    }

    /**
     * Verify APK signature matches the expected hash.
     * SECURITY: Prevents repackaging and tampering
     */
    fun verifySignature(context: Context): Boolean {
        try {
            val expectedHash = "A4B5C6D7E8F90123456789ABCDEF0123456789ABCDEF0123456789ABCDEF012345"
            // SECURITY: Replace the above hash with your own app's SHA-256 certificate hash before release.
            // Get your hash by running:
            //   keytool -list -v -keystore your.keystore -alias your-alias 2>/dev/null | grep "SHA256" | awk '{print $2}' | tr -d ':'

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val digest = MessageDigest.getInstance("SHA-256")

            val certHashes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                val certs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.signingCertificateHistory
                } else {
                    signingInfo.signingCertificateHistory
                }
                certs.map { cert ->
                    digest.digest(cert.toByteArray()).joinToString("") { "%02X".format(it) }
                }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures.map { signature ->
                    digest.digest(signature.toByteArray()).joinToString("") { "%02X".format(it) }
                }
            }

            return certHashes.any { it == expectedHash }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Detect Xposed framework by checking for known classes.
     */
    private fun detectXposed(): Boolean {
        try {
            // Try to load known Xposed class
            ClassLoader.getSystemClassLoader().loadClass("de.robv.android.xposed.XposedBridge")
            return true
        } catch (e: ClassNotFoundException) {
            // Xposed not found via classloader
        }

        // Check for Xposed packages on device
        return XPOSED_PACKAGES.any { pkg ->
            try {
                val file = File("/data/data/$pkg")
                file.exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Detect Frida by checking for its default port and process.
     */
    private fun detectFrida(): Boolean {
        // Try to connect to Frida's default port
        try {
            Socket("127.0.0.1", FRIDA_DEFAULT_PORT).use {
                return true
            }
        } catch (e: Exception) {
            // Port not open
        }

        // Check for frida-server process
        try {
            val process = Runtime.getRuntime().exec("ps")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.contains("frida") == true) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Cannot read process list
        }

        return false
    }

    /**
     * Detect emulator environment.
     */
    private fun detectEmulator(): Boolean {
        // Check build properties commonly set on emulators
        if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) return true
        if (Build.FINGERPRINT.contains("generic")) return true
        if (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")) return true
        if (Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator")) return true
        if (Build.MANUFACTURER.contains("Genymotion")) return true
        if (Build.PRODUCT.contains("sdk") || Build.PRODUCT.contains("google_sdk")) return true

        // Check for QEMU kernel
        for (prop in EMULATOR_PROPS) {
            try {
                val process = Runtime.getRuntime().exec("getprop $prop")
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val value = reader.readLine() ?: continue
                    if (value.isNotEmpty() && value != "0") {
                        return true
                    }
                }
            } catch (e: Exception) {
                // Property not accessible
            }
        }

        return false
    }

    /**
     * Detect root access by checking for su binary and other indicators.
     */
    private fun detectRoot(): Boolean {
        return ROOT_BINARIES.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Apply random jitter to operation delays.
     * SECURITY: Adds ±20% randomness to prevent timing-based detection
     */
    fun applyJitter(baseMs: Long): Long {
        val jitter = (baseMs * 0.2 * (Math.random() * 2 - 1)).toLong()
        return (baseMs + jitter).coerceAtLeast(100L)
    }
}
