package com.example.routematch

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.routematch.databinding.ActivitySettingsBinding
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadSettings()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadSettings() {
        // PIN status
        updatePinStatus()

        // Match settings
        binding.sliderDetour.value = SecurityPrefs.getMaxDetour(this).toFloat()
        binding.sliderAngle.value = SecurityPrefs.getMaxAngle(this).toFloat()
        updateDetourLabel(binding.sliderDetour.value)
        updateAngleLabel(binding.sliderAngle.value)

        // Jitter
        binding.switchJitter.isChecked = SecurityPrefs.isJitterEnabled(this)

        // TTS
        binding.switchTTS.isChecked = SecurityPrefs.isTtsEnabled(this)

        // Manual location
        val isManual = SecurityPrefs.isManualLocationEnabled(this)
        binding.switchManualLocation.isChecked = isManual
        binding.layoutLat.visibility = if (isManual) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutLng.visibility = if (isManual) android.view.View.VISIBLE else android.view.View.GONE

        SecurityPrefs.getManualLat(this)?.let {
            binding.etManualLat.setText(it.toString())
        }
        SecurityPrefs.getManualLng(this)?.let {
            binding.etManualLng.setText(it.toString())
        }
    }

    private fun setupListeners() {
        // PIN setup button
        binding.btnPinSetup.setOnClickListener { showPinDialog() }

        // Detour slider
        binding.sliderDetour.addOnChangeListener { slider, value, _ ->
            updateDetourLabel(value)
            SecurityPrefs.setMaxDetour(this, value.toDouble())
        }

        // Angle slider
        binding.sliderAngle.addOnChangeListener { slider, value, _ ->
            updateAngleLabel(value)
            SecurityPrefs.setMaxAngle(this, value.toDouble())
        }

        // Jitter toggle
        binding.switchJitter.setOnCheckedChangeListener { _, isChecked ->
            SecurityPrefs.setJitterEnabled(this, isChecked)
        }

        // TTS toggle
        binding.switchTTS.setOnCheckedChangeListener { _, isChecked ->
            TTSHelper.setEnabled(isChecked)
            SecurityPrefs.setTtsEnabled(this, isChecked)
        }

        // Test TTS button
        binding.btnTestTTS.setOnClickListener {
            TTSHelper.init(this@SettingsActivity)
            TTSHelper.speak("测试语音播报正常")
        }

        // Manual location toggle
        binding.switchManualLocation.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutLat.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            binding.layoutLng.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            SecurityPrefs.setManualLocationEnabled(this, isChecked)
            if (!isChecked) {
                SecurityPrefs.clearManualLocation(this)
            }
        }
    }

    private fun updateDetourLabel(value: Float) {
        binding.tvDetourValue.text = getString(R.string.settings_max_detour) + ": ${value.toInt()} 米"
    }

    private fun updateAngleLabel(value: Float) {
        binding.tvAngleValue.text = getString(R.string.settings_max_angle) + ": ${value.toInt()} 度"
    }

    private fun updatePinStatus() {
        val isSet = SecurityPrefs.isPinSet(this)
        binding.btnPinSetup.text = if (isSet) {
            getString(R.string.settings_pin_change)
        } else {
            getString(R.string.settings_pin_set)
        }
        binding.tvPinStatus.text = if (isSet) {
            "PIN 已设置"
        } else {
            getString(R.string.settings_pin_summary)
        }
    }

    private fun showPinDialog() {
        val isSet = SecurityPrefs.isPinSet(this)

        if (isSet) {
            // Show options: change or remove
            val options = arrayOf(
                getString(R.string.settings_pin_change),
                getString(R.string.settings_pin_remove)
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_pin_title))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showPinInputDialog(false)
                        1 -> {
                            SecurityPrefs.removePin(this)
                            updatePinStatus()
                            Toast.makeText(this, "PIN 已移除", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } else {
            showPinInputDialog(false)
        }
    }

    private fun showPinInputDialog(isConfirm: Boolean) {
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.pin_dialog, null)
        val etPin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPinInput)
        val tvError = view.findViewById<android.widget.TextView>(R.id.tvPinError)

        if (isConfirm) {
            etPin.hint = getString(R.string.settings_pin_confirm)
        } else {
            etPin.hint = getString(R.string.settings_pin_hint)
        }

        var firstPin: String? = null

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_pin_title))
            .setView(view)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val pin = etPin.text?.toString() ?: ""

                if (pin.length < 4 || pin.length > 6) {
                    tvError.visibility = android.view.View.VISIBLE
                    tvError.text = getString(R.string.settings_pin_too_short)
                    return@setPositiveButton
                }

                if (!pin.all { it.isDigit() }) {
                    tvError.visibility = android.view.View.VISIBLE
                    tvError.text = "PIN 只能包含数字"
                    return@setPositiveButton
                }

                if (!isConfirm) {
                    // First entry - ask for confirmation
                    showPinInputDialog(true)
                    firstPin = pin
                } else {
                    // Confirmation entry
                    if (pin == firstPin) {
                        SecurityPrefs.setPin(this@SettingsActivity, pin)
                        updatePinStatus()
                        Toast.makeText(this@SettingsActivity, "PIN 已设置", Toast.LENGTH_SHORT).show()
                    } else {
                        tvError.visibility = android.view.View.VISIBLE
                        tvError.text = getString(R.string.settings_pin_mismatch)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Reload manual location values if changed
        val isManual = SecurityPrefs.isManualLocationEnabled(this)
        binding.switchManualLocation.isChecked = isManual
        binding.layoutLat.visibility = if (isManual) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutLng.visibility = if (isManual) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onDestroy() {
        // Save manual location values
        val latStr = binding.etManualLat.text?.toString() ?: ""
        val lngStr = binding.etManualLng.text?.toString() ?: ""
        if (latStr.isNotBlank() && lngStr.isNotBlank()) {
            try {
                val lat = latStr.toDouble()
                val lng = lngStr.toDouble()
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    SecurityPrefs.setManualLocation(this, lat, lng)
                }
            } catch (e: NumberFormatException) {
                // Ignore invalid input
            }
        }
        super.onDestroy()
    }
}
