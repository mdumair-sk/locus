package com.locus.core.domain.models

/**
 * Gateway providing device runtime resource headroom (RAM, storage) and hardware fingerprint for
 * model candidate filtering and benchmark ranking policy (M-9).
 */
interface DeviceCapabilitiesGateway {
    /**
     * Available RAM headroom in bytes (e.g. from ActivityManager.MemoryInfo.availMem). If
     * unconstrained or unknown, returns 0.
     */
    fun getAvailableRamBytes(): Long

    /** Total device physical RAM in bytes (e.g. from ActivityManager.MemoryInfo.totalMem). */
    fun getTotalRamBytes(): Long

    /**
     * Available storage headroom in bytes for model downloads and temporary files (e.g. from
     * StatFs.availableBytes). If unconstrained or unknown, returns 0.
     */
    fun getAvailableStorageBytes(): Long

    /** Stable device fingerprint/identifier used to match benchmarked tok/s entries in ModelMeta. */
    fun getDeviceFingerprint(): String
}
