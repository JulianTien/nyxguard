package com.scf.nyxguard.ui.guardian

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.FragmentGuardianDashboardBinding
import com.scf.nyxguard.network.enqueue

class DashboardTabFragment : Fragment() {

    private var _binding: FragmentGuardianDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuardianDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRefresh.setOnClickListener { loadData() }
        loadData()
    }

    private fun loadData() {
        binding.loading.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        GuardianModeRepository.loadOverview(
            onSuccess = { overview ->
                if (_binding == null) return@loadOverview
                binding.loading.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
                binding.dashboardTitle.text = getString(R.string.guardian_dashboard_title)
                binding.dashboardSubtitle.text = getString(
                    R.string.guardian_dashboard_subtitle,
                    overview.nickname,
                    overview.guardianCount
                )
                val activeTrip = overview.guardianDashboard?.protected_users
                    ?.mapNotNull { it.active_trip }
                    ?.firstOrNull()
                    ?: overview.activeTrip?.active_trip_brief
                if (activeTrip != null) {
                    binding.activeTripTitle.text = getString(
                        R.string.guardian_dashboard_active_trip,
                        activeTrip.destination
                    )
                    binding.activeTripBody.text = getString(
                        R.string.guardian_dashboard_active_trip_body,
                        activeTrip.mode,
                        activeTrip.status,
                        activeTrip.eta_minutes
                    )
                } else {
                    binding.activeTripTitle.text = getString(R.string.guardian_dashboard_no_trip_title)
                    binding.activeTripBody.text = getString(R.string.guardian_dashboard_no_trip_body)
                }
                val summary = overview.profileSummary
                binding.summaryBody.text = if (summary != null) {
                    getString(
                        R.string.guardian_dashboard_summary_body,
                        summary.frequent_routes_count,
                        summary.guard_minutes_total
                    )
                } else {
                    getString(R.string.guardian_dashboard_summary_fallback)
                }
            },
            onError = {
                if (_binding == null) return@loadOverview
                binding.loading.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(R.string.guardian_dashboard_load_failed)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
