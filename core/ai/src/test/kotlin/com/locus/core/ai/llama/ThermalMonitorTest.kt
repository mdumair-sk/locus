package com.locus.core.ai.llama

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import com.locus.core.domain.chat.ThermalMonitor as DomainThermalMonitor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE)
class ThermalMonitorTest {
    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var shadowPowerManager: ShadowPowerManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowPowerManager = Shadows.shadowOf(powerManager)
        shadowPowerManager.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
    }

    @Test
    fun initialStatusIsFalse() {
        val monitor = ThermalMonitor(context, powerManager)
        assertFalse(monitor.isThrottlingLikely.value)
    }

    @Test
    fun startMonitoringWithDefaultStatusKeepsThrottlingFalse() {
        val monitor = ThermalMonitor(context, powerManager)
        monitor.startMonitoring()
        assertFalse(monitor.isThrottlingLikely.value)
        monitor.stopMonitoring()
    }

    @Test
    fun startMonitoringWithModerateInitialStatusSetsThrottlingTrue() {
        shadowPowerManager.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        val monitor = ThermalMonitor(context, powerManager)
        monitor.startMonitoring()
        assertTrue(monitor.isThrottlingLikely.value)
        monitor.stopMonitoring()
    }

    @Test
    fun dynamicThermalStatusChangeToModerateTriggersThrottlingLikelyViaPolling() =
        runTest {
            val monitor = ThermalMonitor(context, powerManager, coroutineScope = this)
            monitor.startMonitoring()
            assertFalse(monitor.isThrottlingLikely.value)

            shadowPowerManager.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
            advanceTimeBy(1001L)
            assertTrue(monitor.isThrottlingLikely.value)
            monitor.stopMonitoring()
        }

    @Test
    fun dynamicThermalStatusChangeToLightDoesNotTriggerThrottling() =
        runTest {
            val monitor = ThermalMonitor(context, powerManager, coroutineScope = this)
            monitor.startMonitoring()
            assertFalse(monitor.isThrottlingLikely.value)

            shadowPowerManager.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_LIGHT)
            advanceTimeBy(1001L)
            assertFalse(monitor.isThrottlingLikely.value)
            monitor.stopMonitoring()
        }

    @Test
    fun dismissWarningResetsThrottlingState() {
        shadowPowerManager.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        val monitor = ThermalMonitor(context, powerManager)
        monitor.startMonitoring()
        assertTrue(monitor.isThrottlingLikely.value)

        monitor.dismissWarning()
        assertFalse(monitor.isThrottlingLikely.value)
        monitor.stopMonitoring()
    }

    @Test
    fun stopMonitoringCancelsPolling() =
        runTest {
            val monitor = ThermalMonitor(context, powerManager, coroutineScope = this)
            monitor.startMonitoring()
            monitor.stopMonitoring()

            shadowPowerManager.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
            advanceTimeBy(2000L)
            assertFalse(monitor.isThrottlingLikely.value)
        }

    @Test
    fun llamaRuntimeHooksThermalMonitorInGenerateStream() =
        runTest {
            var startCalled = false
            var stopCalled = false

            val testThermalMonitor =
                object : DomainThermalMonitor {
                    override val isThrottlingLikely: StateFlow<Boolean> =
                        MutableStateFlow(false).asStateFlow()

                    override fun startMonitoring() {
                        startCalled = true
                    }

                    override fun stopMonitoring() {
                        stopCalled = true
                    }

                    override fun dismissWarning() {
                        // no-op in test
                    }
                }

            val fakeRuntime =
                object : LlamaRuntime(testThermalMonitor) {
                    // subclass for test
                }

            try {
                fakeRuntime.generateStream("test").toList()
            } catch (_: IllegalStateException) {
                // expected since model is not loaded
            }

            assertTrue("startMonitoring should be called", startCalled)
            assertTrue("stopMonitoring should be called in finally", stopCalled)
        }
}
