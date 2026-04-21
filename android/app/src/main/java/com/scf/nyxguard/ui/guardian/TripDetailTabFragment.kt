package com.scf.nyxguard.ui.guardian

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.FragmentGuardianTripDetailBinding
import com.scf.nyxguard.util.GuardianRouteStore

class TripDetailTabFragment : Fragment() {

    private var _binding: FragmentGuardianTripDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuardianTripDetailBinding.inflate(inflater, container, false)
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
        val tripId = route?.tripId
        GuardianModeRepository.loadCurrentTrip(
            tripId = tripId,
            onSuccess = { state ->
                if (_binding == null) return@loadCurrentTrip
                binding.loading.visibility = View.GONE
                val trip = state.trip
                if (trip == null) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.emptyState.text = getString(R.string.guardian_trip_empty)
                    return@loadCurrentTrip
                }
                binding.tripTitle.text = getString(R.string.guardian_trip_title, trip.destination)
                binding.tripMode.text = getString(
                    R.string.guardian_trip_mode,
                    trip.mode,
                    trip.status
                )
                binding.tripEta.text = getString(R.string.guardian_trip_eta, trip.eta_minutes)
                binding.tripVehicle.text = getString(
                    R.string.guardian_trip_vehicle,
                    getString(R.string.guardian_trip_vehicle_unknown),
                    getString(R.string.guardian_trip_vehicle_unknown),
                    getString(R.string.guardian_trip_vehicle_unknown)
                )
                binding.tripLocation.text = trip.latest_location?.let {
                    getString(R.string.guardian_trip_location, it.lat, it.lng)
                } ?: getString(R.string.guardian_trip_location_missing)
                binding.tripRoute.text = if (trip.route_preview.isNotEmpty()) {
                    getString(R.string.guardian_trip_route_points, trip.route_preview.size)
                } else {
                    getString(R.string.guardian_trip_route_missing)
                }
                binding.tripGuardian.text = getString(
                    R.string.guardian_trip_guardians,
                    trip.guardian_count,
                    trip.traveler_nickname
                )
            },
            onError = {
                if (_binding == null) return@loadCurrentTrip
                binding.loading.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(R.string.guardian_trip_load_failed)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
