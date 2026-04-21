package com.scf.nyxguard

import com.scf.nyxguard.common.ActiveTripSnapshot
import com.scf.nyxguard.common.ActiveTripStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActiveTripStore.clear(context)
    }

    @Test
    fun activeTripStore_persistsAndClearsSnapshot() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        ActiveTripStore.save(
            appContext,
            ActiveTripSnapshot(
                isActive = true,
                tripId = 42,
                mode = "walk",
                destination = "Home",
                etaMinutes = 12,
                guardianSummary = "妈妈"
            )
        )

        val restored = ActiveTripStore.get(appContext)
        assertEquals(42, restored?.tripId)
        assertEquals("walk", restored?.mode)
        assertEquals("Home", restored?.destination)

        ActiveTripStore.clear(appContext)
        assertNull(ActiveTripStore.get(appContext))
    }
}
