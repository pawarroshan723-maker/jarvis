package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanContainer
import com.example.ui.theme.BorderCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.VoiceLanguage

data class CommandHelpItem(
    val category: CommandCategory,
    val titleEn: String,
    val titleMr: String,
    val sampleEn: String,
    val sampleMr: String,
    val alternateEn: List<String> = emptyList(),
    val alternateMr: List<String> = emptyList(),
    val descriptionEn: String,
    val descriptionMr: String,
    val isHardwareDirect: Boolean = true
)

enum class CommandCategory(val labelEn: String, val labelMr: String) {
    ALL("All", "सर्व"),
    STATUS("Status & Core", "स्थिती व माहिती"),
    HARDWARE("Torch & Flash", "टॉर्च व फ्लॅश"),
    AUDIO("Audio & Ringer", "आवाज व सायलेंट"),
    DSP_NOISE("DSP & Noise Gate", "डीएसपी व नॉईज"),
    TELEPHONY("Call & SMS", "कॉल व मेसेज"),
    PLAYER("Media & Music", "गाणी व प्लेअर"),
    WAKELOCK("WakeLock & Mic", "वेक लॉक व माईक"),
    SETTINGS_APPS("Apps & Settings", "ॲप्स व सेटिंग्ज"),
    MATH_KNOWLEDGE("Math & Facts", "गणित व ज्ञान"),
    AUTOMATION("Sensor Rules", "ऑटोमेशन नियम")
}

val ALL_JARVIS_COMMANDS = listOf(
    // 1. Status & Core
    CommandHelpItem(
        category = CommandCategory.STATUS,
        titleEn = "System Telemetry & Status",
        titleMr = "सिस्टम स्थिती व रिपोर्ट",
        sampleEn = "system status",
        sampleMr = "सिस्टम स्थिती",
        alternateEn = listOf("status report", "jarvis status", "how are you"),
        alternateMr = listOf("स्टेटस रिपोर्ट", "कशी आहे सिस्टीम", "कसा आहेस"),
        descriptionEn = "Reports live battery level, charging state, ringer mode, volume, and service status.",
        descriptionMr = "बॅटरी टक्केवारी, चार्जिंग स्थिती, सायलेंट मोड आणि आवाज पातळी सांगते."
    ),
    CommandHelpItem(
        category = CommandCategory.STATUS,
        titleEn = "Time & Date Query",
        titleMr = "वेळ आणि आजची तारीख",
        sampleEn = "what time is it",
        sampleMr = "किती वाजले",
        alternateEn = listOf("what is the date", "time in india", "today's date"),
        alternateMr = listOf("वेळ सांगा", "आजची तारीख काय आहे", "आजचा वार"),
        descriptionEn = "Speaks exact local or Indian Standard Time (IST) and date in natural English or Marathi.",
        descriptionMr = "सध्याची अचूक वेळ आणि तारीख मराठी किंवा इंग्रजीत सांगते."
    ),
    CommandHelpItem(
        category = CommandCategory.STATUS,
        titleEn = "Jarvis Greetings",
        titleMr = "जार्व्हिस अभिवादन",
        sampleEn = "hello jarvis",
        sampleMr = "नमस्कार",
        alternateEn = listOf("jarvis", "wake up", "are you there"),
        alternateMr = listOf("हॅलो जार्व्हिस", "नमस्ते", "हाय जार्व्हिस"),
        descriptionEn = "Immediate wake response confirming Jarvis is listening and standing by.",
        descriptionMr = "जार्व्हिस सक्रिय असून आपल्या आज्ञेसाठी तयार असल्याचे उत्तर देते."
    ),

    // 2. Hardware & Flashlight
    CommandHelpItem(
        category = CommandCategory.HARDWARE,
        titleEn = "Turn On Flashlight",
        titleMr = "टॉर्च चालू करा",
        sampleEn = "turn on flashlight",
        sampleMr = "टॉर्च चालू करा",
        alternateEn = listOf("torch on", "lumos", "flashlight on"),
        alternateMr = listOf("टॉर्च लावा", "लाईट चालू करा", "फ्लॅश ऑन"),
        descriptionEn = "Illuminates phone camera LED flashlight immediately.",
        descriptionMr = "मोबाईलची कॅमेरा एलईडी टॉर्च त्वरित सुरू करते."
    ),
    CommandHelpItem(
        category = CommandCategory.HARDWARE,
        titleEn = "Turn Off Flashlight",
        titleMr = "टॉर्च बंद करा",
        sampleEn = "turn off flashlight",
        sampleMr = "टॉर्च बंद करा",
        alternateEn = listOf("torch off", "nox", "turn off the light"),
        alternateMr = listOf("टॉर्च विझवा", "लाईट बंद करा", "फ्लॅश ऑफ"),
        descriptionEn = "Deactivates camera LED flashlight.",
        descriptionMr = "कॅमेरा एलईडी टॉर्च बंद करते."
    ),
    CommandHelpItem(
        category = CommandCategory.HARDWARE,
        titleEn = "Flashlight Strobe",
        titleMr = "स्ट्रोब लाईट सुरू करा",
        sampleEn = "flashlight strobe",
        sampleMr = "स्ट्रोब लाइट",
        alternateEn = listOf("strobe light", "strobe on", "flash strobe"),
        alternateMr = listOf("स्ट्रोब सुरू करा", "फ्लॅश स्ट्रोब"),
        descriptionEn = "Rapidly pulses flashlight at high frequency for alerts or signaling.",
        descriptionMr = "इशारा देण्यासाठी टॉर्च वेगाने ब्लिंक करते."
    ),
    CommandHelpItem(
        category = CommandCategory.HARDWARE,
        titleEn = "Emergency SOS Flash",
        titleMr = "आपत्कालीन एसओएस फ्लॅश",
        sampleEn = "flash sos",
        sampleMr = "एसओएस लाईट",
        alternateEn = listOf("emergency sos", "sos light", "torch sos"),
        alternateMr = listOf("एसओएस अलर्ट", "आपत्कालीन लाईट"),
        descriptionEn = "Blinks camera torch in international SOS Morse code pattern (... --- ...).",
        descriptionMr = "आंतरराष्ट्रीय एसओएस मोर्स कोड पॅटर्ननुसार टॉर्च ब्लिंक करते."
    ),

    // 3. Audio & Volume
    CommandHelpItem(
        category = CommandCategory.AUDIO,
        titleEn = "Maximum Volume",
        titleMr = "फुल आवाज करा",
        sampleEn = "max volume",
        sampleMr = "फुल आवाज करा",
        alternateEn = listOf("full volume", "volume 100%", "maximum volume"),
        alternateMr = listOf("पूर्ण आवाज", "आवाज १०० टक्के करा", "जास्तीत जास्त आवाज"),
        descriptionEn = "Sets media and speech volume to 100% full capacity.",
        descriptionMr = "मोबाईलचा आवाज पूर्ण १०० टक्के वाढवते."
    ),
    CommandHelpItem(
        category = CommandCategory.AUDIO,
        titleEn = "Mute / Silent Mode",
        titleMr = "म्यूट व सायलेंट मोड",
        sampleEn = "mute",
        sampleMr = "म्यूट करा",
        alternateEn = listOf("silent mode", "silence", "mute phone"),
        alternateMr = listOf("सायलेंट मोड", "शांत करा", "आवाज बंद करा"),
        descriptionEn = "Mutes all media streams and switches ringer to silent.",
        descriptionMr = "सर्व आवाज म्यूट करून फोन सायलेंट मोडवर ठेवते."
    ),
    CommandHelpItem(
        category = CommandCategory.AUDIO,
        titleEn = "Vibration Mode",
        titleMr = "व्हायब्रेट मोड",
        sampleEn = "vibrate mode",
        sampleMr = "व्हायब्रेट मोड",
        alternateEn = listOf("set to vibrate", "vibration mode"),
        alternateMr = listOf("व्हायब्रेशन मोड", "फोन व्हायब्रेट करा"),
        descriptionEn = "Switches system ringer mode to vibration only.",
        descriptionMr = "फोन रिंगर फक्त व्हायब्रेशन मोडवर सेट करते."
    ),
    CommandHelpItem(
        category = CommandCategory.AUDIO,
        titleEn = "Volume Adjustments",
        titleMr = "आवाज वाढवा / कमी करा",
        sampleEn = "volume up",
        sampleMr = "आवाज वाढवा",
        alternateEn = listOf("volume down", "increase volume", "decrease volume"),
        alternateMr = listOf("आवाज कमी करा", "आवाज मोठा करा", "व्हॉल्युम बारीक करा"),
        descriptionEn = "Steps media volume up or down in 25% increments.",
        descriptionMr = "आवाज २५ टक्क्यांनी वाढवते किंवा कमी करते."
    ),

    // 4. DSP & Noise Gate
    CommandHelpItem(
        category = CommandCategory.DSP_NOISE,
        titleEn = "DSP Hardware vs Simulator Audit",
        titleMr = "डीएसपी हार्डवेअर तपासणी",
        sampleEn = "check dsp",
        sampleMr = "डीएसपी तपासा",
        alternateEn = listOf("dsp status", "is dsp hardware or simulator", "verify dsp"),
        alternateMr = listOf("डीएसपी हार्डवेअर आहे का", "डीएसपी स्थिती"),
        descriptionEn = "Audits Audio HAL effects, SoC chipset silicon, and checks if hardware noise suppression is active.",
        descriptionMr = "फोनमधील ऑडिओ इफेक्ट्स, हार्डवेअर चिपसेट आणि प्रत्यक्ष हार्डवेअर डीएसपी तपासते."
    ),
    CommandHelpItem(
        category = CommandCategory.DSP_NOISE,
        titleEn = "Set Noise Gate Threshold (dB)",
        titleMr = "नॉईज गेट थ्रेशोल्ड (dB)",
        sampleEn = "set noise gate 5 db",
        sampleMr = "नॉईज गेट ५ डीबी करा",
        alternateEn = listOf("noise gate 3 db", "noise sensitivity 4 db", "noise threshold 6 db"),
        alternateMr = listOf("नॉईज गेट ३ डीबी", "नॉईज सेन्सिटिव्हिटी ४ डीबी"),
        descriptionEn = "Manually sets the background noise cutoff gate in decibels (1.0 to 15.0 dB).",
        descriptionMr = "मायक्रोफोनचा नॉईज कटऑफ थ्रेशोल्ड मॅन्युअली १ ते १५ डीबी दरम्यान सेट करते."
    ),
    CommandHelpItem(
        category = CommandCategory.DSP_NOISE,
        titleEn = "Auto Noise Gate Mode",
        titleMr = "ऑटोमॅटिक नॉईज गेट मोड",
        sampleEn = "noise gate auto",
        sampleMr = "नॉईज गेट ऑटो करा",
        alternateEn = listOf("auto noise gate", "dynamic noise gate"),
        alternateMr = listOf("ऑटो नॉईज गेट", "डायनॅमिक नॉईज गेट"),
        descriptionEn = "Enables dynamic background tracking to automatically adapt cutoff to room noise.",
        descriptionMr = "खोलीतील आवाजानुसार आपोआप थ्रेशोल्ड जुळवून घेणारा ऑटो मोड सुरू करते."
    ),

    // 5. Telephony & SMS
    CommandHelpItem(
        category = CommandCategory.TELEPHONY,
        titleEn = "Make Direct Phone Call",
        titleMr = "थेट फोन कॉल लावा",
        sampleEn = "call 9876543210",
        sampleMr = "९८७६५४३२१० वर कॉल करा",
        alternateEn = listOf("call Mom", "dial 100", "make a call to John"),
        alternateMr = listOf("आईला फोन लावा", "कॉल लावा ९८७६५४३२१०"),
        descriptionEn = "Dials phone number or contact directly via telephony manager or dialer.",
        descriptionMr = "दिलेल्या नंबरवर किंवा संपर्कावर थेट फोन कॉल लावते."
    ),
    CommandHelpItem(
        category = CommandCategory.TELEPHONY,
        titleEn = "Send Direct SMS Message",
        titleMr = "थेट एसएमएस पाठवा",
        sampleEn = "send sms to 9876543210 I am reaching soon",
        sampleMr = "९८७६५४३२१० ला मेसेज पाठवा मी लवकरच पोहोचत आहे",
        alternateEn = listOf("message 9876543210 Hello sir", "text 9876543210 On my way"),
        alternateMr = listOf("एसएमएस पाठवा ९८७६५४३२१० नमस्कार सर"),
        descriptionEn = "Composes and transmits SMS message directly without needing internet.",
        descriptionMr = "इंटरनेटशिवाय थेट मोबाइलवरून एसएमएस संदेश पाठवते."
    ),

    // 6. Media & Music
    CommandHelpItem(
        category = CommandCategory.PLAYER,
        titleEn = "Play Songs & Music",
        titleMr = "गाणी व म्युझिक प्ले करा",
        sampleEn = "play song Believer",
        sampleMr = "गाणे लावा Believer",
        alternateEn = listOf("play on youtube Hanuman Chalisa", "play local song", "play music"),
        alternateMr = listOf("यूट्यूबवर गाणे लावा", "स्थानिक गाणे प्ले करा", "म्युझिक सुरू करा"),
        descriptionEn = "Launches YouTube, local audio player, VLC, or Spotify with chosen track.",
        descriptionMr = "यूट्यूब किंवा स्थानिक ऑडिओ प्लेअरमध्ये गाणे सुरू करते."
    ),
    CommandHelpItem(
        category = CommandCategory.PLAYER,
        titleEn = "Media Playback Controls",
        titleMr = "गाणे थांबवा / सुरू करा",
        sampleEn = "pause music",
        sampleMr = "गाणे थांबवा",
        alternateEn = listOf("resume music", "next song", "previous song", "stop music"),
        alternateMr = listOf("गाणे सुरू करा", "पुढचे गाणे", "मागचे गाणे", "म्युझिक बंद करा"),
        descriptionEn = "Controls background audio playback even when device screen is locked.",
        descriptionMr = "स्क्रीन लॉक असतानाही गाणे थांबवते, सुरू करते किंवा बदलते."
    ),

    // 7. WakeLock & Mic
    CommandHelpItem(
        category = CommandCategory.WAKELOCK,
        titleEn = "Persistent CPU WakeLock",
        titleMr = "वेक लॉक चालू / बंद",
        sampleEn = "wake lock on",
        sampleMr = "वेक लॉक चालू",
        alternateEn = listOf("enable wake lock", "keep awake", "wake lock off"),
        alternateMr = listOf("स्क्रीन चालू ठेवा", "वेक लॉक बंद"),
        descriptionEn = "Prevents CPU sleep so player and automations execute reliably on locked devices.",
        descriptionMr = "स्क्रीन लॉक असतानाही प्लेअर आणि ऑटोमेशन सुरळीत चालू ठेवण्यासाठी सीपीयू जागृत ठेवते."
    ),
    CommandHelpItem(
        category = CommandCategory.WAKELOCK,
        titleEn = "Always-On Mic Modes",
        titleMr = "नेहमी माईक चालू मोड्स",
        sampleEn = "always mic on",
        sampleMr = "नेहमी माईक चालू",
        alternateEn = listOf("lock only mic", "screen on mic only", "turn off always mic"),
        alternateMr = listOf("लॉक ओन्ली माईक", "स्क्रीन ऑन माईक", "माईक बंद करा"),
        descriptionEn = "Configures when continuous hands-free voice listening is active (Lock only / Screen on / Always).",
        descriptionMr = "मायक्रोफोन सतत कधी ऐकत राहील (नेहमी / फक्त लॉकवर / फक्त स्क्रीनवर) ते नियंत्रित करते."
    ),

    // 8. Apps & Settings
    CommandHelpItem(
        category = CommandCategory.SETTINGS_APPS,
        titleEn = "Open Android Settings",
        titleMr = "सिस्टीम सेटिंग्ज उघडा",
        sampleEn = "open wifi settings",
        sampleMr = "वायफाय सेटिंग उघडा",
        alternateEn = listOf("open bluetooth settings", "open display settings", "open sound settings", "open battery settings"),
        alternateMr = listOf("ब्लूटूथ सेटिंग", "डिस्प्ले सेटिंग", "आवाज सेटिंग", "बॅटरी सेटिंग"),
        descriptionEn = "Directly jumps to native Android system settings submenus.",
        descriptionMr = "अँड्रॉइड सिस्टीमची संबंधित सेटिंग त्वरित उघडते."
    ),
    CommandHelpItem(
        category = CommandCategory.SETTINGS_APPS,
        titleEn = "Launch Installed Applications",
        titleMr = "इन्स्टॉल केलेले ॲप्स उघडा",
        sampleEn = "open camera",
        sampleMr = "कॅमेरा उघडा",
        alternateEn = listOf("open calculator", "open whatsapp", "open youtube", "open gallery", "open clock"),
        alternateMr = listOf("कॅल्क्युलेटर", "व्हाट्सअ‍ॅप उघडा", "यूट्यूब उघडा", "गॅलरी", "घड्याळ"),
        descriptionEn = "Launches any installed Android app by voice name matching.",
        descriptionMr = "मोबाईलमधील कोणतेही ॲप आवाजी आज्ञेनुसार उघडते."
    ),

    // 9. Math & Facts
    CommandHelpItem(
        category = CommandCategory.MATH_KNOWLEDGE,
        titleEn = "Offline Math Calculations",
        titleMr = "ऑफलाइन गणित व आकडेमोड",
        sampleEn = "calculate 25 plus 75",
        sampleMr = "२५ अधिक ७५",
        alternateEn = listOf("what is 12 times 8", "sqrt 144", "calculate 150 divided by 3"),
        alternateMr = listOf("५ गुणिले ६", "१०० वजा ४०", "१४४ चे वर्गमूळ"),
        descriptionEn = "Evaluates arithmetic and scientific math expressions instantly offline.",
        descriptionMr = "गुणाकार, बेरीज, वजाबाकी, भागाकार आणि वर्गमूळ त्वरित सोडवते."
    ),
    CommandHelpItem(
        category = CommandCategory.MATH_KNOWLEDGE,
        titleEn = "Unit & Measurement Conversions",
        titleMr = "एकक व रूपांतरणे",
        sampleEn = "convert 5 km to meters",
        sampleMr = "५ किलोमीटरचे मीटर",
        alternateEn = listOf("10 kg in grams", "100 usd in inr", "30 celsius in fahrenheit"),
        alternateMr = listOf("१० किलोचे ग्रॅम", "३० सेल्सिअसचे फॅरेनहाइट"),
        descriptionEn = "Converts units of length, weight, temperature, and currency offline.",
        descriptionMr = "लांबी, वजन आणि तापमानाची एकके रूपांतरित करते."
    ),
    CommandHelpItem(
        category = CommandCategory.MATH_KNOWLEDGE,
        titleEn = "World Clock & Global Time",
        titleMr = "जागतिक घड्याळ व शहरांची वेळ",
        sampleEn = "time in London",
        sampleMr = "लंडन मधील वेळ",
        alternateEn = listOf("time in New York", "time in Tokyo", "time in Dubai", "time in Paris", "time in Singapore"),
        alternateMr = listOf("न्यू यॉर्क वेळ", "दुबई वेळ", "टोकियो वेळ", "पॅरिस मधील वेळ"),
        descriptionEn = "Calculates exact local time across world financial centers and capitals instantly offline.",
        descriptionMr = "लंडन, न्यू यॉर्क, दुबई, टोकियो अशा जागतिक शहरांची अचूक वेळ त्वरित सांगते."
    ),

    // 10. Sensor Automations
    CommandHelpItem(
        category = CommandCategory.AUTOMATION,
        titleEn = "Create Sensor Automation Rule",
        titleMr = "सेन्सर ऑटोमेशन नियम तयार करा",
        sampleEn = "when face down mute",
        sampleMr = "फोन पालथा ठेवल्यावर म्यूट करा",
        alternateEn = listOf("when shake toggle flash", "when dark turn on torch", "when charger plugged say charging"),
        alternateMr = listOf("फोन हलवल्यावर टॉर्च लावा", "अंधार झाल्यावर टॉर्च लावा", "चार्जिंग चालू झाल्यावर सांगा"),
        descriptionEn = "Creates offline autonomous hardware triggers linked to accelerometer, proximity, and light sensors.",
        descriptionMr = "सेन्सर हालचालींनुसार (हालवणे, पालथा ठेवणे, अंधार) आपोआप काम करणारे नियम बनवते."
    ),

    // 11. Gemini AI & Pro Cascades
    CommandHelpItem(
        category = CommandCategory.STATUS,
        titleEn = "Select Gemini Model / Pro Cascade",
        titleMr = "जेमिनी मॉडेल व प्रॉ कॅस्केड",
        sampleEn = "cascade pro",
        sampleMr = "कॅस्केड प्रॉ चालू करा",
        alternateEn = listOf("use gemini 3.1 pro", "cascade flash", "cascade lite", "switch model auto"),
        alternateMr = listOf("३.१ प्रॉ मॉडेल वापरा", "कॅस्केड फ्लॅश", "कॅस्केड लाईट"),
        descriptionEn = "Switches active AI intelligence tier between Gemini 3.1 Pro, Flash Latest, or high-throughput Flash-Lite.",
        descriptionMr = "सखोल विचार व कोडिंगसाठी जेमिनी ३.१ प्रॉ किंवा अतिवेगवान कॅस्केड लाईट निवडते."
    ),

    // 12. Voice Language & Hotlines
    CommandHelpItem(
        category = CommandCategory.TELEPHONY,
        titleEn = "Emergency Hotline Shortcuts",
        titleMr = "आपत्कालीन हेल्पलाइन कॉल्स",
        sampleEn = "call police",
        sampleMr = "पोलिसांना फोन करा",
        alternateEn = listOf("call ambulance", "call fire", "call 112", "call 100", "call 108"),
        alternateMr = listOf("अ‍ॅम्ब्युलन्स बोलवा", "दमकल बोलवा", "११२ वर कॉल करा"),
        descriptionEn = "Directly places emergency call to Police (100), Ambulance (108), or National Emergency (112).",
        descriptionMr = "पोलिस (१००), रुग्णवाहिका (१०८) किंवा आपत्कालीन (११२) नंबरवर थेट कॉल लावते."
    ),
    CommandHelpItem(
        category = CommandCategory.STATUS,
        titleEn = "Voice Language Switching",
        titleMr = "आवाजी भाषा बदला",
        sampleEn = "switch to Marathi",
        sampleMr = "मराठीत बोला",
        alternateEn = listOf("switch to English", "speak in Marathi", "speak in English"),
        alternateMr = listOf("इंग्रजीत बोला", "मराठी भाषा निवडा", "इंग्रजी भाषा निवडा"),
        descriptionEn = "Toggles conversational speech synthesis and recognition language dynamically.",
        descriptionMr = "जार्व्हिसची संवाद भाषा मराठी किंवा इंग्रजीमध्ये आवाजाने बदलते."
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandsHelpDialog(
    selectedLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    onDismiss: () -> Unit,
    onExecuteCommand: (String) -> Unit = {},
    onSpeakExample: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CommandCategory.ALL) }
    var selectedLangTab by remember { mutableStateOf(0) } // 0: All, 1: English, 2: Marathi
    var showHowToUse by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    var copiedCommandToast by remember { mutableStateOf<String?>(null) }

    val isMarathiInterface = selectedLanguage == VoiceLanguage.MARATHI

    val filteredCommands = remember(searchQuery, selectedCategory, selectedLangTab) {
        ALL_JARVIS_COMMANDS.filter { item ->
            val matchesCategory = selectedCategory == CommandCategory.ALL || item.category == selectedCategory
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                item.titleEn.lowercase().contains(q) ||
                item.titleMr.lowercase().contains(q) ||
                item.sampleEn.lowercase().contains(q) ||
                item.sampleMr.lowercase().contains(q) ||
                item.descriptionEn.lowercase().contains(q) ||
                item.descriptionMr.lowercase().contains(q) ||
                item.alternateEn.any { it.lowercase().contains(q) } ||
                item.alternateMr.any { it.lowercase().contains(q) }
            }
            matchesCategory && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BorderCyan, RoundedCornerShape(14.dp))
                    .testTag("voice_commands_help_dialog"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    // HEADER ROW
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ArcCyanContainer)
                                    .border(1.dp, ArcCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Commands",
                                    tint = ArcCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isMarathiInterface) "व्हॉईस कमांड सेंटर व मदत" else "VOICE COMMAND CENTER & HELP",
                                    color = ArcCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = if (isMarathiInterface) "१००% ऑफलाइन सुरक्षित डिव्हाइस कंट्रोल (मराठी व इंग्रजी)" else "100% Offline Real Device Control (English & Marathi)",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // "How to Use" Toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (showHowToUse) ArcCyanContainer else SurfaceVariantDark)
                                    .border(1.dp, if (showHowToUse) ArcCyan else BorderCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { showHowToUse = !showHowToUse }
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                    .testTag("how_to_use_guide_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                        contentDescription = "How to use",
                                        tint = if (showHowToUse) ArcCyan else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isMarathiInterface) "कसे वापरावे" else "HOW TO USE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showHowToUse) ArcCyan else TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantDark)
                                    .testTag("close_commands_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HOW TO USE GUIDE EXPANDABLE ACCORDION
                    AnimatedVisibility(visible = showHowToUse) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .border(1.dp, NeonAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = ObsidianDark)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isMarathiInterface) "📌 जार्व्हिस कसा वापरावा (HOW TO USE GUIDE):" else "📌 HOW TO USE JARVIS COMMANDS:",
                                    color = NeonAmber,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val guideSteps = if (isMarathiInterface) listOf(
                                    "१. हात न लावता बोलणे: 'Jarvis' म्हणा किंवा मध्यभागी असणाऱ्या 'Arc Reactor' माईकवर टॅप करा.",
                                    "२. २-सेकंद स्वयंचलित विराम: तुमचे बोलणे पूर्ण झाल्यावर २ सेकंद थांबा, जार्व्हिस आपोआप आज्ञा पूर्ण करेल.",
                                    "३. स्क्रीन लॉक असताना: वेक लॉक चालू ठेवा ('वेक लॉक चालू') — स्क्रीन बंद असतानाही गाणी, टॉर्च आणि कॉल्स चालतील.",
                                    "४. थेट चाचणी: खाली दिलेल्या कोणत्याही कमांड समोरील 'RUN' बटणावर टॅप करून ती त्वरित तपासून पहा!"
                                ) else listOf(
                                    "1. Hands-Free Voice: Say 'Jarvis' or tap the pulsing Arc Reactor Core in the center of the HUD.",
                                    "2. 2-Second Silence Auto-Trigger: Stop speaking for 2 seconds when done; Jarvis processes and executes immediately.",
                                    "3. On Locked Screen: Keep WakeLock active ('wake lock on') to control music, torch, and calls with display off.",
                                    "4. Instant One-Tap Test: Tap the 'RUN' button on any command card below to execute it directly without speaking!"
                                )
                                guideSteps.forEach { step ->
                                    Text(
                                        text = step,
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // SEARCH & FILTER BAR
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("command_search_input"),
                        placeholder = {
                            Text(
                                text = if (isMarathiInterface) "कमांड शोधा... (उदा. टॉर्च, आवाज, डीएसपी, call, music)" else "Search commands... (e.g. torch, dsp, volume, call, music)",
                                fontSize = 10.5.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = ArcCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = BorderCyan.copy(alpha = 0.5f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = ArcCyan
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // CATEGORY FILTER HORIZONTAL ROW
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(CommandCategory.values()) { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ArcCyanContainer else SurfaceVariantDark)
                                    .border(
                                        1.dp,
                                        if (isSelected) ArcCyan else BorderCyan.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("category_chip_${cat.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isMarathiInterface) cat.labelMr else cat.labelEn,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ArcCyan else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // RESULTS HEADER & COUNT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMarathiInterface) "उपलब्ध कमांड्स (${filteredCommands.size})" else "AVAILABLE COMMANDS (${filteredCommands.size})",
                            color = ArcCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isMarathiInterface) "मराठी व इंग्रजी दोन्ही समर्थित" else "TAP 'RUN' TO TEST DIRECTLY",
                            color = NeonGreen,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // COMMANDS LIST
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCommands) { item ->
                            CommandCard(
                                item = item,
                                isMarathi = isMarathiInterface,
                                onExecute = {
                                    val cmdToRun = if (isMarathiInterface) item.sampleMr else item.sampleEn
                                    onExecuteCommand(cmdToRun)
                                    onDismiss()
                                },
                                onCopy = { textToCopy ->
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    copiedCommandToast = textToCopy
                                }
                            )
                        }

                        if (filteredCommands.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 30.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (isMarathiInterface) "कोणतीही कमांड सापडली नाही" else "No matching command found",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isMarathiInterface) "वेगळा शब्द शोधून पहा" else "Try searching with a different keyword",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    item: CommandHelpItem,
    isMarathi: Boolean,
    onExecute: () -> Unit,
    onCopy: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianDark)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Title & Category Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMarathi) item.titleMr else item.titleEn,
                    color = ArcCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ArcCyanContainer)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMarathi) item.category.labelMr else item.category.labelEn,
                        fontSize = 7.5.sp,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isMarathi) item.descriptionMr else item.descriptionEn,
                color = TextSecondary,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Command Sample & Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EN: ",
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "\"${item.sampleEn}\"",
                            color = NeonGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MR: ",
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "\"${item.sampleMr}\"",
                            color = NeonAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy Button
                    IconButton(
                        onClick = { onCopy(if (isMarathi) item.sampleMr else item.sampleEn) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy command",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Execute Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ArcCyanContainer)
                            .border(1.dp, ArcCyan, RoundedCornerShape(6.dp))
                            .clickable { onExecute() }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run",
                                tint = ArcCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "RUN",
                                color = ArcCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Alternates
            if (item.alternateEn.isNotEmpty() || item.alternateMr.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val alternatesText = if (isMarathi) {
                    item.alternateMr.ifEmpty { item.alternateEn }.joinToString(" • ") { "\"$it\"" }
                } else {
                    item.alternateEn.ifEmpty { item.alternateMr }.joinToString(" • ") { "\"$it\"" }
                }
                Text(
                    text = "Also try: $alternatesText",
                    color = TextMuted,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
