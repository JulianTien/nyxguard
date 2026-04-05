package com.scf.nyxguard.ui.guardian

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.scf.nyxguard.MainActivity
import com.scf.nyxguard.R
import com.scf.nyxguard.common.RoleMode
import com.scf.nyxguard.common.RoleModeStore
import com.scf.nyxguard.databinding.ActivityGuardianHubBinding

class GuardianHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardianHubBinding
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardianHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinator) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSwitchTraveler.setOnClickListener { switchToTravelerMode() }

        binding.tabDashboard.setOnClickListener { showFragment(DashboardTabFragment()) }
        binding.tabTrip.setOnClickListener { showFragment(TripDetailTabFragment()) }
        binding.tabSos.setOnClickListener { showFragment(SosDetailTabFragment()) }

        if (savedInstanceState == null) {
            showFragment(selectInitialFragment())
        } else {
            restoreFragment(savedInstanceState)
        }
    }

    private fun selectInitialFragment(): Fragment {
        return when (intent.getStringExtra(EXTRA_INITIAL_TAB)) {
            TAB_TRIP -> TripDetailTabFragment()
            TAB_SOS -> SosDetailTabFragment()
            else -> DashboardTabFragment()
        }
    }

    private fun restoreFragment(savedInstanceState: Bundle) {
        val tag = savedInstanceState.getString(KEY_ACTIVE_TAB) ?: TAB_DASHBOARD
        val fragment = when (tag) {
            TAB_TRIP -> supportFragmentManager.findFragmentByTag(TAB_TRIP) as? Fragment ?: TripDetailTabFragment()
            TAB_SOS -> supportFragmentManager.findFragmentByTag(TAB_SOS) as? Fragment ?: SosDetailTabFragment()
            else -> supportFragmentManager.findFragmentByTag(TAB_DASHBOARD) as? Fragment ?: DashboardTabFragment()
        }
        showFragment(fragment)
    }

    private fun showFragment(fragment: Fragment) {
        val tag = fragmentTag(fragment)
        supportFragmentManager.beginTransaction()
            .replace(R.id.guardian_fragment_container, fragment, tag)
            .commit()
        activeFragment = fragment
        renderTabs(tag)
    }

    private fun fragmentTag(fragment: Fragment): String {
        return when (fragment) {
            is TripDetailTabFragment -> TAB_TRIP
            is SosDetailTabFragment -> TAB_SOS
            else -> TAB_DASHBOARD
        }
    }

    private fun renderTabs(activeTag: String) {
        binding.tabDashboard.isChecked = activeTag == TAB_DASHBOARD
        binding.tabTrip.isChecked = activeTag == TAB_TRIP
        binding.tabSos.isChecked = activeTag == TAB_SOS
    }

    private fun switchToTravelerMode() {
        RoleModeStore.set(this, RoleMode.TRAVELER)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACTIVE_TAB, fragmentTag(activeFragment ?: DashboardTabFragment()))
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "guardian_initial_tab"
        const val TAB_DASHBOARD = "dashboard"
        const val TAB_TRIP = "trip"
        const val TAB_SOS = "sos"
        private const val KEY_ACTIVE_TAB = "active_guardian_tab"
    }
}
