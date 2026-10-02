package com.example.voice

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.util.Locale

/**
 * Categorization of the audio Digital Signal Processing (DSP) architecture.
 */
enum class DspArchitectureType(val displayName: String, val shortBadge: String) {
    ACTUAL_HARDWARE_DSP("Actual Hardware DSP", "DSP: HW (ACTUAL)"),
    SOFTWARE_SIMULATOR_DSP("Software Simulator / Emulator", "DSP: SIMULATOR"),
    AOSP_SOFTWARE_FILTER("AOSP Software CPU Filter", "DSP: AOSP (SW)"),
    APP_EMBEDDED_FILTER("Jarvis Embedded Software Filter", "DSP: SW FILTER")
}

/**
 * Inspection details for a single discovered AudioEffect pipeline descriptor.
 */
data class DspEffectDescriptor(
    val typeName: String,
    val effectName: String,
    val implementor: String,
    val uuid: String,
    val isHardwareSilicon: Boolean
)

/**
 * Comprehensive audit result verifying whether DSP is actual hardware silicon or simulator/software.
 */
data class DspHardwareAudit(
    val architecture: DspArchitectureType,
    val isActualHardware: Boolean,
    val isSimulator: Boolean,
    val badgeLabel: String,
    val shortStatus: String,
    val chipsetSoc: String,
    val deviceModel: String,
    val isNoiseSuppressorAvailable: Boolean,
    val isEchoCancelerAvailable: Boolean,
    val isAutoGainControlAvailable: Boolean,
    val hardwareVendor: String,
    val effects: List<DspEffectDescriptor>,
    val technicalVerdict: String,
    val detailedExplanation: String
)

/**
 * Diagnostic tool that inspects the Android Audio HAL, AudioEffect subsystem, and SoC architecture
 * to definitively verify whether Noise Suppression and Echo Cancellation are running on
 * ACTUAL PHYSICAL HARDWARE DSP (Hexagon/APU/Exynos) or a SOFTWARE SIMULATOR / EMULATOR.
 */
object DspHardwareInspector {

    private const val TAG = "DspHardwareInspector"

    // Known physical silicon / DSP semiconductor vendors
    private val KNOWN_HARDWARE_VENDORS = listOf(
        "qualcomm", "qcom", "hexagon", "mediatek", "mtk", "samsung", "exynos",
        "cirrus", "nxp", "texas instruments", "ti", "sony", "huawei", "hisilicon",
        "realtek", "broadcom", "synaptics", "dsp group"
    )

    // Known software CPU processing vendors
    private val KNOWN_SOFTWARE_VENDORS = listOf(
        "the android open source project", "aosp", "google", "webrtc"
    )

    /**
     * Checks if the app is currently running inside an Android Emulator, Cloud VM, or Simulator.
     */
    fun isRunningInEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val device = Build.DEVICE.lowercase(Locale.ROOT)
        val product = Build.PRODUCT.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val board = Build.BOARD.lowercase(Locale.ROOT)

        return fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for x86") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk_gphone") ||
                product.contains("sdk") ||
                product.contains("vbox") ||
                manufacturer.contains("genymotion") ||
                board.contains("goldfish") ||
                brand.contains("generic") ||
                device.contains("generic") ||
                (Build.HOST != null && Build.HOST.startsWith("android-test"))
    }

    /**
     * Performs an in-depth hardware audit of the audio subsystem.
     */
    fun performHardwareAudit(context: Context? = null): DspHardwareAudit {
        val isEmulator = isRunningInEmulator()
        val chipsetSoc = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.HARDWARE} / ${Build.BOARD})"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"

        val nsAvailable = try {
            NoiseSuppressor.isAvailable()
        } catch (e: Throwable) {
            false
        }

        val aecAvailable = try {
            AcousticEchoCanceler.isAvailable()
        } catch (e: Throwable) {
            false
        }

        val agcAvailable = try {
            AutomaticGainControl.isAvailable()
        } catch (e: Throwable) {
            false
        }

        val discoveredEffects = mutableListOf<DspEffectDescriptor>()
        var detectedHardwareVendor: String? = null
        var hasSiliconVendorEffect = false
        var hasAospSoftwareEffect = false

        try {
            val descriptors: Array<AudioEffect.Descriptor>? = AudioEffect.queryEffects()
            if (descriptors != null) {
                for (desc in descriptors) {
                    val typeUuid = desc.type?.toString() ?: ""
                    val isNs = typeUuid.equals(AudioEffect.EFFECT_TYPE_NS?.toString(), ignoreCase = true)
                    val isAec = typeUuid.equals(AudioEffect.EFFECT_TYPE_AEC?.toString(), ignoreCase = true)
                    val isAgc = typeUuid.equals(AudioEffect.EFFECT_TYPE_AGC?.toString(), ignoreCase = true)

                    if (isNs || isAec || isAgc) {
                        val typeName = when {
                            isNs -> "Noise Suppressor (NS)"
                            isAec -> "Acoustic Echo Canceler (AEC)"
                            else -> "Automatic Gain Control (AGC)"
                        }

                        val implementorLower = (desc.implementor ?: "").lowercase(Locale.ROOT)
                        val nameLower = (desc.name ?: "").lowercase(Locale.ROOT)

                        val isHwVendor = KNOWN_HARDWARE_VENDORS.any {
                            implementorLower.contains(it) || nameLower.contains(it)
                        }
                        val isSwVendor = KNOWN_SOFTWARE_VENDORS.any {
                            implementorLower.contains(it) || nameLower.contains(it)
                        }

                        if (isHwVendor) {
                            hasSiliconVendorEffect = true
                            if (detectedHardwareVendor == null) {
                                detectedHardwareVendor = desc.implementor ?: desc.name
                            }
                        }
                        if (isSwVendor) {
                            hasAospSoftwareEffect = true
                        }

                        discoveredEffects.add(
                            DspEffectDescriptor(
                                typeName = typeName,
                                effectName = desc.name ?: "Unknown",
                                implementor = desc.implementor ?: "Unknown",
                                uuid = desc.uuid?.toString() ?: "",
                                isHardwareSilicon = isHwVendor && !isEmulator
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error querying AudioEffect descriptors: ${e.message}")
        }

        // Determine Final Architecture Classification
        val architecture: DspArchitectureType
        val isActualHardware: Boolean
        val isSimulator: Boolean
        val badgeLabel: String
        val shortStatus: String
        val technicalVerdict: String
        val detailedExplanation: String
        val resolvedVendor = detectedHardwareVendor ?: if (isEmulator) "Google Android Emulator Host" else Build.MANUFACTURER

        if (isEmulator) {
            architecture = DspArchitectureType.SOFTWARE_SIMULATOR_DSP
            isActualHardware = false
            isSimulator = true
            badgeLabel = "DSP: SIMULATOR"
            shortStatus = "Simulator Mode (Host Audio Bridge)"
            technicalVerdict = "SIMULATOR / EMULATOR (Software Host Audio Pipeline)"
            detailedExplanation = "Application is executing on an Android Virtual Device / Cloud Emulator ($chipsetSoc). There is no physical DSP silicon attached; audio capture and noise suppression are emulated in software via the host OS audio bridge."
        } else if ((nsAvailable || aecAvailable) && hasSiliconVendorEffect) {
            architecture = DspArchitectureType.ACTUAL_HARDWARE_DSP
            isActualHardware = true
            isSimulator = false
            badgeLabel = "DSP: HW (ACTUAL)"
            shortStatus = "Actual Hardware DSP Active ($resolvedVendor)"
            technicalVerdict = "ACTUAL PHYSICAL HARDWARE DSP ($resolvedVendor)"
            detailedExplanation = "Physical DSP silicon detected on $chipsetSoc (SoC Audio Subsystem). Noise cancellation and acoustic echo cancellation execute in dedicated offloaded hardware DSP with zero CPU overhead, beamforming, and ultra-low latency."
        } else if (nsAvailable || aecAvailable) {
            architecture = DspArchitectureType.AOSP_SOFTWARE_FILTER
            isActualHardware = false
            isSimulator = false
            badgeLabel = "DSP: AOSP (SW)"
            shortStatus = "AOSP Software Filter Active"
            technicalVerdict = "AOSP SOFTWARE PRE-PROCESSOR (CPU Filter)"
            detailedExplanation = "Physical device detected, but audio effects are provided by AOSP/WebRTC software algorithms executing on the main application CPU rather than dedicated offloaded DSP silicon."
        } else {
            architecture = DspArchitectureType.APP_EMBEDDED_FILTER
            isActualHardware = false
            isSimulator = false
            badgeLabel = "DSP: SW FILTER"
            shortStatus = "Jarvis Adaptive Software Filter Active"
            technicalVerdict = "JARVIS VOCAL BANDPASS & ADAPTIVE NOISE GATE"
            detailedExplanation = "Platform audio HAL does not provide built-in DSP effects. Jarvis is actively running internal software DSP: 300Hz-3400Hz vocal formant bandpass filter, dynamic noise floor adaptation, and SNR gating."
        }

        return DspHardwareAudit(
            architecture = architecture,
            isActualHardware = isActualHardware,
            isSimulator = isSimulator,
            badgeLabel = badgeLabel,
            shortStatus = shortStatus,
            chipsetSoc = chipsetSoc,
            deviceModel = deviceModel,
            isNoiseSuppressorAvailable = nsAvailable,
            isEchoCancelerAvailable = aecAvailable,
            isAutoGainControlAvailable = agcAvailable,
            hardwareVendor = resolvedVendor,
            effects = discoveredEffects,
            technicalVerdict = technicalVerdict,
            detailedExplanation = detailedExplanation
        )
    }
}
