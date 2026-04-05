package com.scf.nyxguard

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.scf.nyxguard.common.ActiveTripStore
import com.scf.nyxguard.databinding.ActivityMainBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.V2SosRequest
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.ui.ChatFragment
import com.scf.nyxguard.ui.HomeFragment
import com.scf.nyxguard.ui.MapFragment
import com.scf.nyxguard.ui.ProfileFragment
import com.scf.nyxguard.util.AmapSdkInitializer
import com.scf.nyxguard.util.AndroidLocationProvider
import com.scf.nyxguard.util.SosAudioPlaceholder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var homeFragment: HomeFragment
    private lateinit var mapFragment: MapFragment
    private lateinit var chatFragment: ChatFragment
    private lateinit var profileFragment: ProfileFragment
    private var activeFragment: Fragment? = null
    private var selectedNavId: Int = R.id.nav_home
    private var globalSosInFlight: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installGLThreadCrashGuard()

        AmapSdkInitializer.ensureInitialized(this)

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.bottomNavShell.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            setupFragments()
        } else {
            restoreFragments(savedInstanceState)
        }
        setupBottomNav()
        setupGlobalSos()
        renderBottomNav()
    }

    private fun setupFragments() {
        homeFragment = HomeFragment()
        mapFragment = MapFragment()
        chatFragment = ChatFragment()
        profileFragment = ProfileFragment()

        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, profileFragment, TAG_PROFILE).hide(profileFragment)
            .add(R.id.fragment_container, chatFragment, TAG_CHAT).hide(chatFragment)
            .add(R.id.fragment_container, mapFragment, TAG_MAP).hide(mapFragment)
            .add(R.id.fragment_container, homeFragment, TAG_HOME)
            .commitNow()
        activeFragment = homeFragment
    }

    private fun restoreFragments(savedInstanceState: Bundle) {
        val fm = supportFragmentManager
        homeFragment = fm.findFragmentByTag(TAG_HOME) as? HomeFragment ?: HomeFragment()
        mapFragment = fm.findFragmentByTag(TAG_MAP) as? MapFragment ?: MapFragment()
        chatFragment = fm.findFragmentByTag(TAG_CHAT) as? ChatFragment ?: ChatFragment()
        profileFragment = fm.findFragmentByTag(TAG_PROFILE) as? ProfileFragment ?: ProfileFragment()

        if (!homeFragment.isAdded || !mapFragment.isAdded || !chatFragment.isAdded || !profileFragment.isAdded) {
            fm.beginTransaction().apply {
                if (!profileFragment.isAdded) add(R.id.fragment_container, profileFragment, TAG_PROFILE).hide(profileFragment)
                if (!chatFragment.isAdded) add(R.id.fragment_container, chatFragment, TAG_CHAT).hide(chatFragment)
                if (!mapFragment.isAdded) add(R.id.fragment_container, mapFragment, TAG_MAP).hide(mapFragment)
                if (!homeFragment.isAdded) add(R.id.fragment_container, homeFragment, TAG_HOME)
            }.commitNow()
        }

        selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV, R.id.nav_home)
        val targetFragment = when (selectedNavId) {
            R.id.nav_profile -> profileFragment
            R.id.nav_chat -> chatFragment
            R.id.nav_map -> mapFragment
            else -> homeFragment
        }
        fm.beginTransaction().apply {
            listOf(homeFragment, mapFragment, chatFragment, profileFragment).forEach { fragment ->
                if (fragment === targetFragment) show(fragment) else hide(fragment)
            }
        }.commitNow()
        activeFragment = targetFragment
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener { switchFragment(homeFragment, R.id.nav_home) }
        binding.navMap.setOnClickListener { switchFragment(mapFragment, R.id.nav_map) }
        binding.navChat.setOnClickListener { switchFragment(chatFragment, R.id.nav_chat) }
        binding.navProfile.setOnClickListener { switchFragment(profileFragment, R.id.nav_profile) }
    }

    private fun setupGlobalSos() {
        binding.fabGlobalSos.setOnClickListener {
            if (globalSosInFlight || supportFragmentManager.findFragmentByTag("global-sos") != null) {
                return@setOnClickListener
            }
            val dialog = CountdownDialogFragment()
            dialog.setOnSOSConfirmedListener {
                triggerGlobalSos()
            }
            dialog.show(supportFragmentManager, "global-sos")
        }
    }

    private fun triggerGlobalSos() {
        if (globalSosInFlight) return
        globalSosInFlight = true
        val snapshot = ActiveTripStore.get(this)
        resolveSosLocation(
            onResolved = { lat, lng ->
                ApiClient.service.triggerGlobalSos(
                    V2SosRequest(
                        trip_id = snapshot?.tripId?.takeIf { it > 0 },
                        lat = lat,
                        lng = lng,
                        audio_url = SosAudioPlaceholder.create(this, "global")
                    )
                ).enqueue(
                    onSuccess = {
                        globalSosInFlight = false
                        Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                    },
                    onError = {
                        globalSosInFlight = false
                        Toast.makeText(this, getString(R.string.shell_sos_sent), Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_NAV, selectedNavId)
        super.onSaveInstanceState(outState)
    }

    private fun resolveSosLocation(onResolved: (Double, Double) -> Unit) {
        val snapshot = ActiveTripStore.get(this)
        val hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            onResolved(snapshot?.startLat ?: 0.0, snapshot?.startLng ?: 0.0)
            return
        }

        val request = AndroidLocationProvider.requestSingleLocation(this) { location ->
            if (location != null) {
                onResolved(location.latitude, location.longitude)
            } else {
                onResolved(snapshot?.startLat ?: 0.0, snapshot?.startLng ?: 0.0)
            }
        }

        if (request == null) {
            onResolved(snapshot?.startLat ?: 0.0, snapshot?.startLng ?: 0.0)
        }
    }

    private fun switchFragment(target: Fragment, navId: Int) {
        if (target === activeFragment) return
        supportFragmentManager.beginTransaction().apply {
            activeFragment?.let { hide(it) }
            show(target)
        }.commit()
        activeFragment = target
        selectedNavId = navId
        renderBottomNav()
    }

    private fun renderBottomNav() {
        updateNavItem(binding.navHome, binding.navHomeIcon, binding.navHomeLabel, selectedNavId == R.id.nav_home)
        updateNavItem(binding.navMap, binding.navMapIcon, binding.navMapLabel, selectedNavId == R.id.nav_map)
        updateNavItem(binding.navChat, binding.navChatIcon, binding.navChatLabel, selectedNavId == R.id.nav_chat)
        updateNavItem(binding.navProfile, binding.navProfileIcon, binding.navProfileLabel, selectedNavId == R.id.nav_profile)
    }

    private fun updateNavItem(container: LinearLayout, icon: ImageView, label: TextView, active: Boolean) {
        if (active) {
            val backgroundRes = if (container.id == R.id.nav_map) {
                R.drawable.bg_revamp_nav_active_outline
            } else {
                R.drawable.bg_revamp_nav_active
            }
            container.setBackgroundResource(backgroundRes)
            icon.imageTintList = getColorStateList(R.color.revamp_indigo)
            label.setTextColor(getColor(R.color.revamp_indigo))
            label.setTypeface(label.typeface, android.graphics.Typeface.BOLD)
        } else {
            container.background = null
            icon.imageTintList = ContextCompat.getColorStateList(this, R.color.revamp_text_muted)
            label.setTextColor(getColor(R.color.revamp_text_muted))
            label.setTypeface(label.typeface, android.graphics.Typeface.NORMAL)
        }
    }

    /**
     * 拦截 GLThread 上的 OpenGL 崩溃（模拟器常见），避免整个 App 闪退。
     * 仅吞掉 GLThread 的 createContext 异常，其余异常仍交给默认 handler。
     */
    private fun installGLThreadCrashGuard() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (thread.name.startsWith("GLThread") &&
                throwable.message?.contains("createContext") == true
            ) {
                Log.e(TAG, "GLThread 崩溃已拦截（模拟器不支持所需 OpenGL 版本）", throwable)
                // 在主线程通知 MapFragment 切换到降级模式
                runOnUiThread {
                    val mapFrag = supportFragmentManager.findFragmentByTag(TAG_MAP) as? MapFragment
                    mapFrag?.onGLContextFailed()
                }
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val TAG_HOME = "home"
        private const val TAG_MAP = "map"
        private const val TAG_CHAT = "chat"
        private const val TAG_PROFILE = "profile"
        private const val KEY_SELECTED_NAV = "selected_nav_id"
    }
}
