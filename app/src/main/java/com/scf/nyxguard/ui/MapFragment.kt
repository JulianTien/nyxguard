package com.scf.nyxguard.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ConfigurationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.scf.nyxguard.R
import com.scf.nyxguard.common.ActiveTripStore
import com.scf.nyxguard.common.ThemePreferenceStore
import com.scf.nyxguard.databinding.FragmentMapBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.CurrentTripDto
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.ride.RideSettingActivity
import com.scf.nyxguard.ride.RideTrackingActivity
import com.scf.nyxguard.service.LocationTrackingService
import com.scf.nyxguard.util.AmapSdkInitializer
import com.scf.nyxguard.util.AndroidLocationProvider
import com.scf.nyxguard.walk.WalkSettingActivity
import com.scf.nyxguard.walk.WalkTrackingActivity

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var mapView: TextureMapView? = null
    private var aMap: AMap? = null
    private var locationSubscription: AndroidLocationProvider.Subscription? = null
    private var mapAvailable = false
    private var mapSupported = true
    private var currentTrip: CurrentTripDto? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            startLocation()
        } else {
            Toast.makeText(requireContext(), getString(R.string.map_location_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AmapSdkInitializer.ensureInitialized(requireContext())
        mapSupported = supportsOpenGLES2()
        if (mapSupported) {
            initMap(savedInstanceState)
        } else {
            showFallback()
        }

        setupThemeToggle()
        setupActions()
        loadCurrentTrip()
    }

    private fun supportsOpenGLES2(): Boolean {
        val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info: ConfigurationInfo = am.deviceConfigurationInfo
        return info.reqGlEsVersion >= 0x20000
    }

    private fun initMap(savedInstanceState: Bundle?) {
        try {
            hideFallback()
            mapView = TextureMapView(requireContext()).also { mv ->
                binding.mapContainer.addView(mv)
                mv.onCreate(savedInstanceState)
                aMap = mv.map
                mapAvailable = true
            }
            setupMap()
            checkLocationPermission()
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.map_init_failed), e)
            showFallback()
        }
    }

    private fun showFallback() {
        mapAvailable = false
        binding.mapContainer.visibility = View.GONE
        binding.fallbackLayout.visibility = View.VISIBLE
        binding.fabLocation.visibility = View.GONE
        binding.mapLightScrim.visibility = View.GONE
        binding.mapGridOverlay.visibility = View.GONE
        binding.mapRoutePreview.visibility = View.GONE
    }

    private fun hideFallback() {
        binding.mapContainer.visibility = View.VISIBLE
        binding.fallbackLayout.visibility = View.GONE
        binding.mapLightScrim.visibility = View.GONE
        binding.mapGridOverlay.visibility = View.GONE
        binding.mapRoutePreview.visibility = View.GONE
    }

    private fun setupMap() {
        aMap?.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = false
            moveCamera(CameraUpdateFactory.zoomTo(16f))
        }
    }

    private fun checkLocationPermission() {
        val fineLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (fineLocation == PackageManager.PERMISSION_GRANTED ||
            coarseLocation == PackageManager.PERMISSION_GRANTED
        ) {
            startLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocation() {
        locationSubscription?.stop()
        locationSubscription = AndroidLocationProvider.requestLocationUpdates(
            requireContext(),
            10_000L
        ) { location ->
            onLocationReceived(location)
        }
    }

    private fun onLocationReceived(location: Location) {
        aMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                16f
            )
        )
        locationSubscription?.stop()
        locationSubscription = null
    }

    private fun setupThemeToggle() {
        binding.btnThemeToggle.setOnClickListener {
            ThemePreferenceStore.toggleLightDark(requireContext())
        }
    }

    private fun setupActions() {
        binding.btnCenterMap.setOnClickListener {
            val snapshot = ActiveTripStore.get(requireContext())
            if (snapshot != null) {
                openActiveTrip(snapshot)
            } else if (mapAvailable) {
                startLocation()
            }
        }
        binding.btnStartWalk.setOnClickListener {
            startActivity(Intent(requireContext(), WalkSettingActivity::class.java))
        }
        binding.btnStartRide.setOnClickListener {
            startActivity(Intent(requireContext(), RideSettingActivity::class.java))
        }
        binding.btnOpenTrip.setOnClickListener {
            showEndTripDialog()
        }
    }

    private fun openActiveTrip(snapshot: com.scf.nyxguard.common.ActiveTripSnapshot) {
        when (snapshot.mode) {
            "ride" -> {
                startActivity(Intent(requireContext(), RideTrackingActivity::class.java).apply {
                    putExtra(RideTrackingActivity.EXTRA_ACTIVE_TRIP_ID, snapshot.tripId)
                    putExtra(RideSettingActivity.EXTRA_DEST_LAT, snapshot.endLat)
                    putExtra(RideSettingActivity.EXTRA_DEST_LNG, snapshot.endLng)
                    putExtra(RideSettingActivity.EXTRA_DEST_NAME, snapshot.destination)
                    putExtra(RideSettingActivity.EXTRA_ESTIMATED_MINUTES, snapshot.etaMinutes)
                    putExtra(RideSettingActivity.EXTRA_PLATE_NUMBER, snapshot.plateNumber)
                    putExtra(RideSettingActivity.EXTRA_VEHICLE_TYPE, snapshot.vehicleType)
                    putExtra(RideSettingActivity.EXTRA_VEHICLE_COLOR, snapshot.vehicleColor)
                    putExtra(RideSettingActivity.EXTRA_GUARDIAN_SUMMARY, snapshot.guardianSummary)
                })
            }
            else -> {
                startActivity(Intent(requireContext(), WalkTrackingActivity::class.java).apply {
                    putExtra(WalkTrackingActivity.EXTRA_ACTIVE_TRIP_ID, snapshot.tripId)
                    putExtra(WalkSettingActivity.EXTRA_START_LAT, snapshot.startLat)
                    putExtra(WalkSettingActivity.EXTRA_START_LNG, snapshot.startLng)
                    putExtra(WalkSettingActivity.EXTRA_DEST_LAT, snapshot.endLat)
                    putExtra(WalkSettingActivity.EXTRA_DEST_LNG, snapshot.endLng)
                    putExtra(WalkSettingActivity.EXTRA_DEST_NAME, snapshot.destination)
                    putExtra(WalkSettingActivity.EXTRA_ESTIMATED_MINUTES, snapshot.etaMinutes)
                    putExtra(WalkSettingActivity.EXTRA_GUARDIAN_SUMMARY, snapshot.guardianSummary)
                })
            }
        }
    }

    private fun showEndTripDialog() {
        val snapshot = ActiveTripStore.get(requireContext())
        val activeMode = currentTrip?.mode ?: snapshot?.mode ?: return
        val messageRes = if (activeMode == "ride") {
            R.string.ride_end_message
        } else {
            R.string.walk_end_message
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.trip_end_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.dialog_end) { _, _ -> finishActiveTrip() }
            .setNegativeButton(R.string.dialog_continue, null)
            .show()
    }

    private fun finishActiveTrip() {
        val remoteId = currentTrip?.id?.takeIf { it > 0 }
        val snapshotId = ActiveTripStore.get(requireContext())?.tripId?.takeIf { it > 0 }
        val tripId = remoteId ?: snapshotId
        if (tripId == null) {
            completeTripFinish()
            return
        }
        ApiClient.service.finishTrip(tripId).enqueue(
            onSuccess = { completeTripFinish() },
            onError = { completeTripFinish() }
        )
    }

    private fun completeTripFinish() {
        ActiveTripStore.clear(requireContext())
        requireContext().stopService(Intent(requireContext(), LocationTrackingService::class.java))
        Toast.makeText(requireContext(), getString(R.string.trip_finished_safe), Toast.LENGTH_LONG).show()
        currentTrip = null
        renderGuardState(null)
        loadCurrentTrip()
    }

    private fun loadCurrentTrip() {
        ApiClient.service.getCurrentTrip().enqueue(
            onSuccess = { response ->
                currentTrip = response
                renderGuardState(response)
            },
            onError = {
                currentTrip = null
                renderGuardState(null)
            }
        )
    }

    private fun renderGuardState(remoteTrip: CurrentTripDto?) {
        val snapshot = ActiveTripStore.get(requireContext())
        val hasActiveTrip = remoteTrip != null || snapshot?.isActive == true

        syncMapState(hasActiveTrip)

        binding.guardActiveContent.visibility = if (hasActiveTrip) View.VISIBLE else View.GONE
        binding.guardIdleContent.visibility = if (hasActiveTrip) View.GONE else View.VISIBLE
        binding.guardStatusCard.alpha = if (hasActiveTrip) 1f else 0.94f

        if (!hasActiveTrip) {
            binding.guardTitleText.setText(R.string.guard_proto_idle_title)
            binding.guardGuardianText.setText(R.string.guard_idle_subtitle)
            binding.guardEtaText.setText(R.string.guard_no_trip_eta_short)
            binding.guardSubtitleText.setText(R.string.guard_no_trip_eta)
            binding.guardIdleTitleText.setText(R.string.guard_idle_title)
            binding.guardSheetBody.setText(R.string.guard_location_sheet_body)
            return
        }

        val destination = remoteTrip?.destination ?: snapshot?.destination.orEmpty()
        val mode = remoteTrip?.mode ?: snapshot?.mode.orEmpty()
        val eta = remoteTrip?.eta_minutes ?: snapshot?.etaMinutes ?: 0
        val guardianCount = remoteTrip?.guardian_count ?: parseGuardianCount(snapshot?.guardianSummary)

        binding.guardTitleText.text = getString(
            R.string.guard_active_title,
            if (mode == "ride") getString(R.string.guard_proto_mode_ride) else getString(R.string.guard_proto_mode_walk)
        )
        binding.guardGuardianText.text = getString(R.string.guard_active_guardians, guardianCount)
        binding.guardEtaText.text = eta.coerceAtLeast(0).toString()
        binding.guardSubtitleText.setText(R.string.guard_active_eta_suffix)
        binding.guardSheetLabel.setText(R.string.guard_destination_label)
        binding.guardSheetValue.text = destination.ifBlank { getString(R.string.guard_idle_title) }
    }

    private fun parseGuardianCount(summary: String?): Int {
        return summary
            ?.split(",", "，", " ", "、")
            ?.map { it.trim() }
            ?.count { it.isNotEmpty() }
            ?.takeIf { it > 0 }
            ?: 0
    }

    private fun syncMapState(hasActiveTrip: Boolean) {
        if (!mapSupported) {
            showFallback()
            return
        }

        if (mapView == null) {
            initMap(null)
        } else {
            hideFallback()
            if (hasActiveTrip && mapAvailable) {
                startLocation()
            }
        }
    }

    private fun destroyMap() {
        locationSubscription?.stop()
        locationSubscription = null
        mapView?.onDestroy()
        mapView?.let { binding.mapContainer.removeView(it) }
        mapView = null
        aMap = null
        mapAvailable = false
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        loadCurrentTrip()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        destroyMap()
        _binding = null
    }

    /** 由 MainActivity 在 GLThread 崩溃时调用，切换到降级模式 */
    fun onGLContextFailed() {
        if (_binding != null && mapAvailable) {
            destroyMap()
            showFallback()
        }
    }

    companion object {
        private const val TAG = "MapFragment"
    }
}
