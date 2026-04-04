package com.scf.nyxguard.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scf.nyxguard.R
import com.scf.nyxguard.alarm.AlarmActivity
import com.scf.nyxguard.common.ThemePreferenceStore
import com.scf.nyxguard.databinding.FragmentHomeBinding
import com.scf.nyxguard.fakecall.FakeCallSettingActivity
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.ride.RideSettingActivity
import com.scf.nyxguard.walk.WalkSettingActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGreeting()
        setupCards()
        loadDashboard()
    }

    private fun setupGreeting() {
        val localNickname = TokenManager.getNickname(requireContext()).ifBlank { "Sarah" }
        binding.greetingText.text = getString(R.string.home_greeting_with_name, getString(R.string.greeting_evening), localNickname)
        binding.greetingSubtitle.setText(R.string.home_guard_subtitle)
    }

    private fun setupCards() {
        binding.cardWalk.setOnClickListener {
            startActivity(Intent(requireContext(), WalkSettingActivity::class.java))
        }
        binding.cardRide.setOnClickListener {
            startActivity(Intent(requireContext(), RideSettingActivity::class.java))
        }
        binding.cardFakeCall.setOnClickListener {
            startActivity(Intent(requireContext(), FakeCallSettingActivity::class.java))
        }
        binding.cardAlarm.setOnClickListener {
            startActivity(Intent(requireContext(), AlarmActivity::class.java))
        }
        binding.btnThemeToggle.setOnClickListener {
            ThemePreferenceStore.toggleLightDark(requireContext())
            requireActivity().recreate()
        }
    }

    private fun loadDashboard() {
        ApiClient.service.getDashboard().enqueue(
            onSuccess = { response ->
                if (_binding == null) return@enqueue
                binding.greetingText.text =
                    getString(R.string.home_greeting_with_name, response.greeting, response.nickname)
                binding.greetingSubtitle.text = if (response.active_trip_brief != null) {
                    getString(
                        R.string.home_dashboard_active_trip,
                        response.active_trip_brief.destination,
                        response.active_trip_brief.guardian_count
                    )
                } else {
                    getString(R.string.home_guard_subtitle)
                }
            },
            onError = {
                if (_binding == null) return@enqueue
                setupGreeting()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
