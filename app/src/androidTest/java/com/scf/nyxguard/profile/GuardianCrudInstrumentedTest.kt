package com.scf.nyxguard.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scf.nyxguard.R
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.TokenManager
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GuardianCrudInstrumentedTest {

    private lateinit var server: MockWebServer
    private val guardians = mutableListOf<GuardianPayload>()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        server.dispatcher = guardianDispatcher()
        ApiClient.init(context, server.url("/").toString())
        TokenManager.saveLogin(context, TOKEN, USER_ID, NICKNAME)
    }

    @After
    fun tearDown() {
        TokenManager.clearStoredLogin(context)
        server.shutdown()
        guardians.clear()
    }

    @Test
    fun addAndDeleteGuardian_updatesListAndEmptyState() {
        launchGuardianActivity().use { activity ->
            onView(withId(R.id.empty_text)).check(matches(isDisplayed()))

            onView(withId(R.id.fab_add)).perform(click())
            onView(withId(R.id.input_name)).perform(replaceText(GUARDIAN_NAME))
            onView(withId(R.id.input_phone)).perform(replaceText(GUARDIAN_PHONE))
            onView(withText(context.getString(R.string.guardian_add_action))).perform(click())

            val addRequest = server.takeRequest(5, TimeUnit.SECONDS)
            val refreshRequest = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(addRequest)
            assertNotNull(refreshRequest)
            assertEquals("/api/guardians", addRequest?.path)
            assertEquals("/api/guardians", refreshRequest?.path)
            onView(withText(GUARDIAN_NAME)).check(matches(isDisplayed()))

            onView(withId(R.id.btn_delete)).perform(click())
            onView(withText(context.getString(R.string.guardian_delete_action))).perform(click())

            val deleteRequest = server.takeRequest(5, TimeUnit.SECONDS)
            val deleteRefresh = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(deleteRequest)
            assertNotNull(deleteRefresh)
            assertEquals("/api/guardians/1", deleteRequest?.path)
            assertEquals("/api/guardians", deleteRefresh?.path)
            onView(withId(R.id.empty_text)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun fiveGuardians_disableAddButton() {
        guardians.clear()
        repeat(5) { index ->
            guardians += GuardianPayload(
                id = index + 1,
                nickname = "Guardian$index",
                phone = "1380000000$index",
                relationship = "朋友",
            )
        }

        launchGuardianActivity().use {
            onView(withId(R.id.fab_add)).check(matches(not(androidx.test.espresso.matcher.ViewMatchers.isEnabled())))
        }
    }

    private fun guardianDispatcher(): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.method == "GET" && request.path == "/api/guardians" ->
                        jsonResponse(guardiansJson())

                    request.method == "POST" && request.path == "/api/guardians" -> {
                        val nextId = (guardians.maxOfOrNull { it.id } ?: 0) + 1
                        guardians += GuardianPayload(nextId, GUARDIAN_NAME, GUARDIAN_PHONE, "朋友")
                        jsonResponse(
                            """
                            {"id":$nextId,"nickname":"$GUARDIAN_NAME","phone":"$GUARDIAN_PHONE","relationship":"朋友"}
                            """.trimIndent()
                        )
                    }

                    request.method == "DELETE" && request.path == "/api/guardians/1" -> {
                        guardians.removeAll { it.id == 1 }
                        jsonResponse("""{"message":"deleted"}""")
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun guardiansJson(): String =
        guardians.joinToString(prefix = "[", postfix = "]") { guardian ->
            """
            {"id":${guardian.id},"nickname":"${guardian.nickname}","phone":"${guardian.phone}","relationship":"${guardian.relationship}"}
            """.trimIndent()
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json")

    private fun launchGuardianActivity(): ActivityHandle {
        val monitor = instrumentation.addMonitor(GuardianActivity::class.java.name, null, false)
        context.startActivity(Intent(context, GuardianActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_TIMEOUT_MS)
        requireNotNull(activity) { "Expected GuardianActivity launch" }
        instrumentation.removeMonitor(monitor)
        server.takeRequest(5, TimeUnit.SECONDS) // initial GET /api/guardians
        return ActivityHandle(activity)
    }

    private data class GuardianPayload(
        val id: Int,
        val nickname: String,
        val phone: String,
        val relationship: String,
    )

    private class ActivityHandle(
        private val activity: Activity,
    ) : AutoCloseable {
        override fun close() {
            activity.finish()
        }
    }

    private companion object {
        const val ACTIVITY_TIMEOUT_MS = 7_000L
        const val USER_ID = 11
        const val NICKNAME = "GuardianOwner"
        const val GUARDIAN_NAME = "Alice"
        const val GUARDIAN_PHONE = "13812345678"
        const val TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDAwMDAwMDAsInN1YiI6IjExIn0.signature"
    }
}
