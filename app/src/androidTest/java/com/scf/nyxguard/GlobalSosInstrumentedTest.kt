package com.scf.nyxguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.TokenManager
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GlobalSosInstrumentedTest {

    private lateinit var server: MockWebServer

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        server.dispatcher = sosDispatcher()
        ApiClient.init(context, server.url("/").toString())
        TokenManager.saveLogin(context, TOKEN, USER_ID, NICKNAME)
    }

    @After
    fun tearDown() {
        TokenManager.clearStoredLogin(context)
        server.shutdown()
    }

    @Test
    fun cancelCountdown_doesNotTriggerSosRequest() {
        launchMainActivity().use {
            onView(withId(R.id.fab_global_sos)).perform(click())
            onView(withId(R.id.countdown_text)).check(matches(isDisplayed()))
            onView(withId(R.id.cancel_button)).perform(click())
            assertFalse(findRequest("/api/v2/sos", 6_000))
        }
    }

    @Test
    fun confirmCountdown_triggersGlobalSosRequest() {
        launchMainActivity().use {
            onView(withId(R.id.fab_global_sos)).perform(click())
            onView(withId(R.id.countdown_text)).check(matches(isDisplayed()))

            val request = waitForRequest("/api/v2/sos", 8_000)
            assertNotNull("Expected /api/v2/sos request", request)
            assertEquals("/api/v2/sos", request?.path)
        }
    }

    private fun sosDispatcher(): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/v2/dashboard" -> jsonResponse(
                        """
                        {
                          "nickname":"$NICKNAME",
                          "greeting":"Good evening",
                          "guardian_count":0,
                          "active_trip_brief":null,
                          "quick_tools_state":{}
                        }
                        """.trimIndent()
                    )

                    "/api/v2/sos" -> jsonResponse(
                        """
                        {
                          "status":"recorded",
                          "sos_id":101,
                          "linked_trip_id":null,
                          "guardian_count":0,
                          "message":"SOS求助已记录",
                          "media_key":null,
                          "audio_url":"file://placeholder"
                        }
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

    private fun waitForRequest(path: String, timeoutMs: Long): RecordedRequest? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val request = server.takeRequest(1, TimeUnit.SECONDS) ?: continue
            if (request.path == path) {
                return request
            }
        }
        return null
    }

    private fun findRequest(path: String, timeoutMs: Long): Boolean =
        waitForRequest(path, timeoutMs) != null

    private fun launchMainActivity(): ActivityHandle {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_TIMEOUT_MS)
        requireNotNull(activity) { "Expected MainActivity launch" }
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

    private companion object {
        const val ACTIVITY_TIMEOUT_MS = 8_000L
        const val USER_ID = 12
        const val NICKNAME = "NyxGuard"
        const val TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDAwMDAwMDAsInN1YiI6IjEyIn0.signature"
    }
}
