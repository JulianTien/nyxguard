package com.scf.nyxguard.ride

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RouteSearch
import com.scf.nyxguard.common.GuardianSelectionDialog
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityRideSettingBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.GuardianDto
import com.scf.nyxguard.network.TripGuardianSelectionStore
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.profile.GuardianActivity
import com.scf.nyxguard.walk.PoiSearchAdapter

class RideSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRideSettingBinding

    private var startLatLng: LatLng? = null
    private var destLatLng: LatLng? = null
    private var destName: String = ""
    private var routePoints: ArrayList<LatLng> = arrayListOf()
    private var totalDistance: Int = 0
    private var estimatedMinutes: Int = 0
    private var guardians: List<GuardianDto> = emptyList()
    private val selectedGuardianIds = linkedSetOf<Int>()

    private var locationClient: AMapLocationClient? = null
    private val poiAdapter = PoiSearchAdapter { tip -> onPoiSelected(tip) }
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true)
        com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        binding = ActivityRideSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedGuardianIds.addAll(TripGuardianSelectionStore.getSelectedGuardianIds())

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinator) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupDropdowns()
        setupPoiSearch()
        setupGuardianSelection()
        setupStartButton()
        loadGuardians()
        locateCurrentPosition()
    }

    override fun onResume() {
        super.onResume()
        loadGuardians()
    }

    private fun setupDropdowns() {
        val vehicleTypes = resources.getStringArray(R.array.ride_vehicle_type_options)
        val colors = resources.getStringArray(R.array.ride_vehicle_color_options)

        binding.spinnerVehicleType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vehicleTypes)
        )
        binding.spinnerVehicleColor.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, colors)
        )
    }

    private fun locateCurrentPosition() {
        locationClient = AMapLocationClient(applicationContext).apply {
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isNeedAddress = true
            }
            setLocationOption(option)
            setLocationListener { location ->
                if (location != null && location.errorCode == 0) {
                    startLatLng = LatLng(location.latitude, location.longitude)
                    checkCanStart()
                }
            }
            startLocation()
        }
    }

    private fun setupPoiSearch() {
        binding.poiRecycler.layoutManager = LinearLayoutManager(this)
        binding.poiRecycler.adapter = poiAdapter

        binding.destInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
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
                    binding.btnStartRide.isEnabled = false
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
            binding.guardianSummaryText.setText(R.string.ride_guardian_empty_hint)
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

    private fun searchPoi(keyword: String) {
        val query = InputtipsQuery(keyword, "")
        val tips = Inputtips(this, query)
        tips.setInputtipsListener { tipList, rCode ->
            if (rCode == 1000 && tipList != null) {
                runOnUiThread {
                    poiAdapter.submitList(tipList)
                    binding.poiRecycler.visibility =
                        if (tipList.any { it.point != null }) View.VISIBLE else View.GONE
                }
            }
        }
        tips.requestInputtipsAsyn()
    }

    private fun onPoiSelected(tip: Tip) {
        destLatLng = LatLng(tip.point.latitude, tip.point.longitude)
        destName = tip.name
        binding.destInput.setText(destName)
        binding.destInput.clearFocus()
        binding.poiRecycler.visibility = View.GONE

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
        val query = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DrivingDefault, null, null, "")

        routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
            override fun onDriveRouteSearched(result: DriveRouteResult?, errorCode: Int) {
                if (errorCode != 1000 || result == null || result.paths.isNullOrEmpty()) {
                    Toast.makeText(this@RideSettingActivity, getString(R.string.route_planning_failed), Toast.LENGTH_SHORT).show()
                    return
                }
                val path = result.paths[0]
                totalDistance = path.distance.toInt()
                estimatedMinutes = (path.duration / 60).toInt().coerceAtLeast(1)

                routePoints.clear()
                for (step in path.steps) {
                    for (point in step.polyline) {
                        routePoints.add(LatLng(point.latitude, point.longitude))
                    }
                }

                binding.routeInfoCard.visibility = View.VISIBLE
                binding.routeDistance.text = if (totalDistance >= 1000) {
                    getString(R.string.route_distance_km, totalDistance / 1000.0)
                } else {
                    getString(R.string.route_distance_meters, totalDistance)
                }
                binding.routeTime.text = getString(R.string.route_duration_minutes, estimatedMinutes)

                checkCanStart()
            }

            override fun onBusRouteSearched(r: com.amap.api.services.route.BusRouteResult?, i: Int) {}
            override fun onWalkRouteSearched(r: com.amap.api.services.route.WalkRouteResult?, i: Int) {}
            override fun onRideRouteSearched(r: com.amap.api.services.route.RideRouteResult?, i: Int) {}
        })
        routeSearch.calculateDriveRouteAsyn(query)
    }

    private fun checkCanStart() {
        val plateValid = binding.inputPlate.text?.toString()?.trim()?.isNotEmpty() == true
        val destValid = destLatLng != null && routePoints.isNotEmpty()
        val startValid = startLatLng != null
        binding.btnStartRide.isEnabled = plateValid && destValid && startValid && selectedGuardianIds.isNotEmpty()
    }

    private fun setupStartButton() {
        // 监听车牌号输入变化
        binding.inputPlate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { checkCanStart() }
        })

        binding.btnStartRide.setOnClickListener {
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

            val intent = Intent(this, RideTrackingActivity::class.java).apply {
                putExtra(EXTRA_START_LAT, startLatLng!!.latitude)
                putExtra(EXTRA_START_LNG, startLatLng!!.longitude)
                putExtra(EXTRA_DEST_LAT, destLatLng!!.latitude)
                putExtra(EXTRA_DEST_LNG, destLatLng!!.longitude)
                putExtra(EXTRA_DEST_NAME, destName)
                putExtra(EXTRA_ESTIMATED_MINUTES, estimatedMinutes)
                putExtra(EXTRA_TOTAL_DISTANCE, totalDistance)
                putParcelableArrayListExtra(EXTRA_ROUTE_POINTS, routePoints)
                putExtra(EXTRA_PLATE_NUMBER, binding.inputPlate.text.toString().trim())
                putExtra(EXTRA_VEHICLE_TYPE, binding.spinnerVehicleType.text.toString())
                putExtra(EXTRA_VEHICLE_COLOR, binding.spinnerVehicleColor.text.toString())
                putExtra(EXTRA_REMARK, binding.inputRemark.text?.toString()?.trim() ?: "")
                putIntegerArrayListExtra(EXTRA_GUARDIAN_IDS, ArrayList(selectedGuardianIds))
                putExtra(EXTRA_GUARDIAN_SUMMARY, selectedNames)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    companion object {
        const val EXTRA_START_LAT = "start_lat"
        const val EXTRA_START_LNG = "start_lng"
        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"
        const val EXTRA_DEST_NAME = "dest_name"
        const val EXTRA_ESTIMATED_MINUTES = "estimated_minutes"
        const val EXTRA_TOTAL_DISTANCE = "total_distance"
        const val EXTRA_ROUTE_POINTS = "route_points"
        const val EXTRA_PLATE_NUMBER = "plate_number"
        const val EXTRA_VEHICLE_TYPE = "vehicle_type"
        const val EXTRA_VEHICLE_COLOR = "vehicle_color"
        const val EXTRA_REMARK = "remark"
        const val EXTRA_GUARDIAN_IDS = "guardian_ids"
        const val EXTRA_GUARDIAN_SUMMARY = "guardian_summary"
    }
}
