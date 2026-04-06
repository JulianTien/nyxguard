package com.scf.nyxguard.walk

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkPath
import com.scf.nyxguard.common.GuardianSelectionDialog
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityWalkSettingBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.GuardianDto
import com.scf.nyxguard.network.TripGuardianSelectionStore
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.profile.GuardianActivity
import com.scf.nyxguard.util.AmapSdkInitializer
import com.scf.nyxguard.util.AndroidLocationProvider
import com.scf.nyxguard.util.GeoUtils
import com.scf.nyxguard.util.SystemLocationFallback

class WalkSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkSettingBinding

    private var startLatLng: LatLng? = null
    private var startName: String = ""
    private var destLatLng: LatLng? = null
    private var destName: String = ""
    private var routePoints: ArrayList<LatLng> = arrayListOf()
    private var totalDistance: Int = 0
    private var estimatedMinutes: Int = 0
    private var guardians: List<GuardianDto> = emptyList()
    private val selectedGuardianIds = linkedSetOf<Int>()

    private var locationRequest: AndroidLocationProvider.Subscription? = null
    private var mapView: TextureMapView? = null
    private var aMap: AMap? = null
    private val poiAdapter = PoiSearchAdapter { tip ->
        onPoiSelected(tip)
    }
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isApplyingPoiSelection = false
    private var allowPoiSuggestions = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AmapSdkInitializer.ensureInitialized(this)

        binding = ActivityWalkSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedGuardianIds.addAll(TripGuardianSelectionStore.getSelectedGuardianIds())

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinator) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupPoiSearch()
        setupGuardianSelection()
        setupStartTrip()
        loadGuardians()
        locateCurrentPosition()
    }

    private fun locateCurrentPosition() {
        locationRequest?.stop()
        locationRequest = AndroidLocationProvider.requestSingleLocation(this) { location ->
            if (location != null) {
                startLatLng = LatLng(location.latitude, location.longitude)
                startName = getString(R.string.walk_current_location)
                binding.startLocationText.text = getString(
                    R.string.walk_current_location_coords,
                    location.latitude,
                    location.longitude
                )
                binding.startLoading.visibility = View.GONE
                checkCanStart()
            } else {
                Log.w(TAG, "System locate failed, falling back to last known location")
                applySystemLocationFallback()
            }
        }

        if (locationRequest == null) {
            applySystemLocationFallback()
        }
    }

    private fun applySystemLocationFallback() {
        val fallbackLocation = SystemLocationFallback.getLastKnownLocation(this)
        if (fallbackLocation != null) {
            startLatLng = LatLng(fallbackLocation.latitude, fallbackLocation.longitude)
            startName = getString(R.string.walk_current_location)
            binding.startLocationText.text = getString(
                R.string.walk_current_location_coords,
                fallbackLocation.latitude,
                fallbackLocation.longitude
            )
            binding.startLoading.visibility = View.GONE
            checkCanStart()
        } else {
            binding.startLocationText.text = getString(R.string.walk_locate_failed)
            binding.startLoading.visibility = View.GONE
        }
    }

    private fun setupPoiSearch() {
        binding.poiRecycler.layoutManager = LinearLayoutManager(this)
        binding.poiRecycler.adapter = poiAdapter

        binding.destInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isApplyingPoiSelection) return
                allowPoiSuggestions = true
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val keyword = s?.toString()?.trim() ?: ""
                if (keyword.length < 2) {
                    binding.poiRecycler.visibility = View.GONE
                    return
                }
                searchRunnable = Runnable { searchPoi(keyword) }
                searchHandler.postDelayed(searchRunnable!!, 300)
            }
        })
    }

    private fun searchPoi(keyword: String) {
        val query = InputtipsQuery(keyword, "")
        val tips = Inputtips(this, query)
        tips.setInputtipsListener { tipList, rCode ->
            if (rCode == 1000 && tipList != null) {
                runOnUiThread {
                    val currentKeyword = binding.destInput.text?.toString()?.trim().orEmpty()
                    if (!allowPoiSuggestions || currentKeyword != keyword) {
                        return@runOnUiThread
                    }
                    poiAdapter.submitList(tipList)
                    binding.poiRecycler.visibility =
                        if (tipList.any { it.point != null }) View.VISIBLE else View.GONE
                }
            }
        }
        tips.requestInputtipsAsyn()
    }

    private fun onPoiSelected(tip: com.amap.api.services.help.Tip) {
        destLatLng = LatLng(tip.point.latitude, tip.point.longitude)
        destName = tip.name
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        allowPoiSuggestions = false
        isApplyingPoiSelection = true
        try {
            binding.destInput.setText(destName)
            binding.destInput.setSelection(destName.length)
        } finally {
            isApplyingPoiSelection = false
        }
        binding.destInput.clearFocus()
        binding.poiRecycler.visibility = View.GONE

        // 隐藏键盘
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.destInput.windowToken, 0)

        planRoute()
    }

    private fun planRoute() {
        val start = startLatLng ?: return
        val dest = destLatLng ?: return

        val routeSearch = RouteSearch(this)
        val from = LatLonPoint(start.latitude, start.longitude)
        val to = LatLonPoint(dest.latitude, dest.longitude)
        val fromAndTo = RouteSearch.FromAndTo(from, to)
        val query = RouteSearch.WalkRouteQuery(fromAndTo)

        routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
            override fun onWalkRouteSearched(result: com.amap.api.services.route.WalkRouteResult?, errorCode: Int) {
                if (errorCode != 1000 || result == null || result.paths.isNullOrEmpty()) {
                    Toast.makeText(this@WalkSettingActivity, getString(R.string.route_planning_failed), Toast.LENGTH_SHORT).show()
                    return
                }
                val path = result.paths[0]
                displayRoute(path, start, dest)
            }

            override fun onBusRouteSearched(r: com.amap.api.services.route.BusRouteResult?, i: Int) {}
            override fun onDriveRouteSearched(r: com.amap.api.services.route.DriveRouteResult?, i: Int) {}
            override fun onRideRouteSearched(r: com.amap.api.services.route.RideRouteResult?, i: Int) {}
        })
        routeSearch.calculateWalkRouteAsyn(query)
    }

    private fun displayRoute(path: WalkPath, start: LatLng, dest: LatLng) {
        totalDistance = path.distance.toInt()
        estimatedMinutes = GeoUtils.estimateWalkingMinutes(totalDistance)

        // 提取路线点
        routePoints.clear()
        for (step in path.steps) {
            for (point in step.polyline) {
                routePoints.add(LatLng(point.latitude, point.longitude))
            }
        }

        // 显示路线信息
        binding.routeInfoCard.visibility = View.VISIBLE
        binding.routeDistance.text = if (totalDistance >= 1000) {
            getString(R.string.route_distance_km, totalDistance / 1000.0)
        } else {
            getString(R.string.route_distance_meters, totalDistance)
        }
        binding.routeTime.text = getString(R.string.route_duration_minutes, estimatedMinutes)

        // 初始化地图预览
        initMapPreview(start, dest)
        checkCanStart()
    }

    private fun initMapPreview(start: LatLng, dest: LatLng) {
        binding.mapPreviewContainer.visibility = View.VISIBLE

        if (mapView == null) {
            try {
                mapView = TextureMapView(this).also { mv ->
                    binding.mapPreviewContainer.addView(mv, 0)
                    mv.onCreate(null)
                    aMap = mv.map
                    AmapSdkInitializer.applyMapLanguage(
                        context = this,
                        map = aMap,
                        scenePoints = routePoints + listOf(start, dest),
                        sceneLabels = listOf(destName),
                    )
                    aMap?.uiSettings?.apply {
                        isZoomControlsEnabled = false
                        isScrollGesturesEnabled = false
                        isZoomGesturesEnabled = false
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                    }
                }
            } catch (e: Exception) {
                binding.mapPreviewContainer.visibility = View.GONE
                return
            }
        }

        aMap?.clear()

        // 画路线
        if (routePoints.isNotEmpty()) {
            aMap?.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .width(12f)
                    .color(0xFF1A73E8.toInt())
            )
        }

        // 起点标记
        aMap?.addMarker(
            MarkerOptions()
                .position(start)
                .icon(BitmapDescriptorFactory.fromBitmap(
                    BitmapFactory.decodeResource(resources, R.drawable.ic_start_point)
                ))
                .title(getString(R.string.marker_start))
        )

        // 终点标记
        aMap?.addMarker(
            MarkerOptions()
                .position(dest)
                .icon(BitmapDescriptorFactory.fromBitmap(
                    BitmapFactory.decodeResource(resources, R.drawable.ic_destination)
                ))
                .title(getString(R.string.marker_destination))
        )

        // 调整视野包含整条路线
        val boundsBuilder = LatLngBounds.Builder()
        boundsBuilder.include(start)
        boundsBuilder.include(dest)
        routePoints.forEach { boundsBuilder.include(it) }
        aMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 60))
    }

    private fun setupGuardianSelection() {
        binding.btnSelectGuardians.setOnClickListener {
            if (guardians.isEmpty()) {
                openGuardianManagement()
                return@setOnClickListener
            }
            showGuardianSelectionDialog()
        }

        binding.btnManageGuardians.setOnClickListener {
            openGuardianManagement()
        }
    }

    private fun loadGuardians() {
        ApiClient.service.getGuardians().enqueue(
            onSuccess = { response ->
                guardians = response
                val availableIds = guardians.map { it.id }.toSet()
                val filteredSelection = linkedSetOf<Int>().also { filtered ->
                    selectedGuardianIds.filterTo(filtered) { it in availableIds }
                }
                selectedGuardianIds.clear()
                selectedGuardianIds.addAll(filteredSelection)
                TripGuardianSelectionStore.setSelectedGuardianIds(selectedGuardianIds)
                updateGuardianCard()
                checkCanStart()
            },
            onError = { msg ->
                if (guardians.isEmpty()) {
                    binding.guardianSummaryText.setText(R.string.guardian_trip_load_failed)
                    binding.btnStartTrip.isEnabled = false
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showGuardianSelectionDialog() {
        GuardianSelectionDialog.show(
            context = this,
            guardians = guardians,
            selectedGuardianIds = selectedGuardianIds
        ) { selectedIds ->
            selectedGuardianIds.clear()
            selectedGuardianIds.addAll(selectedIds)
            TripGuardianSelectionStore.setSelectedGuardianIds(selectedGuardianIds)
            updateGuardianCard()
            checkCanStart()
        }
    }

    private fun openGuardianManagement() {
        startActivity(Intent(this, GuardianActivity::class.java))
    }

    private fun updateGuardianCard() {
        if (guardians.isEmpty()) {
            binding.guardianSummaryText.setText(R.string.guardian_trip_empty_hint)
            return
        }

        if (selectedGuardianIds.isEmpty()) {
            binding.guardianSummaryText.setText(R.string.guardian_trip_none_selected)
            return
        }

        val selectedNames = guardians
            .filter { selectedGuardianIds.contains(it.id) }
            .joinToString(", ") { guardian ->
                if (guardian.relationship.isBlank()) guardian.nickname else "${guardian.nickname} (${guardian.relationship})"
            }

        binding.guardianSummaryText.text = getString(R.string.guardian_trip_selected_summary, selectedNames)
    }

    private fun checkCanStart() {
        binding.btnStartTrip.isEnabled =
            startLatLng != null &&
                destLatLng != null &&
                routePoints.isNotEmpty() &&
                selectedGuardianIds.isNotEmpty()
    }

    private fun setupStartTrip() {
        binding.btnStartTrip.setOnClickListener {
            if (selectedGuardianIds.isEmpty()) {
                Toast.makeText(this, getString(R.string.guardian_trip_need_selection), Toast.LENGTH_SHORT).show()
                openGuardianManagement()
                return@setOnClickListener
            }

            val selectedNames = guardians
                .filter { selectedGuardianIds.contains(it.id) }
                .joinToString(", ") { guardian ->
                    if (guardian.relationship.isBlank()) guardian.nickname else "${guardian.nickname} (${guardian.relationship})"
                }

            val intent = Intent(this, WalkTrackingActivity::class.java).apply {
                putExtra(EXTRA_START_LAT, startLatLng!!.latitude)
                putExtra(EXTRA_START_LNG, startLatLng!!.longitude)
                putExtra(EXTRA_START_NAME, startName)
                putExtra(EXTRA_DEST_LAT, destLatLng!!.latitude)
                putExtra(EXTRA_DEST_LNG, destLatLng!!.longitude)
                putExtra(EXTRA_DEST_NAME, destName)
                putExtra(EXTRA_ESTIMATED_MINUTES, estimatedMinutes)
                putExtra(EXTRA_TOTAL_DISTANCE, totalDistance)
                putIntegerArrayListExtra(EXTRA_GUARDIAN_IDS, ArrayList(selectedGuardianIds))
                putExtra(EXTRA_GUARDIAN_SUMMARY, selectedNames)
                putParcelableArrayListExtra(EXTRA_ROUTE_POINTS, routePoints)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        loadGuardians()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationRequest?.stop()
        mapView?.onDestroy()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    companion object {
        private const val TAG = "WalkSettingActivity"
        const val EXTRA_START_LAT = "start_lat"
        const val EXTRA_START_LNG = "start_lng"
        const val EXTRA_START_NAME = "start_name"
        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"
        const val EXTRA_DEST_NAME = "dest_name"
        const val EXTRA_ESTIMATED_MINUTES = "estimated_minutes"
        const val EXTRA_TOTAL_DISTANCE = "total_distance"
        const val EXTRA_ROUTE_POINTS = "route_points"
        const val EXTRA_GUARDIAN_IDS = "guardian_ids"
        const val EXTRA_GUARDIAN_SUMMARY = "guardian_summary"
    }
}
