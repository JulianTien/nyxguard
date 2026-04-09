package com.scf.nyxguard.trip

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amap.api.maps.model.LatLng
import com.scf.nyxguard.R
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.ride.RideSettingActivity
import com.scf.nyxguard.ride.RideTrackingActivity
import com.scf.nyxguard.walk.WalkSettingActivity
import com.scf.nyxguard.walk.WalkTrackingActivity
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TrackingLifecycleInstrumentedTest {

    private lateinit var server: MockWebServer

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        server.dispatcher = trackingDispatcher()
        ApiClient.init(context, server.url("/").toString())
        TokenManager.saveLogin(context, TOKEN, USER_ID, NICKNAME)
        grantRuntimePermissions()
    }

    @After
    fun tearDown() {
        TokenManager.clearStoredLogin(context)
        server.shutdown()
    }

    @Test
    fun walkTracking_startAndFinish_callsTripApis() {
        launchActivity(walkIntent()).use {
            val createTrip = waitForRequest("/api/trips")
            assertNotNull("Expected walk createTrip request", createTrip)
            onView(withId(R.id.btn_end_trip)).perform(click())
            onView(withText(context.getString(R.string.dialog_end))).perform(click())
            val finishTrip = waitForRequest("/api/trips/101/finish")
            assertNotNull("Expected walk finishTrip request", finishTrip)
        }
    }

    @Test
    fun rideTracking_startAndFinish_callsTripApis() {
        launchActivity(rideIntent()).use {
            val createTrip = waitForRequest("/api/trips")
            assertNotNull("Expected ride createTrip request", createTrip)
            onView(withId(R.id.btn_end_trip)).perform(click())
            onView(withText(context.getString(R.string.dialog_end))).perform(click())
            val finishTrip = waitForRequest("/api/trips/101/finish")
            assertNotNull("Expected ride finishTrip request", finishTrip)
        }
    }

    private fun trackingDispatcher(): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path == "/api/trips" -> jsonResponse(
                        """
                        {
                          "id": 101,
                          "status": "active",
                          "trip_type": "walk",
                          "created_at": "2026-04-09T00:00:00Z",
                          "expected_arrive_at": "2026-04-09T00:30:00Z"
                        }
                        """.trimIndent()
                    )

                    request.path == "/api/v2/chat/proactive" -> jsonResponse(
                        """
                        {
                          "id": 1,
                          "role": "assistant",
                          "content": "Take care",
                          "trip_id": 101,
                          "message_type": "proactive",
                          "created_at": "2026-04-09T00:00:00Z"
                        }
                        """.trimIndent()
                    )

                    request.path == "/api/trips/101/finish" -> jsonResponse(
                        """
                        {
                          "status": "finished",
                          "finished_at": "2026-04-09T00:10:00Z"
                        }
                        """.trimIndent()
                    )

                    request.path?.startsWith("/api/trips/101/locations") == true ->
                        jsonResponse("""{"uploaded":1}""")

                    request.path?.startsWith("/api/trips/101/sos") == true ->
                        jsonResponse("""{"status":"sent","sos_id":1,"message":"SOS sent"}""")

                    request.path?.startsWith("/api/v2/trips/101/alerts") == true ->
                        jsonResponse(
                            """
                            {"trip_id":101,"alert_type":"walk_timeout","guardian_count":0,"proactive_message":"check-in"}
                            """.trimIndent()
                        )

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json")

    private fun waitForRequest(path: String, timeoutMs: Long = 8_000L): RecordedRequest? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val request = server.takeRequest(1, TimeUnit.SECONDS) ?: continue
            if (request.path == path) {
                return request
            }
        }
        return null
    }

    private fun walkIntent(): Intent =
        Intent(context, WalkTrackingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(WalkSettingActivity.EXTRA_START_LAT, 31.2304)
            putExtra(WalkSettingActivity.EXTRA_START_LNG, 121.4737)
            putExtra(WalkSettingActivity.EXTRA_START_NAME, "People's Square")
            putExtra(WalkSettingActivity.EXTRA_DEST_LAT, 31.2243)
            putExtra(WalkSettingActivity.EXTRA_DEST_LNG, 121.4768)
            putExtra(WalkSettingActivity.EXTRA_DEST_NAME, "Xintiandi")
            putExtra(WalkSettingActivity.EXTRA_ESTIMATED_MINUTES, 18)
            putExtra(WalkSettingActivity.EXTRA_TOTAL_DISTANCE, 1200)
            putExtra(WalkSettingActivity.EXTRA_GUARDIAN_SUMMARY, "Alice")
            putIntegerArrayListExtra(WalkSettingActivity.EXTRA_GUARDIAN_IDS, arrayListOf(1))
            putParcelableArrayListExtra(
                WalkSettingActivity.EXTRA_ROUTE_POINTS,
                arrayListOf(
                    LatLng(31.2304, 121.4737),
                    LatLng(31.2280, 121.4750),
                    LatLng(31.2243, 121.4768),
                ),
            )
        }

    private fun rideIntent(): Intent =
        Intent(context, RideTrackingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(RideSettingActivity.EXTRA_START_LAT, 31.2304)
            putExtra(RideSettingActivity.EXTRA_START_LNG, 121.4737)
            putExtra(RideSettingActivity.EXTRA_DEST_LAT, 31.2243)
            putExtra(RideSettingActivity.EXTRA_DEST_LNG, 121.4768)
            putExtra(RideSettingActivity.EXTRA_DEST_NAME, "Xintiandi")
            putExtra(RideSettingActivity.EXTRA_ESTIMATED_MINUTES, 15)
            putExtra(RideSettingActivity.EXTRA_TOTAL_DISTANCE, 3000)
            putExtra(RideSettingActivity.EXTRA_GUARDIAN_SUMMARY, "Alice")
            putIntegerArrayListExtra(RideSettingActivity.EXTRA_GUARDIAN_IDS, arrayListOf(1))
            putParcelableArrayListExtra(
                RideSettingActivity.EXTRA_ROUTE_POINTS,
                arrayListOf(
                    LatLng(31.2304, 121.4737),
                    LatLng(31.2280, 121.4750),
                    LatLng(31.2243, 121.4768),
                ),
            )
            putExtra(RideSettingActivity.EXTRA_PLATE_NUMBER, "沪A12345")
            putExtra(RideSettingActivity.EXTRA_VEHICLE_TYPE, "SUV")
            putExtra(RideSettingActivity.EXTRA_VEHICLE_COLOR, "Black")
            putExtra(RideSettingActivity.EXTRA_REMARK, "")
        }

    private fun launchActivity(intent: Intent): ActivityHandle {
        val monitor = instrumentation.addMonitor(intent.component?.className, null, false)
        context.startActivity(intent)
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_TIMEOUT_MS)
        requireNotNull(activity) { "Expected ${intent.component?.className} launch" }
        instrumentation.removeMonitor(monitor)
        return ActivityHandle(activity)
    }

    private class ActivityHandle(
        private val activity: Activity,
    ) : AutoCloseable {
        override fun close() {
            activity.finish()
        }
    }

    private fun grantRuntimePermissions() {
        val packageName = context.packageName
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant $packageName $permission"
            ).close()
        }
    }

    private companion object {
        const val ACTIVITY_TIMEOUT_MS = 8_000L
        const val USER_ID = 13
        const val NICKNAME = "Walker"
        const val TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDAwMDAwMDAsInN1YiI6IjEzIn0.signature"
    }
}
