package com.locus.core.ai.models

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.locus.core.ai.llama.DeviceFingerprintProvider
import com.locus.core.domain.models.DeviceCapabilitiesGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete Android implementation of [DeviceCapabilitiesGateway] using [ActivityManager] for RAM
 * headroom and [StatFs] for file system storage headroom.
 */
@Singleton
class AndroidDeviceCapabilitiesGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val deviceFingerprintProvider: DeviceFingerprintProvider,
    ) : DeviceCapabilitiesGateway {
        override fun getAvailableRamBytes(): Long {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            return memoryInfo.availMem
        }

        override fun getTotalRamBytes(): Long {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            return memoryInfo.totalMem
        }

        override fun getAvailableStorageBytes(): Long =
            try {
                val dir = context.filesDir
                val statFs = StatFs(dir.absolutePath)
                statFs.availableBytes
            } catch (_: Exception) {
                0L
            }

        override fun getDeviceFingerprint(): String = deviceFingerprintProvider.getDeviceFingerprint()
    }
