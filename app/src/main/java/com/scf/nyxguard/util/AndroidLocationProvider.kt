package com.scf.nyxguard.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat

object AndroidLocationProvider {

    interface Subscription {
        fun stop()
    }

    fun getLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        if (!hasLocationPermission(context)) return null

        return runCatching {
            candidateProviders(locationManager, hasFineLocationPermission(context))
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    fun requestSingleLocation(
        context: Context,
        onResult: (Location?) -> Unit,
    ): Subscription? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null || !hasLocationPermission(context)) {
            onResult(null)
            return null
        }

        getLastKnownLocation(context)?.let { cached ->
            onResult(cached)
            return null
        }

        val provider = candidateProviders(locationManager, hasFineLocationPermission(context)).firstOrNull()
        if (provider == null) {
            onResult(getLastKnownLocation(context))
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            runCatching {
                locationManager.getCurrentLocation(
                    provider,
                    signal,
                    context.mainExecutor
                ) { location ->
                    onResult(location ?: getLastKnownLocation(context))
                }
            }.onFailure {
                onResult(getLastKnownLocation(context))
            }
            object : Subscription {
                override fun stop() {
                    signal.cancel()
                }
            }
        } else {
            requestLegacySingleLocation(locationManager, provider, onResult)
        }
    }

    fun requestLocationUpdates(
        context: Context,
        intervalMs: Long,
        onLocation: (Location) -> Unit,
    ): Subscription? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        if (!hasLocationPermission(context)) return null

        val providers = candidateProviders(locationManager, hasFineLocationPermission(context))
        if (providers.isEmpty()) return null

        getLastKnownLocation(context)?.let(onLocation)

        val listeners = mutableListOf<LocationListener>()
        providers.forEach { provider ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onLocation(location)
                }
            }
            listeners += listener
            requestUpdatesSafely(locationManager, provider, intervalMs, listener)
        }

        return object : Subscription {
            override fun stop() {
                listeners.forEach { listener ->
                    runCatching { locationManager.removeUpdates(listener) }
                }
            }
        }
    }

    private fun candidateProviders(locationManager: LocationManager, includeGps: Boolean): List<String> {
        val preferred = buildList {
            add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            if (includeGps) {
                add(LocationManager.GPS_PROVIDER)
            }
        }
        val enabled = runCatching { locationManager.getProviders(true).toSet() }.getOrDefault(emptySet())
        return preferred.filter { it in enabled }
    }

    @SuppressLint("MissingPermission")
    private fun requestLegacySingleLocation(
        locationManager: LocationManager,
        provider: String,
        onResult: (Location?) -> Unit,
    ): Subscription {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { locationManager.removeUpdates(this) }
                onResult(location)
            }
        }
        runCatching {
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }.onFailure {
            onResult(null)
        }
        return object : Subscription {
            override fun stop() {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdatesSafely(
        locationManager: LocationManager,
        provider: String,
        intervalMs: Long,
        listener: LocationListener,
    ) {
        runCatching {
            locationManager.requestLocationUpdates(
                provider,
                intervalMs,
                0f,
                listener,
                Looper.getMainLooper()
            )
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return hasFineLocationPermission(context) || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
