package com.example.routematch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnAction: Button

    // Permission page data
    val permissionPages = listOf(
        PermissionPage(
            titleRes = R.string.perm_notification_title,
            descRes = R.string.perm_notification_desc,
            iconRes = android.R.drawable.ic_dialog_info,
            permissionId = PERM_NOTIFICATION
        ),
        PermissionPage(
            titleRes = R.string.perm_overlay_title,
            descRes = R.string.perm_overlay_desc,
            iconRes = android.R.drawable.ic_menu_view,
            permissionId = PERM_OVERLAY
        ),
        PermissionPage(
            titleRes = R.string.perm_location_title,
            descRes = R.string.perm_location_desc,
            iconRes = android.R.drawable.ic_menu_mylocation,
            permissionId = PERM_LOCATION
        ),
        PermissionPage(
            titleRes = R.string.perm_battery_title,
            descRes = R.string.perm_battery_desc,
            iconRes = android.R.drawable.ic_lock_idle_lock,
            permissionId = PERM_BATTERY
        )
    )

    private lateinit var adapter: PermissionPagerAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabIndicator)
        btnAction = findViewById(R.id.btnAction)

        adapter = PermissionPagerAdapter(this, permissionPages)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        updatePermissionStatus()

        btnAction.setOnClickListener {
            val currentPage = viewPager.currentItem
            val permission = permissionPages.getOrNull(currentPage) ?: return@setOnClickListener

            when (permission.permissionId) {
                PERM_NOTIFICATION -> openNotificationSettings()
                PERM_OVERLAY -> openOverlaySettings()
                PERM_LOCATION -> requestLocationPermission()
                PERM_BATTERY -> openBatterySettings()
            }

            // Refresh status after a short delay
            viewPager.postDelayed({ updatePermissionStatus() }, 500)
        }

        // Update button text when page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateActionButton(position)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        permissionPages.forEach { page ->
            page.isGranted = when (page.permissionId) {
                PERM_NOTIFICATION -> NotificationListener.isListenerEnabled(this)
                PERM_OVERLAY -> FloatingService.hasOverlayPermission(this)
                PERM_LOCATION -> hasLocationPermission()
                PERM_BATTERY -> isBatteryOptimizationIgnored()
                else -> false
            }
        }
        adapter.notifyDataSetChanged()
        updateActionButton(viewPager.currentItem)
        checkAllPermissionsAndProceed()
    }

    private fun updateActionButton(position: Int) {
        val page = permissionPages.getOrNull(position) ?: return
        btnAction.text = if (page.isGranted) {
            if (position < permissionPages.size - 1) getString(R.string.btn_next)
            else getString(R.string.btn_start)
        } else {
            getString(R.string.btn_grant)
        }
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun checkAllPermissionsAndProceed() {
        val allGranted = permissionPages.all { it.isGranted }
        if (allGranted) {
            btnAction.text = getString(R.string.btn_start)
            btnAction.setOnClickListener {
                // Start the floating service and finish
                startFloatingService()
            }
        } else {
            btnAction.setOnClickListener {
                val currentPage = viewPager.currentItem
                val permission = permissionPages[currentPage]
                when (permission.permissionId) {
                    PERM_NOTIFICATION -> openNotificationSettings()
                    PERM_OVERLAY -> openOverlaySettings()
                    PERM_LOCATION -> requestLocationPermission()
                    PERM_BATTERY -> openBatterySettings()
                }
                viewPager.postDelayed({ updatePermissionStatus() }, 500)
            }
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    data class PermissionPage(
        val titleRes: Int,
        val descRes: Int,
        val iconRes: Int,
        val permissionId: Int,
        var isGranted: Boolean = false
    )

    companion object {
        private const val PERM_NOTIFICATION = 1
        private const val PERM_OVERLAY = 2
        private const val PERM_LOCATION = 3
        private const val PERM_BATTERY = 4
    }
}

class PermissionPagerAdapter(
    private val activity: AppCompatActivity,
    private val pages: List<MainActivity.PermissionPage>
) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): androidx.fragment.app.Fragment {
        return PermissionPageFragment.newInstance(position)
    }
}

class PermissionPageFragment : androidx.fragment.app.Fragment() {

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int) = PermissionPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_POSITION, position) }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        return inflater.inflate(R.layout.permission_page, container, false)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val position = arguments?.getInt(ARG_POSITION, 0) ?: 0
        val activity = requireActivity() as? MainActivity ?: return
        val page = activity.permissionPages.getOrNull(position) ?: return

        view.findViewById<ImageView>(R.id.ivPageIcon).setImageResource(page.iconRes)
        view.findViewById<TextView>(R.id.tvPageTitle).setText(page.titleRes)
        view.findViewById<TextView>(R.id.tvPageDesc).setText(page.descRes)

        val tvStatus = view.findViewById<TextView>(R.id.tvPageStatus)
        if (page.isGranted) {
            tvStatus.visibility = android.view.View.VISIBLE
            tvStatus.text = "✓ 已开启"
            tvStatus.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    R.color.permission_granted
                )
            )
        } else {
            tvStatus.visibility = android.view.View.VISIBLE
            tvStatus.text = "○ 未开启"
            tvStatus.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    R.color.permission_denied
                )
            )
        }
    }
}
