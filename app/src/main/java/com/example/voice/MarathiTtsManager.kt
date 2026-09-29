package com.example.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * MarathiTtsManager:
 * Resolves all Text-To-Speech (TTS) engine, locale, and phonetic challenges for Marathi & bilingual speech:
 *
 * 1. Native Marathi TTS (mr-IN):
 *    Directly selects installed Google / System Marathi voices with native phonetics.
 *
 * 2. Indic Devanagari Fallback (hi-IN):
 *    If the device lacks the specific 'mr-IN' voice pack, uses 'hi-IN' (which shares the exact same
 *    Devanagari script and phonemes), avoiding the foreign "different country TTS problem".
 *
 * 3. Phonetic Romanized Transliteration:
 *    If an international device or emulator has ZERO Devanagari voices installed, transliterates
 *    Devanagari script to clean phonetic Romanized Marathi. English TTS engines can pronounce it
 *    intelligibly without erroring, skipping, or announcing foreign country TTS errors.
 *
 * 4. Indian English (en-IN) Preference:
 *    Avoids hardcoding Locale.UK (which sounds like a foreign country and struggles with Indian names),
 *    preferring en-IN or the user's system locale.
 *
 * 5. Direct One-Tap Voice Download Intent:
 *    Provides intent helpers to launch Android TTS settings and install the official Google Marathi voice pack.
 */
object MarathiTtsManager {

    private const val TAG = "MarathiTtsManager"

    val LOCALE_MARATHI: Locale = Locale.forLanguageTag("mr-IN")
    val LOCALE_HINDI: Locale = Locale.forLanguageTag("hi-IN")
    val LOCALE_ENGLISH_INDIA: Locale = Locale.forLanguageTag("en-IN")

    data class TtsDiagnosticInfo(
        val enginePackage: String,
        val engineName: String,
        val isMarathiSupported: Boolean,
        val isHindiSupported: Boolean,
        val isIndianEnglishSupported: Boolean,
        val currentVoiceName: String,
        val statusDescription: String,
        val needsVoiceDownload: Boolean
    )

    enum class VoiceMode {
        NATIVE_MARATHI,
        HINDI_DEVANAGARI,
        INDIAN_ENGLISH,
        PHONETIC_TRANSLITERATED,
        SYSTEM_DEFAULT
    }

    data class PreparedSpeech(
        val textToSpeak: String,
        val voiceMode: VoiceMode,
        val localeUsed: Locale
    )

    fun isDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    fun isMarathiPhrase(text: String): Boolean {
        if (isDevanagari(text)) return true
        val lower = text.lowercase(Locale.ROOT)
        val marathiMarkers = listOf(
            "chalu kara", "chalu kar", "band kara", "band kar", "aawaj", "awaz",
            "kiti ahe", "kiti aahe", "kiti jhali", "vajle", "tarikh", "sanga",
            "namaskar", "dhanyawad", "shubh sakal", "shubh ratri", "ughada",
            "pathva", "lava", "set kara", "vadva", "vadhva", "kami kara",
            "motha kara", "barik kara", "tu kon", "tuze naav", "kasa ahes",
            "kashi ahes", "kay chalalay", "prakash", "ujed", "dishe", "ulta"
        )
        return marathiMarkers.any { lower.contains(it) }
    }

    /**
     * Inspects available voices and configures the TTS engine for optimal pronunciation.
     * Pure English text (like incoming English SMS or English responses) is always spoken
     * with an English TTS voice (en-IN/en-US) to avoid distorted accents, even when Marathi
     * is the default assistant language.
     */
    fun prepareAndConfigureTts(
        tts: TextToSpeech,
        rawText: String,
        selectedLanguage: VoiceLanguage
    ): PreparedSpeech {
        val hasDevanagari = isDevanagari(rawText)
        val hasMarathiMarker = isMarathiPhrase(rawText)

        // Text should use Marathi Devanagari TTS engine ONLY IF it contains actual Devanagari script
        // or explicit Marathi phrases. Pure Latin/English text must be spoken with an English voice
        // to prevent distorted/accented reading.
        val shouldSpeakInMarathiVoice = hasDevanagari || (hasMarathiMarker && selectedLanguage == VoiceLanguage.MARATHI)

        // Clean and normalize text for speech (converting times like 12:30 PM to 'twelve thirty PM'
        // and removing colons so TTS never says 'colon' or reads digits one-by-one)
        val normalizedText = normalizeTextForSpeech(rawText, shouldSpeakInMarathiVoice)

        if (shouldSpeakInMarathiVoice) {
            // Step 1: Check Native Marathi (mr-IN)
            val mrAvail = try {
                tts.isLanguageAvailable(LOCALE_MARATHI)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }

            if (mrAvail >= TextToSpeech.LANG_AVAILABLE) {
                try {
                    tts.setLanguage(LOCALE_MARATHI)
                    // If API >= 21, try to find an installed native Marathi voice
                    selectBestVoiceForLocale(tts, LOCALE_MARATHI)
                    tts.setPitch(0.98f)
                    tts.setSpeechRate(0.95f) // Slightly relaxed pace for pristine clarity
                    return PreparedSpeech(normalizedText, VoiceMode.NATIVE_MARATHI, LOCALE_MARATHI)
                } catch (e: Exception) {
                    Log.w(TAG, "Error configuring native Marathi voice", e)
                }
            }

            // Step 2: Check Hindi (hi-IN) - shares identical Devanagari alphabet & phonetic roots
            val hiAvail = try {
                tts.isLanguageAvailable(LOCALE_HINDI)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }

            if (hiAvail >= TextToSpeech.LANG_AVAILABLE) {
                try {
                    tts.setLanguage(LOCALE_HINDI)
                    selectBestVoiceForLocale(tts, LOCALE_HINDI)
                    tts.setPitch(0.95f)
                    tts.setSpeechRate(0.95f)
                    return PreparedSpeech(normalizedText, VoiceMode.HINDI_DEVANAGARI, LOCALE_HINDI)
                } catch (e: Exception) {
                    Log.w(TAG, "Error configuring Hindi Devanagari fallback", e)
                }
            }

            // Step 3: Neither Marathi nor Hindi TTS voice installed on the device!
            // Transliterate Devanagari into natural phonetic Romanized Marathi for English voice playback.
            val transliterated = if (hasDevanagari) transliterateToPhoneticRoman(normalizedText) else normalizedText

            val enInAvail = try {
                tts.isLanguageAvailable(LOCALE_ENGLISH_INDIA)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }

            val fallbackLocale = if (enInAvail >= TextToSpeech.LANG_AVAILABLE) {
                LOCALE_ENGLISH_INDIA
            } else {
                Locale.Builder().setLanguage("en").setRegion("IN").build()
            }

            try {
                tts.setLanguage(fallbackLocale)
                selectBestVoiceForLocale(tts, fallbackLocale)
                tts.setPitch(0.95f)
                tts.setSpeechRate(0.95f)
            } catch (e: Exception) {
                Log.w(TAG, "Error setting fallback English locale", e)
            }

            return PreparedSpeech(transliterated, VoiceMode.PHONETIC_TRANSLITERATED, fallbackLocale)
        } else {
            // General English speech - explicitly set to Indian English (en-IN) or US English
            val enInAvail = try {
                tts.isLanguageAvailable(LOCALE_ENGLISH_INDIA)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }

            val targetLocale = if (enInAvail >= TextToSpeech.LANG_AVAILABLE) {
                LOCALE_ENGLISH_INDIA
            } else {
                Locale.US
            }

            try {
                tts.setLanguage(targetLocale)
                selectBestVoiceForLocale(tts, targetLocale)
                tts.setPitch(0.95f)
                tts.setSpeechRate(0.98f)
            } catch (e: Exception) {
                Log.w(TAG, "Error setting English locale", e)
            }

            return PreparedSpeech(normalizedText, VoiceMode.INDIAN_ENGLISH, targetLocale)
        }
    }

    fun numberToEnglishWords(n: Int): String {
        if (n < 0) return "minus " + numberToEnglishWords(-n)
        if (n == 0) return "zero"
        val units = arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
        )
        val tens = arrayOf(
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
        )
        return when {
            n < 20 -> units[n]
            n < 100 -> {
                val rem = n % 10
                if (rem == 0) tens[n / 10] else "${tens[n / 10]} ${units[rem]}"
            }
            n < 1000 -> {
                val rem = n % 100
                val remStr = if (rem == 0) "" else " " + numberToEnglishWords(rem)
                "${units[n / 100]} hundred$remStr"
            }
            else -> n.toString()
        }
    }

    fun numberToMarathiWords(n: Int): String {
        val mrNumbers = mapOf(
            0 to "शून्य", 1 to "एक", 2 to "दोन", 3 to "तीन", 4 to "चार", 5 to "पाच",
            6 to "सहा", 7 to "सात", 8 to "आठ", 9 to "नऊ", 10 to "दहा",
            11 to "अकरा", 12 to "बारा", 13 to "तेरा", 14 to "चौदा", 15 to "पंधरा",
            16 to "सोळा", 17 to "सतरा", 18 to "अठरा", 19 to "एकोणीस", 20 to "वीस",
            21 to "एकवीस", 22 to "बावीस", 23 to "तेवीस", 24 to "चोवीस", 25 to "पंचवीस",
            26 to "सव्वीस", 27 to "सत्तावीस", 28 to "अठ्ठावीस", 29 to "एकोणतीस", 30 to "तीस",
            31 to "एकतीस", 32 to "बत्तीस", 33 to "तेहतीस", 34 to "चौतीस", 35 to "पस्तीस",
            36 to "छत्तीस", 37 to "सदतीस", 38 to "अडतीस", 39 to "एकेचाळीस", 40 to "चाळीस",
            41 to "एक्केचाळीस", 42 to "बेचाळीस", 43 to "त्रेचाळीस", 44 to "चव्वेचाळीस", 45 to "पंचेचाळीस",
            46 to "शेहेचाळीस", 47 to "सत्तेचाळीस", 48 to "अठ्ठेचाळीस", 49 to "एकोणपन्नास", 50 to "पन्नास",
            51 to "एक्कावन्न", 52 to "बावन्न", 53 to "त्रेपन्न", 54 to "चोपन्न", 55 to "पंचावन्न",
            56 to "छप्पन्न", 57 to "सत्तावन्न", 58 to "अठ्ठावन्न", 59 to "एकोणसाठ", 60 to "साठ"
        )
        return mrNumbers[n] ?: n.toString()
    }

    /**
     * Normalizes text for speech so that times (e.g. 12:30 PM), dates, symbols, and percentages
     * are spoken naturally in fluent words instead of robotic digit-by-digit reading or 'colon' announcements.
     */
    fun normalizeTextForSpeech(text: String, isMarathi: Boolean): String {
        var processed = text

        // 1. Strip markdown formatting symbols that confuse TTS
        processed = processed.replace(Regex("""[*_~#>`]"""), " ")

        // 2. Convert timestamps: e.g. "12:30 PM", "12:30:45 PM", "1:05 AM", "12:30", "०१:३०"
        val timeRegex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM|am|pm)?""")
        processed = timeRegex.replace(processed) { match ->
            val rawHour = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[4].uppercase(Locale.ROOT)

            if (isMarathi) {
                val hour12 = if (rawHour > 12) rawHour - 12 else if (rawHour == 0) 12 else rawHour
                val hourWord = numberToMarathiWords(hour12)
                val period = when {
                    amPm == "AM" -> if (rawHour < 12) "सकाळी" else "रात्री"
                    amPm == "PM" -> if (rawHour < 4 || rawHour == 12) "दुपारी" else if (rawHour < 8) "संध्याकाळी" else "रात्री"
                    else -> ""
                }
                val spokenTime = if (minute == 0) {
                    "$period $hourWord वाजता".trim()
                } else {
                    "$period $hourWord वाजून ${numberToMarathiWords(minute)} मिनिटे".trim()
                }
                spokenTime
            } else {
                val hour12 = if (rawHour > 12) rawHour - 12 else if (rawHour == 0) 12 else rawHour
                val hourWord = numberToEnglishWords(hour12)
                val minuteWord = when {
                    minute == 0 -> if (amPm.isNotEmpty()) "" else "o'clock"
                    minute in 1..9 -> "oh " + numberToEnglishWords(minute)
                    else -> numberToEnglishWords(minute)
                }
                val spokenTime = if (minuteWord.isEmpty()) {
                    "$hourWord $amPm".trim()
                } else {
                    "$hourWord $minuteWord $amPm".trim()
                }
                spokenTime
            }
        }

        // 3. Percentages: 100% -> 100 percent / १०० टक्के
        processed = processed.replace(Regex("""(\d+)\s*%""")) { match ->
            val num = match.groupValues[1]
            if (isMarathi) "$num टक्के" else "$num percent"
        }

        // 4. Replace remaining standalone colons with commas so TTS never speaks the word "colon"
        processed = processed.replace(Regex("""(?<=\w):"""), ",")
        processed = processed.replace(Regex("""\s+:\s+"""), ", ")

        // 5. Replace symbols with natural speech words
        processed = processed.replace("&", if (isMarathi) " आणि " else " and ")
        processed = processed.replace("+", if (isMarathi) " अधिक " else " plus ")

        // 6. Clean up multiple whitespaces
        processed = processed.replace(Regex("""\s+"""), " ").trim()

        return processed
    }

    private fun selectBestVoiceForLocale(tts: TextToSpeech, targetLocale: Locale) {
        try {
            val voices: Set<Voice>? = tts.voices
            if (!voices.isNullOrEmpty()) {
                val targetLang = targetLocale.language
                val targetCountry = targetLocale.country

                // Search priority:
                // 1. Language AND Country match + local offline voice (e.g., en-IN or mr-IN)
                // 2. Language AND Country match + any voice
                // 3. Name/locale string contains target tag (e.g. "en_in", "en-in", "india")
                // 4. Language match
                val match = voices.find { voice ->
                    val vLoc = voice.locale
                    vLoc.language.equals(targetLang, ignoreCase = true) &&
                            vLoc.country.equals(targetCountry, ignoreCase = true) &&
                            !voice.isNetworkConnectionRequired
                } ?: voices.find { voice ->
                    val vLoc = voice.locale
                    vLoc.language.equals(targetLang, ignoreCase = true) &&
                            vLoc.country.equals(targetCountry, ignoreCase = true)
                } ?: voices.find { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    val vLoc = voice.locale.toString().lowercase(Locale.ROOT)
                    val tag = "${targetLang.lowercase()}_${targetCountry.lowercase()}"
                    val tagHyphen = "${targetLang.lowercase()}-${targetCountry.lowercase()}"
                    (name.contains(tag) || name.contains(tagHyphen) || vLoc.contains(tag) || vLoc.contains(tagHyphen) || name.contains("india")) &&
                            !voice.isNetworkConnectionRequired
                } ?: voices.find { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    val vLoc = voice.locale.toString().lowercase(Locale.ROOT)
                    val tag = "${targetLang.lowercase()}_${targetCountry.lowercase()}"
                    val tagHyphen = "${targetLang.lowercase()}-${targetCountry.lowercase()}"
                    name.contains(tag) || name.contains(tagHyphen) || vLoc.contains(tag) || vLoc.contains(tagHyphen) || name.contains("india")
                } ?: voices.find { voice ->
                    voice.locale.language.equals(targetLang, ignoreCase = true)
                }

                if (match != null) {
                    tts.voice = match
                    Log.d(TAG, "Selected TTS Voice: ${match.name} (${match.locale}) for target $targetLocale")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Voice selection fallback: ${e.message}")
        }
    }

    /**
     * Transliterates Devanagari text into natural phonetic Romanized Marathi.
     * This allows any standard English TTS voice to speak Marathi words cleanly
     * when the device has not installed Google's Marathi voice pack.
     */
    fun transliterateToPhoneticRoman(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]

            // Check known word substitutions for better flow
            when {
                ch == '०' -> sb.append('0')
                ch == '१' -> sb.append('1')
                ch == '२' -> sb.append('2')
                ch == '३' -> sb.append('3')
                ch == '४' -> sb.append('4')
                ch == '५' -> sb.append('5')
                ch == '६' -> sb.append('6')
                ch == '७' -> sb.append('7')
                ch == '८' -> sb.append('8')
                ch == '९' -> sb.append('9')

                // Vowels
                ch == 'अ' -> sb.append("a")
                ch == 'आ' -> sb.append("aa")
                ch == 'इ' -> sb.append("i")
                ch == 'ई' -> sb.append("ee")
                ch == 'उ' -> sb.append("u")
                ch == 'ऊ' -> sb.append("oo")
                ch == 'ऋ' -> sb.append("ru")
                ch == 'ए' -> sb.append("e")
                ch == 'ऐ' -> sb.append("ai")
                ch == 'ओ' -> sb.append("o")
                ch == 'औ' -> sb.append("au")
                ch == 'ऑ' -> sb.append("o")
                ch == 'ॲ' -> sb.append("a")

                // Consonants
                ch in '\u0915'..'\u0939' || ch == 'ळ' -> {
                    val base = consonantToPhonetic(ch)
                    // Check next character for Matra or Virama
                    val nextChar = if (i + 1 < len) text[i + 1] else null
                    when (nextChar) {
                        '्' -> { // Virama: suppress inherent vowel
                            sb.append(base)
                            i++ // skip virama
                        }
                        'ा' -> { sb.append(base).append("aa"); i++ }
                        'ि' -> { sb.append(base).append("i"); i++ }
                        'ी' -> { sb.append(base).append("ee"); i++ }
                        'ु' -> { sb.append(base).append("u"); i++ }
                        'ू' -> { sb.append(base).append("oo"); i++ }
                        'े' -> { sb.append(base).append("e"); i++ }
                        'ै' -> { sb.append(base).append("ai"); i++ }
                        'ो' -> { sb.append(base).append("o"); i++ }
                        'ौ' -> { sb.append(base).append("au"); i++ }
                        'ं' -> { sb.append(base).append("an"); i++ }
                        'ॅ' -> { sb.append(base).append("e"); i++ }
                        'ॉ' -> { sb.append(base).append("o"); i++ }
                        'ृ' -> { sb.append(base).append("ru"); i++ }
                        else -> {
                            // Inherent vowel 'a' unless at the very end of word
                            val isWordEnd = (i + 1 >= len) || text[i + 1].isWhitespace() || text[i + 1] in ",.!?:"
                            if (isWordEnd) {
                                sb.append(base)
                            } else {
                                sb.append(base).append("a")
                            }
                        }
                    }
                }
                ch == 'ं' -> sb.append("n")
                ch == 'ः' -> sb.append("h")
                ch == 'ऽ' -> sb.append("'")
                else -> sb.append(ch)
            }
            i++
        }

        // Post-process known pronunciation cleanups
        var result = sb.toString()
        result = result.replace("aani", "aani")
            .replace("jany", "dnya")
            .replace("Jjaarvhisa", "Jarvis")
            .replace("jaarvhisa", "Jarvis")
            .replace("taorcha", "Torch")
            .replace("flaaysha", "Flash")
            .replace("siddha", "sajj")
        return result
    }

    private fun consonantToPhonetic(ch: Char): String {
        return when (ch) {
            'क' -> "k"
            'ख' -> "kh"
            'ग' -> "g"
            'घ' -> "gh"
            'ङ' -> "ng"
            'च' -> "ch"
            'छ' -> "chh"
            'ज' -> "j"
            'झ' -> "jh"
            'ञ' -> "ny"
            'ट' -> "t"
            'ठ' -> "th"
            'ड' -> "d"
            'ढ' -> "dh"
            'ण' -> "n"
            'त' -> "t"
            'थ' -> "th"
            'द' -> "d"
            'ध' -> "dh"
            'न' -> "n"
            'प' -> "p"
            'फ' -> "ph"
            'ब' -> "b"
            'भ' -> "bh"
            'म' -> "m"
            'य' -> "y"
            'र' -> "r"
            'ल' -> "l"
            'व' -> "v"
            'श' -> "sh"
            'ष' -> "sh"
            'स' -> "s"
            'ह' -> "h"
            'ळ' -> "l"
            else -> ch.toString()
        }
    }

    /**
     * Launches Android Text-To-Speech settings or voice data installer.
     */
    fun openTtsSettings(context: Context) {
        try {
            val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(installIntent)
            return
        } catch (e: Exception) {
            Log.d(TAG, "ACTION_INSTALL_TTS_DATA not available: ${e.message}")
        }

        try {
            val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS")
            ttsSettingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(ttsSettingsIntent)
            return
        } catch (e: Exception) {
            Log.d(TAG, "com.android.settings.TTS_SETTINGS not available: ${e.message}")
        }

        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open settings", e)
        }
    }

    /**
     * Analyzes the device's TTS configuration and returns a diagnostic overview.
     */
    fun getDiagnostics(tts: TextToSpeech?): TtsDiagnosticInfo {
        if (tts == null) {
            return TtsDiagnosticInfo(
                enginePackage = "None",
                engineName = "Not Initialized",
                isMarathiSupported = false,
                isHindiSupported = false,
                isIndianEnglishSupported = false,
                currentVoiceName = "N/A",
                statusDescription = "TTS Engine offline",
                needsVoiceDownload = true
            )
        }

        val enginePkg = tts.defaultEngine ?: "Default System Engine"
        val engineFriendlyName = when {
            enginePkg.contains("com.google.android.tts") -> "Google Speech Services (Recommended)"
            enginePkg.contains("samsung") -> "Samsung Text-to-Speech"
            else -> enginePkg
        }

        val mrAvail = try {
            tts.isLanguageAvailable(LOCALE_MARATHI) >= TextToSpeech.LANG_AVAILABLE
        } catch (e: Exception) { false }

        val hiAvail = try {
            tts.isLanguageAvailable(LOCALE_HINDI) >= TextToSpeech.LANG_AVAILABLE
        } catch (e: Exception) { false }

        val enInAvail = try {
            tts.isLanguageAvailable(LOCALE_ENGLISH_INDIA) >= TextToSpeech.LANG_AVAILABLE
        } catch (e: Exception) { false }

        val voiceName = try {
            tts.voice?.name ?: tts.voice?.locale?.displayName ?: "Default"
        } catch (e: Exception) { "Standard" }

        val status = when {
            mrAvail -> "मराठी आवाज उपलब्ध आणि सज्ज (Native mr-IN Ready)"
            hiAvail -> "हिंदी देवनागरी व्हॉईस सज्ज (Devanagari Phonetic Ready)"
            else -> "मराठी व्हॉईस पॅक डाउनलोड आवश्यक (Tap to install)"
        }

        return TtsDiagnosticInfo(
            enginePackage = enginePkg,
            engineName = engineFriendlyName,
            isMarathiSupported = mrAvail,
            isHindiSupported = hiAvail,
            isIndianEnglishSupported = enInAvail,
            currentVoiceName = voiceName,
            statusDescription = status,
            needsVoiceDownload = !mrAvail && !hiAvail
        )
    }
}
