package com.scf.nyxguard.ride

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scf.nyxguard.BuildConfig
import com.scf.nyxguard.CountdownDialogFragment
import com.scf.nyxguard.R
import com.scf.nyxguard.common.ActiveTripSnapshot
import com.scf.nyxguard.common.ActiveTripStore
import com.scf.nyxguard.data.local.PendingLocationStore
import com.scf.nyxguard.databinding.ActivityRideTrackingBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.CreateTripRequest
import com.scf.nyxguard.network.LocationUploadItem
import com.scf.nyxguard.network.ProactiveChatRequest
import com.scf.nyxguard.network.SosRequest
import com.scf.nyxguard.network.TripAlertRequest
import com.scf.nyxguard.network.UploadLocationsRequest
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.service.LocationTrackingService
import com.scf.nyxguard.util.AmapSdkInitializer
import com.scf.nyxguard.util.GeoUtils
import com.scf.nyxguard.util.MockLocationHelper
import com.scf.nyxguard.util.SosAudioPlaceholder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRideTrackingBinding

    private var mapView: TextureMapView? = null
    private var aMap: AMap? = null

    // 行程数据
    private var destLatLng: LatLng? = null
    private var destName: String = ""
    private var routePoints: List<LatLng> = emptyList()
    private var totalDistance: Int = 0
    private var estimatedMinutes: Int = 0
    private var startTimeMillis: Long = 0
    private var guardianSummary: String = ""
    private var guardianIds: List<Int> = emptyList()
    private var startLatLng: LatLng? = null

    // 车辆信息
    private var plateNumber: String = ""
    private var vehicleType: String = ""
    private var vehicleColor: String = ""

    // 追踪状态
    private val trajectory = mutableListOf<LatLng>()
    private var deviationAlertShown = false
    private var arrivalConfirmed = false
    private var tripId: Int = -1
    private val pendingLocations = mutableListOf<LocationUploadItem>()
    private var lastUploadTime = 0L
    private var isUploadingLocations = false
    private var lastProactiveMessageAt = 0L
    private var lastMovementAt = 0L
    private var lastMotionLatLng: LatLng? = null
    private var idlePromptSent = false
    private val pendingLocationStore by lazy { PendingLocationStore.getInstance(applicationContext) }

    // 服务
    private var trackingService: LocationTrackingService? = null
    private var serviceBound = false
    private var mockHelper: MockLocationHelper? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as LocationTrackingService.LocationBinder).getService()
            trackingService = service
            serviceBound = true
            service.onLocationUpdate = { location ->
                runOnUiThread { onLocationReceived(LatLng(location.latitude, location.longitude)) }
            }
            flushQueuedLocationsIfReady()
            flushPersistedLocations()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            trackingService = null
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startTrackingService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AmapSdkInitializer.ensureInitialized(this)

        binding = ActivityRideTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parseIntent()
        lastMovementAt = System.currentTimeMillis()
        persistActiveTrip()
        initMap(savedInstanceState)
        setupUI()
        createTripOnServer()
        requestNotificationPermissionAndStart()
    }

    private fun createTripOnServer() {
        if (tripId > 0) return
        val dest = destLatLng ?: return
        ApiClient.service.createTrip(
            CreateTripRequest(
                trip_type = "ride",
                start_lat = routePoints.firstOrNull()?.latitude ?: 0.0,
                start_lng = routePoints.firstOrNull()?.longitude ?: 0.0,
                end_lat = dest.latitude,
                end_lng = dest.longitude,
                end_name = destName,
                plate_number = plateNumber,
                vehicle_type = vehicleType,
                vehicle_color = vehicleColor,
                estimated_minutes = estimatedMinutes,
                guardian_ids = guardianIds,
            )
        ).enqueue(
            onSuccess = { trip ->
                tripId = trip.id
                ActiveTripStore.updateTripId(this, trip.id)
                flushQueuedLocationsIfReady()
                flushPersistedLocations()
                sendProactiveMessage(trigger = "start", showToast = true)
            },
            onError = {}
        )
    }

    private fun parseIntent() {
        startLatLng = LatLng(
            intent.getDoubleExtra(RideSettingActivity.EXTRA_START_LAT, 0.0),
            intent.getDoubleExtra(RideSettingActivity.EXTRA_START_LNG, 0.0)
        )
        destLatLng = LatLng(
            intent.getDoubleExtra(RideSettingActivity.EXTRA_DEST_LAT, 0.0),
            intent.getDoubleExtra(RideSettingActivity.EXTRA_DEST_LNG, 0.0)
        )
        destName = intent.getStringExtra(RideSettingActivity.EXTRA_DEST_NAME) ?: ""
        estimatedMinutes = intent.getIntExtra(RideSettingActivity.EXTRA_ESTIMATED_MINUTES, 30)
        totalDistance = intent.getIntExtra(RideSettingActivity.EXTRA_TOTAL_DISTANCE, 0)
        routePoints = intent.getParcelableArrayListExtra(RideSettingActivity.EXTRA_ROUTE_POINTS) ?: emptyList()
        plateNumber = intent.getStringExtra(RideSettingActivity.EXTRA_PLATE_NUMBER) ?: ""
        vehicleType = intent.getStringExtra(RideSettingActivity.EXTRA_VEHICLE_TYPE) ?: ""
        vehicleColor = intent.getStringExtra(RideSettingActivity.EXTRA_VEHICLE_COLOR) ?: ""
        guardianIds = intent.getIntegerArrayListExtra(RideSettingActivity.EXTRA_GUARDIAN_IDS) ?: emptyList()
        guardianSummary = intent.getStringExtra(RideSettingActivity.EXTRA_GUARDIAN_SUMMARY).orEmpty()
        tripId = intent.getIntExtra(EXTRA_ACTIVE_TRIP_ID, -1)
        startTimeMillis = System.currentTimeMillis()
    }

    private fun persistActiveTrip() {
        val start = startLatLng ?: return
        val dest = destLatLng ?: return
        ActiveTripStore.save(
            this,
            ActiveTripSnapshot(
                isActive = true,
                tripId = tripId,
                mode = "ride",
                destination = destName,
                etaMinutes = estimatedMinutes,
                guardianSummary = guardianSummary,
                startLat = start.latitude,
                startLng = start.longitude,
                endLat = dest.latitude,
                endLng = dest.longitude,
                plateNumber = plateNumber,
                vehicleType = vehicleType,
                vehicleColor = vehicleColor
            )
        )
    }

    private fun initMap(savedInstanceState: Bundle?) {
        try {
            mapView = TextureMapView(this).also { mv ->
                binding.mapContainer.addView(mv)
                mv.onCreate(savedInstanceState)
                aMap = mv.map
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.map_init_failed), Toast.LENGTH_SHORT).show()
            return
        }

        aMap?.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false

            // 规划路线（橙色）
            if (routePoints.isNotEmpty()) {
                addPolyline(
                    PolylineOptions()
                        .addAll(routePoints)
                        .width(14f)
                        .color(0x80FF9800.toInt())
                )
                val bounds = LatLngBounds.Builder()
                routePoints.forEach { bounds.include(it) }
                moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            }
        }
    }

    private fun setupUI() {
        binding.timer.base = SystemClock.elapsedRealtime()
        binding.timer.start()

        // 车辆信息
        binding.plateNumber.text = plateNumber
        binding.vehicleDesc.text = buildString {
            if (vehicleColor.isNotEmpty()) append(vehicleColor)
            if (vehicleType.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append(vehicleType)
            }
        }

        // 行程信息
        val arrivalTime = Date(startTimeMillis + estimatedMinutes * 60_000L)
        binding.estimatedArrival.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(arrivalTime)
        binding.remainingDistance.text = formatDistance(totalDistance)
        binding.guardianSummaryText.text = guardianSummary.ifBlank {
            getString(R.string.guardian_trip_none_selected)
        }

        binding.btnEndTrip.setOnClickListener { showEndTripDialog() }
        binding.btnSos.setOnClickListener { triggerSOS() }

        // Debug: 长按顶部状态栏启动模拟
        if (BuildConfig.DEBUG) {
            binding.topBar.setOnLongClickListener {
                startMockRide()
                true
            }
        }
    }

    private fun requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTrackingService()
        }
    }

    private fun startTrackingService() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            putExtra(LocationTrackingService.EXTRA_INTERVAL, LocationTrackingService.DEFAULT_RIDE_INTERVAL)
            putExtra(
                LocationTrackingService.EXTRA_NOTIFICATION_TEXT,
                getString(R.string.ride_notification_text, plateNumber)
            )
        }
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        flushQueuedLocationsIfReady()
        flushPersistedLocations()
    }

    // ==================== 模拟乘车（Debug） ====================

    private fun startMockRide() {
        if (mockHelper != null) {
            mockHelper?.stop()
            mockHelper = null
            Toast.makeText(this, getString(R.string.ride_mock_stopped), Toast.LENGTH_SHORT).show()
            return
        }
        if (routePoints.size < 2) return
        Toast.makeText(this, getString(R.string.ride_mock_started), Toast.LENGTH_SHORT).show()
        mockHelper = MockLocationHelper(routePoints, 2_000) { latLng ->
            onLocationReceived(latLng)
        }
        mockHelper?.start()
    }

    // ==================== 位置更新 ====================

    private fun onLocationReceived(latLng: LatLng) {
        val now = System.currentTimeMillis()
        trajectory.add(latLng)

        if (trajectory.size >= 2) {
            aMap?.clear()
            if (routePoints.isNotEmpty()) {
                aMap?.addPolyline(
                    PolylineOptions().addAll(routePoints).width(14f).color(0x80FF9800.toInt())
                )
            }
            aMap?.addPolyline(
                PolylineOptions().addAll(trajectory).width(14f).color(0xFF386A20.toInt())
            )
        }

        aMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))

        val dest = destLatLng ?: return
        val remaining = GeoUtils.distanceBetween(latLng, dest)
        binding.remainingDistance.text = formatDistance(remaining.toInt())

        // 批量上报位置（每15秒）
        pendingLocations.add(
            LocationUploadItem(
                lat = latLng.latitude,
                lng = latLng.longitude,
                accuracy = 10.0,
                recorded_at = java.time.Instant.now().toString()
            )
        )
        if (tripId > 0 && now - lastUploadTime >= 15_000 && pendingLocations.isNotEmpty()) {
            flushQueuedLocationsIfReady()
        }

        updateMotionState(latLng, now)
        maybeSendPeriodicProactive(now)
        checkArrival(latLng, dest)
        checkDeviation(latLng)
    }

    private fun flushQueuedLocationsIfReady() {
        val currentTripId = tripId
        if (currentTripId <= 0 || isUploadingLocations) return
        val batch = drainPendingLocations()
        if (batch.isEmpty()) return
        lastUploadTime = System.currentTimeMillis()
        uploadLocationBatchToServer(currentTripId, batch)
    }

    private fun flushPersistedLocations() {
        val currentTripId = tripId
        if (currentTripId <= 0 || isUploadingLocations) return

        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                pendingLocationStore.loadBatch(currentTripId, "ride")
            }
            if (cached.isEmpty()) {
                return@launch
            }
            uploadLocationBatchToServer(currentTripId, cached, mergeWithStored = false)
        }
    }

    private fun uploadLocationBatchToServer(
        tripIdSnapshot: Int,
        batch: List<LocationUploadItem>,
        mergeWithStored: Boolean = true
    ) {
        if (batch.isEmpty() || isUploadingLocations) return
        isUploadingLocations = true
        var uploadSucceeded = false
        var payloadSnapshot = batch

        lifecycleScope.launch {
            try {
                payloadSnapshot = if (mergeWithStored) {
                    val cached = withContext(Dispatchers.IO) {
                        pendingLocationStore.loadBatch(tripIdSnapshot, "ride")
                    }
                    if (cached.isEmpty()) batch else cached + batch
                } else {
                    batch
                }
                if (payloadSnapshot.isEmpty()) {
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.uploadLocations(tripIdSnapshot, UploadLocationsRequest(payloadSnapshot)).execute()
                }

                withContext(Dispatchers.IO) {
                    if (response.isSuccessful) {
                        pendingLocationStore.clearBatch(tripIdSnapshot, "ride")
                        uploadSucceeded = true
                    } else {
                        pendingLocationStore.saveBatch(tripIdSnapshot, "ride", payloadSnapshot)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.IO) {
                    pendingLocationStore.saveBatch(tripIdSnapshot, "ride", payloadSnapshot)
                }
            } finally {
                isUploadingLocations = false
                if (uploadSucceeded) {
                    flushQueuedLocationsIfReady()
                }
            }
        }
    }

    private fun drainPendingLocations(): List<LocationUploadItem> {
        if (pendingLocations.isEmpty()) return emptyList()
        val batch = pendingLocations.toList()
        pendingLocations.clear()
        return batch
    }

    // ==================== 到达检测（100m） ====================

    private fun checkArrival(current: LatLng, dest: LatLng) {
        if (arrivalConfirmed) return
        if (GeoUtils.distanceBetween(current, dest) <= 100f) {
            arrivalConfirmed = true
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.walk_arrival_title)
                .setMessage(getString(R.string.walk_arrival_message, destName))
                .setCancelable(false)
                .setPositiveButton(R.string.walk_arrival_confirm) { _, _ -> finishTrip() }
                .setNegativeButton(R.string.walk_arrival_not_yet) { _, _ -> arrivalConfirmed = false }
                .show()
        }
    }

    // ==================== 偏离检测（500m + 震动） ====================

    private fun checkDeviation(current: LatLng) {
        if (deviationAlertShown || routePoints.size < 2) return
        if (GeoUtils.distanceToPolyline(current, routePoints) > 500f) {
            deviationAlertShown = true
            sendTripAlert("ride_deviation", current)
            vibrateWarning()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ride_deviation_title)
                .setMessage(R.string.ride_deviation_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ride_deviation_safe) { _, _ -> deviationAlertShown = false }
                .setNegativeButton(R.string.ride_deviation_unsafe) { _, _ -> triggerSOS() }
                .show()
        }
    }

    /** 3次短震动 */
    private fun vibrateWarning() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 100, 100, 100, 100)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Exception) { }
    }

    // ==================== SOS ====================

    private fun triggerSOS() {
        val dialog = CountdownDialogFragment()
        dialog.setOnSOSConfirmedListener {
            trackingService?.enableSOSMode()
            if (tripId > 0) {
                val loc = trajectory.lastOrNull()
                ApiClient.service.triggerSOS(
                    tripId,
                    SosRequest(
                        lat = loc?.latitude ?: 0.0,
                        lng = loc?.longitude ?: 0.0,
                        audio_url = SosAudioPlaceholder.create(this, "ride")
                    )
                ).enqueue(onSuccess = {}, onError = {})
            }
            Toast.makeText(this, getString(R.string.ride_sos_sent, plateNumber), Toast.LENGTH_LONG).show()
        }
        dialog.show(supportFragmentManager, "SOS")
    }

    // ==================== 结束行程 ====================

    private fun showEndTripDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trip_end_title)
            .setMessage(R.string.ride_end_message)
            .setPositiveButton(R.string.dialog_end) { _, _ -> finishTrip() }
            .setNegativeButton(R.string.dialog_continue) { _, _ -> }
            .show()
    }

    private fun finishTrip() {
        mockHelper?.stop()
        if (tripId > 0) {
            ApiClient.service.finishTrip(tripId).enqueue(onSuccess = {}, onError = {})
        }
        ActiveTripStore.clear(this)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        stopService(Intent(this, LocationTrackingService::class.java))
        Toast.makeText(this, getString(R.string.trip_finished_safe), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun updateMotionState(latLng: LatLng, now: Long) {
        val previousMotionPoint = lastMotionLatLng
        if (previousMotionPoint == null || GeoUtils.distanceBetween(previousMotionPoint, latLng) >= 20f) {
            lastMotionLatLng = latLng
            lastMovementAt = now
            idlePromptSent = false
            return
        }

        if (!idlePromptSent && now - lastMovementAt >= 5 * 60_000L) {
            idlePromptSent = true
            sendProactiveMessage(trigger = "idle", showToast = true)
        }
    }

    private fun maybeSendPeriodicProactive(now: Long) {
        if (now - lastProactiveMessageAt >= RIDE_PROACTIVE_INTERVAL_MS) {
            sendProactiveMessage(trigger = "periodic")
        }
    }

    private fun sendProactiveMessage(trigger: String, showToast: Boolean = false) {
        lastProactiveMessageAt = System.currentTimeMillis()
        ApiClient.service.createProactiveMessage(
            ProactiveChatRequest(
                trigger = trigger,
                trip_id = tripId.takeIf { it > 0 }
            )
        ).enqueue(
            onSuccess = { message ->
                if (showToast) {
                    Toast.makeText(this, message.content, Toast.LENGTH_LONG).show()
                }
            },
            onError = { }
        )
    }

    private fun sendTripAlert(alertType: String, latLng: LatLng) {
        val currentTripId = tripId
        if (currentTripId <= 0) return
        lastProactiveMessageAt = System.currentTimeMillis()
        ApiClient.service.createTripAlert(
            currentTripId,
            TripAlertRequest(
                alert_type = alertType,
                lat = latLng.latitude,
                lng = latLng.longitude
            )
        ).enqueue(
            onSuccess = { response ->
                if (response.proactive_message.isNotBlank()) {
                    Toast.makeText(this, response.proactive_message, Toast.LENGTH_LONG).show()
                }
            },
            onError = { }
        )
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) {
            getString(R.string.distance_km, meters / 1000.0)
        } else {
            getString(R.string.distance_meters, meters)
        }
    }

    // ==================== 生命周期 ====================

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }

    override fun onDestroy() {
        super.onDestroy()
        mockHelper?.stop()
        binding.timer.stop()
        mapView?.onDestroy()
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() { showEndTripDialog() }

    companion object {
        const val EXTRA_ACTIVE_TRIP_ID = "active_trip_id"
        private const val RIDE_PROACTIVE_INTERVAL_MS = 5 * 60_000L
    }
}
