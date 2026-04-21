package com.scf.nyxguard.ui.guardian

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.FragmentGuardianSosDetailBinding
import com.scf.nyxguard.util.GuardianRouteStore

class SosDetailTabFragment : Fragment() {

    private var _binding: FragmentGuardianSosDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuardianSosDetailBinding.inflate(inflater, container, false)
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
        val route = GuardianRouteStore.get(requireContext())
        val sosId = route?.sosId
        GuardianModeRepository.loadSosDetail(
            sosId = sosId,
            onSuccess = { state ->
                if (_binding == null) return@loadSosDetail
                binding.loading.visibility = View.GONE
                val sos = state.sos
                if (sos == null) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.emptyState.text = getString(R.string.guardian_sos_empty)
                    return@loadSosDetail
                }
                binding.sosTitle.text = getString(R.string.guardian_sos_title)
                binding.sosState.text = getString(R.string.guardian_sos_state, sos.status)
                binding.sosTrip.text = getString(
                    R.string.guardian_sos_trip,
                    sos.traveler_nickname,
                    sos.mode ?: getString(R.string.guardian_trip_vehicle_unknown)
                )
                binding.sosLocation.text = getString(R.string.guardian_sos_location, sos.lat, sos.lng)
                binding.sosRoute.text = getString(
                    R.string.guardian_sos_route_points,
                    sos.recent_events.size
                )
                binding.sosHint.text = getString(R.string.guardian_sos_hint)
            },
            onError = {
                if (_binding == null) return@loadSosDetail
                binding.loading.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(R.string.guardian_sos_load_failed)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
