package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.JarvisDatabase
import com.example.data.JarvisRepository
import com.example.engine.OfflineIntentEngine
import com.example.hardware.HardwareController
import com.example.sensor.SensorTelemetry
import com.example.voice.JarvisSpeechManager
import com.example.voice.VoiceLanguage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineCoreCommandSetTest {

    private lateinit var context: Context
    private lateinit var database: JarvisDatabase
    private lateinit var repository: JarvisRepository
    private lateinit var hardware: HardwareController
    private lateinit var speechManager: JarvisSpeechManager
    private lateinit var offlineIntentEngine: OfflineIntentEngine
    private val telemetry = SensorTelemetry(batteryLevel = 85, isCharging = false)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JarvisRepository(database.jarvisDao())
        hardware = HardwareController(context)
        speechManager = JarvisSpeechManager(context) { /* test callback */ }
        offlineIntentEngine = OfflineIntentEngine(
            hardware = hardware,
            repository = repository,
            context = context,
            telephonyManager = null,
            speechManager = speechManager,
            autoCallSmsManager = null,
            scheduledTaskManager = null
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- 1. ENGLISH CORE COMMANDS ---

    @Test
    fun testEnglishStatusReportAndGreetings() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("jarvis", telemetry)
        assertTrue(r1.success)
        assertEquals("GREETING", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("system status", telemetry)
        assertTrue(r2.success)
        assertEquals("STATUS_REPORT", r2.intentAction)
        assertTrue(r2.spokenResponse.contains("Battery is at 85 percent"))

        val r3 = offlineIntentEngine.processCommand("help", telemetry)
        assertTrue(r3.success)
        assertEquals("HELP", r3.intentAction)
    }

    @Test
    fun testEnglishDspHardwareAudit() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("check dsp", telemetry)
        assertTrue(r1.success)
        assertEquals("DSP_AUDIT", r1.intentAction)
        assertTrue(r1.spokenResponse.contains("DSP"))

        val r2 = offlineIntentEngine.processCommand("is dsp hardware or simulator", telemetry)
        assertTrue(r2.success)
        assertEquals("DSP_AUDIT", r2.intentAction)
    }

    @Test
    fun testEnglishNoiseGateSensitivityControl() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("set noise gate 5 db", telemetry)
        assertTrue(r1.success)
        assertEquals("SET_NOISE_GATE_DB", r1.intentAction)
        assertEquals(5.0f, speechManager.noiseGateThresholdDb.value, 0.01f)
        assertTrue(speechManager.isManualNoiseGate.value)

        val r2 = offlineIntentEngine.processCommand("noise gate auto", telemetry)
        assertTrue(r2.success)
        assertEquals("SET_NOISE_GATE_AUTO", r2.intentAction)
        assertTrue(!speechManager.isManualNoiseGate.value)

        val r3 = offlineIntentEngine.processCommand("noise gate manual", telemetry)
        assertTrue(r3.success)
        assertEquals("SET_NOISE_GATE_MANUAL", r3.intentAction)
        assertTrue(speechManager.isManualNoiseGate.value)
    }

    @Test
    fun testEnglishTimeAndDate() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("what time is it", telemetry)
        assertTrue(r1.success)
        assertEquals("QUERY_TIME", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("what is the date", telemetry)
        assertTrue(r2.success)
        assertEquals("QUERY_DATE", r2.intentAction)
    }

    @Test
    fun testEnglishFlashlightControls() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("turn on flashlight", telemetry)
        assertEquals("TORCH_ON", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("turn off flashlight", telemetry)
        assertEquals("TORCH_OFF", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("flashlight strobe", telemetry)
        assertEquals("STROBE_LIGHT", r3.intentAction)
    }

    @Test
    fun testEnglishVolumeAndRingerControls() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("max volume", telemetry)
        assertTrue(r1.success)
        assertEquals("MAX_VOLUME", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("mute", telemetry)
        assertTrue(r2.success)
        assertEquals("MUTE_AUDIO", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("silent mode", telemetry)
        assertTrue(r3.success)
        assertEquals("MUTE_AUDIO", r3.intentAction)

        val r4 = offlineIntentEngine.processCommand("vibrate mode", telemetry)
        assertTrue(r4.success)
        assertEquals("RINGER_VIBRATE", r4.intentAction)
    }

    @Test
    fun testEnglishMathCalculations() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("calculate 25 plus 75", telemetry)
        assertTrue(r1.success)
        assertEquals("OFFLINE_KNOWLEDGE", r1.intentAction)
        assertTrue(r1.spokenResponse.contains("100"))

        val r2 = offlineIntentEngine.processCommand("what is 12 times 8", telemetry)
        assertTrue(r2.success)
        assertEquals("OFFLINE_KNOWLEDGE", r2.intentAction)
        assertTrue(r2.spokenResponse.contains("96"))
    }

    @Test
    fun testEnglishWakeLockControls() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("wake lock on", telemetry)
        assertTrue(r1.success)
        assertEquals("WAKELOCK_ON", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("wake lock off", telemetry)
        assertTrue(r2.success)
        assertEquals("WAKELOCK_OFF", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("wake screen", telemetry)
        assertTrue(r3.success)
        assertEquals("WAKE_SCREEN", r3.intentAction)
    }

    @Test
    fun testMediaCommandsAndHandsFreeMode() = runBlocking {
        // Next song / Change song
        val r1 = offlineIntentEngine.processCommand("change song", telemetry)
        assertTrue(r1.success)
        assertEquals("MEDIA_NEXT", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("next song", telemetry)
        assertTrue(r2.success)
        assertEquals("MEDIA_NEXT", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("गाणे बदला", telemetry)
        assertTrue(r3.success)
        assertEquals("MEDIA_NEXT", r3.intentAction)

        // Hands-Free Ducking Mode vs Music Priority Mode
        val r4 = offlineIntentEngine.processCommand("hands free music on", telemetry)
        assertTrue(r4.success)
        assertEquals("MEDIA_HANDS_FREE_ON", r4.intentAction)
        assertEquals(com.example.voice.MediaHandsFreeMode.AUDIO_DUCKING_HANDS_FREE, speechManager.mediaHandsFreeMode.value)

        val r5 = offlineIntentEngine.processCommand("music priority mode", telemetry)
        assertTrue(r5.success)
        assertEquals("MEDIA_PRIORITY_ON", r5.intentAction)
        assertEquals(com.example.voice.MediaHandsFreeMode.MUSIC_PRIORITY_SMART_PAUSE, speechManager.mediaHandsFreeMode.value)
    }

    // --- 2. MARATHI CORE COMMANDS ---

    @Test
    fun testMarathiGreetingsAndStatusReport() = runBlocking {
        speechManager.setLanguage(VoiceLanguage.MARATHI)

        val r1 = offlineIntentEngine.processCommand("नमस्कार", telemetry)
        assertTrue(r1.success)
        assertEquals("GREETING", r1.intentAction)
        assertTrue(r1.spokenResponse.isNotBlank())

        val r2 = offlineIntentEngine.processCommand("सिस्टम स्थिती", telemetry)
        assertTrue(r2.success)
        assertEquals("STATUS_REPORT", r2.intentAction)
        assertTrue(r2.spokenResponse.contains("बॅटरी"))

        val r3 = offlineIntentEngine.processCommand("मदत", telemetry)
        assertTrue(r3.success)
        assertEquals("HELP", r3.intentAction)
    }

    @Test
    fun testMarathiDspHardwareAudit() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("डीएसपी तपासा", telemetry)
        assertTrue(r1.success)
        assertEquals("DSP_AUDIT", r1.intentAction)
        assertTrue(r1.spokenResponse.contains("डीएसपी") || r1.spokenResponse.contains("DSP"))
    }

    @Test
    fun testMarathiNoiseGateSensitivityControl() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("नॉईज गेट ५ डीबी करा", telemetry)
        assertTrue(r1.success)
        assertEquals("SET_NOISE_GATE_DB", r1.intentAction)
        assertEquals(5.0f, speechManager.noiseGateThresholdDb.value, 0.01f)

        val r2 = offlineIntentEngine.processCommand("नॉईज गेट ऑटो करा", telemetry)
        assertTrue(r2.success)
        assertEquals("SET_NOISE_GATE_AUTO", r2.intentAction)
        assertTrue(!speechManager.isManualNoiseGate.value)

        val r3 = offlineIntentEngine.processCommand("नॉईज गेट मॅन्युअल करा", telemetry)
        assertTrue(r3.success)
        assertEquals("SET_NOISE_GATE_MANUAL", r3.intentAction)
        assertTrue(speechManager.isManualNoiseGate.value)
    }

    @Test
    fun testMarathiTimeAndDate() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("किती वाजले", telemetry)
        assertTrue(r1.success)
        assertEquals("QUERY_TIME", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("आजची तारीख काय आहे", telemetry)
        assertTrue(r2.success)
        assertEquals("QUERY_DATE", r2.intentAction)
    }

    @Test
    fun testMarathiFlashlightControls() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("टॉर्च चालू करा", telemetry)
        assertEquals("TORCH_ON", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("टॉर्च बंद करा", telemetry)
        assertEquals("TORCH_OFF", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("स्ट्रोब लाइट", telemetry)
        assertEquals("STROBE_LIGHT", r3.intentAction)
    }

    @Test
    fun testMarathiVolumeAndRingerControls() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("फुल आवाज करा", telemetry)
        assertTrue(r1.success)
        assertEquals("MAX_VOLUME", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("म्यूट करा", telemetry)
        assertTrue(r2.success)
        assertEquals("MUTE_AUDIO", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("सायलेंट मोड", telemetry)
        assertTrue(r3.success)
        assertEquals("MUTE_AUDIO", r3.intentAction)

        val r4 = offlineIntentEngine.processCommand("व्हायब्रेट मोड", telemetry)
        assertTrue(r4.success)
        assertEquals("RINGER_VIBRATE", r4.intentAction)
    }

    @Test
    fun testMarathiMathCalculations() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("२५ अधिक ७५", telemetry)
        assertTrue(r1.success)
        assertEquals("OFFLINE_KNOWLEDGE", r1.intentAction)
        assertTrue(r1.spokenResponse.contains("100") || r1.spokenResponse.contains("१००") || r1.spokenResponse.contains("शंभर"))

        val r2 = offlineIntentEngine.processCommand("५ गुणिले ६", telemetry)
        assertTrue(r2.success)
        assertEquals("OFFLINE_KNOWLEDGE", r2.intentAction)
        assertTrue(r2.spokenResponse.contains("30") || r2.spokenResponse.contains("३०") || r2.spokenResponse.contains("तीस"))
    }

    @Test
    fun testMarathiWakeLockControls() = runBlocking {
        val r1 = offlineIntentEngine.processCommand("वेक लॉक चालू", telemetry)
        assertTrue(r1.success)
        assertEquals("WAKELOCK_ON", r1.intentAction)

        val r2 = offlineIntentEngine.processCommand("वेक लॉक बंद", telemetry)
        assertTrue(r2.success)
        assertEquals("WAKELOCK_OFF", r2.intentAction)

        val r3 = offlineIntentEngine.processCommand("स्क्रीन चालू करा", telemetry)
        assertTrue(r3.success)
        assertEquals("WAKE_SCREEN", r3.intentAction)
    }
}
