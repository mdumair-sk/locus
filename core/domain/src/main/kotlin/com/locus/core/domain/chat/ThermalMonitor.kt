package com.locus.core.domain.chat

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for device thermal status monitoring during sustained local model generation (M-7).
 */
interface ThermalMonitor {
    /** Whether thermal throttling is likely (status reaches THERMAL_STATUS_MODERATE or higher). */
    val isThrottlingLikely: StateFlow<Boolean>

    /** Begins active monitoring of device thermal status during sustained generation. */
    fun startMonitoring()

    /** Halts active monitoring and releases any listeners/resources. */
    fun stopMonitoring()

    /** Dismisses/resets the current throttling warning flag. */
    fun dismissWarning()
}
