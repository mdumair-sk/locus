package com.locus.core.ai.llama

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceFingerprintProvider {
    fun getDeviceFingerprint(): String
}

@Singleton
class DefaultDeviceFingerprintProvider
    @Inject
    constructor() : DeviceFingerprintProvider {
        override fun getDeviceFingerprint(): String {
            val modelName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            return modelName.ifBlank { Build.FINGERPRINT.ifBlank { "unknown-device" } }
        }
    }
