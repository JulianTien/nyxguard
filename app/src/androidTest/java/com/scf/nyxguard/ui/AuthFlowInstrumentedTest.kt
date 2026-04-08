package com.scf.nyxguard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scf.nyxguard.MainActivity
import com.scf.nyxguard.R
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.TokenManager
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AuthFlowInstrumentedTest {

    private lateinit var server: MockWebServer

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        ApiClient.init(context, server.url("/").toString())
        TokenManager.clearStoredLogin(context)
    }

    @After
    fun tearDown() {
        TokenManager.clearStoredLogin(context)
        server.shutdown()
    }

    @Test
    fun loginSuccess_persistsSessionAndLaunchesMain() {
        server.dispatcher = authDispatcher(expectedPath = "/api/auth/login")

        launchAndExpectMain(LoginActivity::class.java) {
            onView(withId(R.id.input_account)).perform(replaceText(PHONE))
            onView(withId(R.id.input_password)).perform(replaceText(PASSWORD), closeSoftKeyboard())
            onView(withId(R.id.btn_login)).perform(click())
        }

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected login request", request)
        assertEquals("/api/auth/login", request?.path)
        assertEquals(TOKEN, TokenManager.getToken(context))
        assertEquals(USER_ID, TokenManager.getUserId(context))
        assertEquals(NICKNAME, TokenManager.getNickname(context))
    }

    @Test
    fun registerSuccess_persistsSessionAndLaunchesMain() {
        server.dispatcher = authDispatcher(expectedPath = "/api/auth/register")

        launchAndExpectMain(RegisterActivity::class.java) {
            onView(withId(R.id.input_nickname)).perform(replaceText(NICKNAME))
            onView(withId(R.id.input_phone)).perform(replaceText(PHONE))
            onView(withId(R.id.input_password)).perform(replaceText(PASSWORD), closeSoftKeyboard())
            onView(withId(R.id.btn_register)).perform(click())
        }

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected register request", request)
        assertEquals("/api/auth/register", request?.path)
        assertEquals(TOKEN, TokenManager.getToken(context))
        assertEquals(USER_ID, TokenManager.getUserId(context))
        assertEquals(NICKNAME, TokenManager.getNickname(context))
    }

    @Test
    fun loginFailure_wrongPassword_staysOnLoginScreenAndShowsError() {
        server.dispatcher = authDispatcher(
            expectedPath = "/api/auth/login",
            responseCode = 401,
            errorDetail = LOGIN_ERROR,
        )

        launchAndExpectStayOnScreen(LoginActivity::class.java) {
            onView(withId(R.id.input_account)).perform(replaceText(PHONE))
            onView(withId(R.id.input_password)).perform(replaceText("wrong123"), closeSoftKeyboard())
            onView(withId(R.id.btn_login)).perform(click())
            onView(withId(R.id.btn_login)).check(matches(isEnabled()))
            onView(withId(R.id.input_account)).check(matches(isDisplayed()))
        }

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected login request", request)
        assertEquals("/api/auth/login", request?.path)
        assertNull(TokenManager.getToken(context))
    }

    @Test
    fun registerFailure_duplicatePhone_staysOnRegisterScreenAndShowsError() {
        server.dispatcher = authDispatcher(
            expectedPath = "/api/auth/register",
            responseCode = 409,
            errorDetail = REGISTER_DUPLICATE_ERROR,
        )

        launchAndExpectStayOnScreen(RegisterActivity::class.java) {
            onView(withId(R.id.input_nickname)).perform(replaceText(NICKNAME))
            onView(withId(R.id.input_phone)).perform(replaceText(PHONE))
            onView(withId(R.id.input_password)).perform(replaceText(PASSWORD), closeSoftKeyboard())
            onView(withId(R.id.btn_register)).perform(click())
            onView(withId(R.id.btn_register)).check(matches(isEnabled()))
            onView(withId(R.id.input_phone)).check(matches(isDisplayed()))
        }

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected register request", request)
        assertEquals("/api/auth/register", request?.path)
        assertNull(TokenManager.getToken(context))
    }

    private fun authDispatcher(
        expectedPath: String,
        responseCode: Int = 200,
        errorDetail: String? = null,
    ): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != expectedPath) {
                    return MockResponse().setResponseCode(404)
                }
                if (responseCode >= 400) {
                    return MockResponse()
                        .setResponseCode(responseCode)
                        .setBody("""{"detail":"$errorDetail"}""")
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "token": "$TOKEN",
                          "user": {
                            "id": $USER_ID,
                            "nickname": "$NICKNAME",
                            "phone": "$PHONE",
                            "email": null,
                            "avatar_url": null,
                            "emergency_phone": null,
                            "created_at": null,
                            "updated_at": null
                          }
                        }
                        """.trimIndent()
                    )
            }
        }
    }

    private fun launchAndExpectMain(
        activityClass: Class<out Activity>,
        interact: () -> Unit,
    ) {
        val mainMonitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, activityClass).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        interact()
        val launchedMain = instrumentation.waitForMonitorWithTimeout(mainMonitor, ACTIVITY_TIMEOUT_MS)
        assertNotNull("Expected MainActivity launch", launchedMain)
        launchedMain?.finish()
        instrumentation.removeMonitor(mainMonitor)
    }

    private fun launchAndExpectStayOnScreen(
        activityClass: Class<out Activity>,
        interact: () -> Unit,
    ) {
        val mainMonitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val activityMonitor = instrumentation.addMonitor(activityClass.name, null, false)
        context.startActivity(Intent(context, activityClass).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        val currentActivity = instrumentation.waitForMonitorWithTimeout(activityMonitor, ACTIVITY_TIMEOUT_MS)
        assertNotNull("Expected ${activityClass.simpleName} launch", currentActivity)
        interact()
        val launchedMain = instrumentation.waitForMonitorWithTimeout(mainMonitor, NO_MAIN_TIMEOUT_MS)
        assertNull("Did not expect MainActivity launch", launchedMain)
        currentActivity?.finish()
        instrumentation.removeMonitor(mainMonitor)
        instrumentation.removeMonitor(activityMonitor)
    }

    private companion object {
        const val ACTIVITY_TIMEOUT_MS = 7_000L
        const val NO_MAIN_TIMEOUT_MS = 1_500L
        const val USER_ID = 8
        const val NICKNAME = "NyxGuard"
        const val PHONE = "13812345678"
        const val PASSWORD = "abc123"
        const val LOGIN_ERROR = "账号或密码错误"
        const val REGISTER_DUPLICATE_ERROR = "该手机号已注册"
        const val TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDAwMDAwMDAsInN1YiI6IjgifQ.signature"
    }
}
