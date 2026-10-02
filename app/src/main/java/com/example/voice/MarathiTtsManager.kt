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
     * Enforces Jarvis "Women Voice Only" requirement and applies user speech rate & pitch.
     */
    fun prepareAndConfigureTts(
        tts: TextToSpeech,
        rawText: String,
        selectedLanguage: VoiceLanguage,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.20f
    ): PreparedSpeech {
        val hasDevanagari = isDevanagari(rawText)
        val hasMarathiMarker = isMarathiPhrase(rawText)

        // Pure English detection: Contains latin characters, and contains ZERO devanagari and NO marathi markers.
        // For incoming English SMS (e.g. "Message received from John: Where are you?"), system notifications,
        // or English responses, speaking with Marathi TTS results in a bizarre distorted Russian-sounding accent.
        // We MUST route pure English text to the English TTS engine!
        val isPureEnglishText = rawText.any { it in 'a'..'z' || it in 'A'..'Z' } && !hasDevanagari && !hasMarathiMarker

        val shouldSpeakInMarathiVoice = when {
            hasDevanagari || hasMarathiMarker -> true
            isPureEnglishText -> false
            selectedLanguage == VoiceLanguage.MARATHI -> true
            else -> false
        }

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
                    // If API >= 21, lock native Marathi female voice
                    selectBestVoiceForLocale(tts, LOCALE_MARATHI)
                    tts.setPitch(speechPitch)
                    tts.setSpeechRate(speechRate)
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
                    tts.setPitch(speechPitch)
                    tts.setSpeechRate(speechRate)
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
                tts.setPitch(speechPitch)
                tts.setSpeechRate(speechRate)
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
                tts.setPitch(speechPitch)
                tts.setSpeechRate(speechRate)
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

    val MARATHI_0_TO_100 = mapOf(
        0 to "शून्य", 1 to "एक", 2 to "दोन", 3 to "तीन", 4 to "चार", 5 to "पाच",
        6 to "सहा", 7 to "सात", 8 to "आठ", 9 to "नऊ", 10 to "दहा",
        11 to "अकरा", 12 to "बारा", 13 to "तेरा", 14 to "चौदा", 15 to "पंधरा",
        16 to "सोळा", 17 to "सतरा", 18 to "अठरा", 19 to "एकोणीस", 20 to "वीस",
        21 to "एकवीस", 22 to "बावीस", 23 to "तेवीस", 24 to "चोवीस", 25 to "पंचवीस",
        26 to "सव्वीस", 27 to "सत्तावीस", 28 to "अठ्ठावीस", 29 to "एकोणतीस", 30 to "तीस",
        31 to "एकतीस", 32 to "बत्तीस", 33 to "तेहतीस", 34 to "चौतीस", 35 to "पस्तीस",
        36 to "छत्तीस", 37 to "सदतीस", 38 to "अडतीस", 39 to "एकोणचाळीस", 40 to "चाळीस",
        41 to "एक्केचाळीस", 42 to "बेचाळीस", 43 to "त्रेचाळीस", 44 to "चव्वेचाळीस", 45 to "पंचेचाळीस",
        46 to "शेहेचाळीस", 47 to "सत्तेचाळीस", 48 to "अठ्ठेचाळीस", 49 to "एकोणपन्नास", 50 to "पन्नास",
        51 to "एक्कावन्न", 52 to "बावन्न", 53 to "त्रेपन्न", 54 to "चोपन्न", 55 to "पंचावन्न",
        56 to "छप्पन्न", 57 to "सत्तावन्न", 58 to "अठ्ठावन्न", 59 to "एकोणसाठ", 60 to "साठ",
        61 to "एकसष्ठ", 62 to "बासष्ठ", 63 to "त्रेसष्ठ", 64 to "चौसष्ठ", 65 to "पासष्ठ",
        66 to "सहासष्ठ", 67 to "सदसष्ठ", 68 to "अडसष्ठ", 69 to "एकोनसत्तर", 70 to "सत्तर",
        71 to "एकाहत्तर", 72 to "बहात्तर", 73 to "त्र्याहत्तर", 74 to "चौर्‍याहत्तर", 75 to "पंचाहत्तर",
        76 to "शहात्तर", 77 to "सत्याहत्तर", 78 to "अठ्ठ्याहत्तर", 79 to "एकोणऐंशी", 80 to "ऐंशी",
        81 to "एक्याऐंशी", 82 to "ब्याऐंशी", 83 to "त्र्याऐंशी", 84 to "चौऱ्याऐंशी", 85 to "पंच्याऐंशी",
        86 to "शहाऐंशी", 87 to "सत्याऐंशी", 88 to "अठ्ठ्याऐंशी", 89 to "एकोणनव्वद", 90 to "नव्वद",
        91 to "एक्क्याण्णव", 92 to "ब्याण्णव", 93 to "त्र्याण्णव", 94 to "चौऱ्याण्णव", 95 to "पंच्याण्णव",
        96 to "शहाण्णव", 97 to "सत्याण्णव", 98 to "अठ्ठ्याण्णव", 99 to "नव्व्याण्णव", 100 to "शंभर"
    )

    fun numberToMarathiWords(n: Long): String {
        if (n < 0) return "वजा " + numberToMarathiWords(-n)
        if (n in 0..100) return MARATHI_0_TO_100[n.toInt()] ?: n.toString()

        val hundredsPrefix = arrayOf(
            "", "एकशे", "दोनशे", "तीनशे", "चारशे", "पाचशे", "सहाशे", "सातशे", "आठशे", "नऊशे"
        )

        return when {
            n < 1000 -> {
                val h = (n / 100).toInt()
                val rem = n % 100
                if (rem == 0L) {
                    if (h == 1) "शंभर" else hundredsPrefix[h]
                } else {
                    "${hundredsPrefix[h]} ${numberToMarathiWords(rem)}".trim()
                }
            }
            n < 100000 -> { // Up to 99,999 (Thousands)
                val th = n / 1000
                val rem = n % 1000
                val thStr = "${numberToMarathiWords(th)} हजार"
                if (rem == 0L) thStr else "$thStr ${numberToMarathiWords(rem)}"
            }
            n < 10000000 -> { // Up to 99,99,999 (Lakhs)
                val lk = n / 100000
                val rem = n % 100000
                val lkStr = "${numberToMarathiWords(lk)} लाख"
                if (rem == 0L) lkStr else "$lkStr ${numberToMarathiWords(rem)}"
            }
            n < 10000000000L -> { // Up to 999 Crores
                val cr = n / 10000000
                val rem = n % 10000000
                val crStr = "${numberToMarathiWords(cr)} कोटी"
                if (rem == 0L) crStr else "$crStr ${numberToMarathiWords(rem)}"
            }
            else -> {
                digitsToMarathiWords(n.toString())
            }
        }
    }

    fun numberToMarathiWords(n: Int): String = numberToMarathiWords(n.toLong())

    fun digitsToMarathiWords(digitStr: String): String {
        val digitMap = mapOf(
            '0' to "शून्य", '1' to "एक", '2' to "दोन", '3' to "तीन", '4' to "चार",
            '5' to "पाच", '6' to "सहा", '7' to "सात", '8' to "आठ", '9' to "नऊ",
            '०' to "शून्य", '१' to "एक", '२' to "दोन", '३' to "तीन", '४' to "चार",
            '५' to "पाच", '६' to "सहा", '७' to "सात", '८' to "आठ", '९' to "नऊ"
        )
        return digitStr.mapNotNull { digitMap[it] }.joinToString(" ")
    }

    val MARATHI_WORDS_TO_NUM: Map<String, Int> = MARATHI_0_TO_100.entries.associate { (k, v) -> v to k }

    val ENGLISH_UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19
    )

    val ENGLISH_TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    fun digitsToDevanagari(asciiDigits: String): String {
        val devanagariDigits = "०१२३४५६७८९"
        return asciiDigits.map { ch ->
            if (ch in '0'..'9') devanagariDigits[ch - '0'] else ch
        }.joinToString("")
    }

    fun devanagariToAsciiDigits(devanagariStr: String): String {
        val devanagariDigits = "०१२३४५६७८९"
        var res = devanagariStr
        for (i in 0..9) {
            res = res.replace(devanagariDigits[i], ('0' + i))
        }
        return res
    }

    fun parseIndicOrAsciiNumber(str: String): Long? {
        val ascii = devanagariToAsciiDigits(str).trim()
        return ascii.toLongOrNull()
    }

    /**
     * Converts any English numeric phrase (e.g. "sixty nine", "69", "sixty-nine", "hundred") into authentic Marathi words.
     */
    fun convertEnglishNumberWordsToMarathi(text: String): String {
        var res = text

        // 1. Compound numbers with optional percent: e.g. "sixty nine percent", "sixty-nine", "twenty five"
        val compoundPercentRegex = Regex("""(?i)\b(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)[\s-]+(one|two|three|four|five|six|seven|eight|nine)\s*(?:percent|%)\b""")
        res = compoundPercentRegex.replace(res) { match ->
            val tens = ENGLISH_TENS[match.groupValues[1].lowercase(Locale.ROOT)] ?: 0
            val unit = ENGLISH_UNITS[match.groupValues[2].lowercase(Locale.ROOT)] ?: 0
            val sum = tens + unit
            val mrWord = MARATHI_0_TO_100[sum] ?: sum.toString()
            "$mrWord टक्के"
        }

        val compoundRegex = Regex("""(?i)\b(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)[\s-]+(one|two|three|four|five|six|seven|eight|nine)\b""")
        res = compoundRegex.replace(res) { match ->
            val tens = ENGLISH_TENS[match.groupValues[1].lowercase(Locale.ROOT)] ?: 0
            val unit = ENGLISH_UNITS[match.groupValues[2].lowercase(Locale.ROOT)] ?: 0
            val sum = tens + unit
            MARATHI_0_TO_100[sum] ?: sum.toString()
        }

        // 2. Scale words with percent
        res = res.replace(Regex("""(?i)\b(?:one[\s-])?hundred\s*(?:percent|%)\b"""), "शंभर टक्के")
        res = res.replace(Regex("""(?i)\b(?:one[\s-])?thousand\b"""), "हजार")
        res = res.replace(Regex("""(?i)\b(?:one[\s-])?hundred\b"""), "शंभर")

        // 3. Tens words
        for ((word, num) in ENGLISH_TENS) {
            val mrWord = MARATHI_0_TO_100[num] ?: num.toString()
            res = res.replace(Regex("""(?i)\b$word\s*(?:percent|%)\b"""), "$mrWord टक्के")
            res = res.replace(Regex("""(?i)\b$word\b"""), mrWord)
        }

        // 4. Units & Teens words
        for ((word, num) in ENGLISH_UNITS) {
            val mrWord = MARATHI_0_TO_100[num] ?: num.toString()
            res = res.replace(Regex("""(?i)\b$word\s*(?:percent|%)\b"""), "$mrWord टक्के")
            res = res.replace(Regex("""(?i)\b$word\b"""), mrWord)
        }

        res = res.replace(Regex("""(?i)\bpercent\b"""), "टक्के")
        return res
    }

    /**
     * Converts English number phrases into numeric digits for intent/rule parsers.
     * E.g. "sixty nine" -> "69", "hundred" -> "100", "five" -> "5"
     */
    fun convertEnglishNumberWordsToDigits(text: String): String {
        var res = text
        val compoundRegex = Regex("""(?i)\b(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)[\s-]+(one|two|three|four|five|six|seven|eight|nine)\b""")
        res = compoundRegex.replace(res) { match ->
            val tens = ENGLISH_TENS[match.groupValues[1].lowercase(Locale.ROOT)] ?: 0
            val unit = ENGLISH_UNITS[match.groupValues[2].lowercase(Locale.ROOT)] ?: 0
            (tens + unit).toString()
        }

        res = res.replace(Regex("""(?i)\b(?:one[\s-])?hundred\b"""), "100")
        res = res.replace(Regex("""(?i)\b(?:one[\s-])?thousand\b"""), "1000")

        for ((word, num) in ENGLISH_TENS) {
            res = res.replace(Regex("""(?i)\b$word\b"""), num.toString())
        }
        for ((word, num) in ENGLISH_UNITS) {
            res = res.replace(Regex("""(?i)\b$word\b"""), num.toString())
        }
        return res
    }

    /**
     * Formats recognized user speech for screen display in Marathi mode.
     * If user speaks numbers or English numeric words (like "69", "sixty nine", "६९"),
     * formats them into authentic Marathi Devanagari numerals and words (e.g. "६९ (एकोनसत्तर)" or "६९")
     * so that the UI never displays awkward English words like "sixty nine" when speaking Marathi.
     */
    fun formatRecognizedSpeechForDisplay(text: String, isMarathi: Boolean): String {
        if (!isMarathi || text.isBlank()) return text

        val trimmed = text.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // Case 1: The entire speech is an English compound number word (e.g. "sixty nine", "sixty-nine")
        val compoundMatch = Regex("""^(?:number\s+)?(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)[\s-]+(one|two|three|four|five|six|seven|eight|nine)$""", RegexOption.IGNORE_CASE).find(lower)
        if (compoundMatch != null) {
            val tens = ENGLISH_TENS[compoundMatch.groupValues[1].lowercase(Locale.ROOT)] ?: 0
            val unit = ENGLISH_UNITS[compoundMatch.groupValues[2].lowercase(Locale.ROOT)] ?: 0
            val sum = tens + unit
            val mrWord = MARATHI_0_TO_100[sum] ?: sum.toString()
            val devDigits = digitsToDevanagari(sum.toString())
            return "$devDigits ($mrWord)"
        }

        // Case 2: The entire speech is a single English number word (e.g. "sixty", "five", "hundred")
        val singleEnglishVal = ENGLISH_UNITS[lower] ?: ENGLISH_TENS[lower] ?: when (lower) {
            "hundred", "one hundred" -> 100
            "zero" -> 0
            else -> null
        }
        if (singleEnglishVal != null) {
            val mrWord = MARATHI_0_TO_100[singleEnglishVal] ?: singleEnglishVal.toString()
            val devDigits = digitsToDevanagari(singleEnglishVal.toString())
            return "$devDigits ($mrWord)"
        }

        // Case 3: The entire speech is ASCII digits (e.g. "69")
        if (trimmed.matches(Regex("""^\d+$"""))) {
            val num = trimmed.toLongOrNull()
            if (num != null) {
                val mrWord = numberToMarathiWords(num)
                val devDigits = digitsToDevanagari(trimmed)
                return "$devDigits ($mrWord)"
            }
        }

        // Case 4: The entire speech is Devanagari digits (e.g. "६९")
        if (trimmed.matches(Regex("""^[०-९]+$"""))) {
            val num = parseIndicOrAsciiNumber(trimmed)
            if (num != null) {
                val mrWord = numberToMarathiWords(num)
                return "$trimmed ($mrWord)"
            }
        }

        // Case 5: The entire speech is a Marathi number word (e.g. "एकोनसत्तर")
        val mrNumVal = MARATHI_WORDS_TO_NUM[trimmed]
        if (mrNumVal != null) {
            val devDigits = digitsToDevanagari(mrNumVal.toString())
            return "$devDigits ($trimmed)"
        }

        // Case 6: Sentences with numbers or English numeric words (e.g. "आवाज 69 करा", "व्हॉल्युम sixty nine करा", "बॅटरी 69%")
        var formatted = convertEnglishNumberWordsToMarathi(trimmed)
        formatted = Regex("""(?<![a-zA-Z])(\d+)(?![a-zA-Z])""").replace(formatted) { match ->
            digitsToDevanagari(match.groupValues[1])
        }
        return formatted
    }

    /**
     * Normalizes text for speech so that times, percentages, and ALL numbers (0-100+)
     * are converted into authentic Marathi Devanagari words.
     * Prevents TTS engines from pronouncing English numbers (like 69) in Hindi ("unhattar").
     */
    fun normalizeTextForSpeech(text: String, isMarathi: Boolean): String {
        var processed = text

        // 1. Strip markdown formatting symbols that confuse TTS
        processed = processed.replace(Regex("""[*_~#>`]"""), " ")

        if (isMarathi) {
            // First convert English number words that might have been spoken or returned
            processed = convertEnglishNumberWordsToMarathi(processed)

            // 2. Convert timestamps (e.g. 12:30 PM, 1:05 AM, 12:30, ०१:३०)
            val timeRegex = Regex("""(\d{1,2}|[०-९]{1,2}):(\d{2}|[०-९]{2})(?::(\d{2}|[०-९]{2}))?\s*(AM|PM|am|pm)?""")
            processed = timeRegex.replace(processed) { match ->
                val rawHour = parseIndicOrAsciiNumber(match.groupValues[1])?.toInt() ?: return@replace match.value
                val minute = parseIndicOrAsciiNumber(match.groupValues[2])?.toInt() ?: 0
                val amPm = match.groupValues[4].uppercase(Locale.ROOT)

                val hour12 = if (rawHour > 12) rawHour - 12 else if (rawHour == 0) 12 else rawHour
                val hourWord = numberToMarathiWords(hour12)
                val period = when {
                    amPm == "AM" -> if (rawHour < 12) "सकाळी" else "रात्री"
                    amPm == "PM" -> if (rawHour < 4 || rawHour == 12) "दुपारी" else if (rawHour < 8) "संध्याकाळी" else "रात्री"
                    else -> ""
                }
                if (minute == 0) {
                    "$period $hourWord वाजता".trim()
                } else {
                    "$period $hourWord वाजून ${numberToMarathiWords(minute)} मिनिटे".trim()
                }
            }

            // 3. Percentages: 69%, 69 टक्के, ६९%, १००% -> "एकोनसत्तर टक्के", "शंभर टक्के"
            val percentRegex = Regex("""(\d+|[०-९]+)\s*(?:%|टक्के)""")
            processed = percentRegex.replace(processed) { match ->
                val num = parseIndicOrAsciiNumber(match.groupValues[1])
                if (num != null) {
                    "${numberToMarathiWords(num)} टक्के"
                } else {
                    match.value
                }
            }

            // 4. Phone numbers / series of digits (7 to 15 digits): pronounce digit-by-digit in Marathi
            val phoneRegex = Regex("""(?<!\w)(\d{7,15}|[०-९]{7,15})(?!\w)""")
            processed = phoneRegex.replace(processed) { match ->
                digitsToMarathiWords(match.groupValues[1])
            }

            // 5. Decimals: e.g. 12.5 -> बारा दशांश पाच
            val decimalRegex = Regex("""(\d+|[०-९]+)\.(\d+|[०-९]+)""")
            processed = decimalRegex.replace(processed) { match ->
                val intPart = parseIndicOrAsciiNumber(match.groupValues[1])
                val decPart = parseIndicOrAsciiNumber(match.groupValues[2])
                if (intPart != null && decPart != null) {
                    "${numberToMarathiWords(intPart)} दशांश ${numberToMarathiWords(decPart)}"
                } else {
                    match.value
                }
            }

            // 6. ALL remaining standalone numbers (ASCII or Devanagari) -> convert directly to Marathi words!
            // This guarantees numbers like 69, 100, 2026 are NEVER read in Hindi!
            val numberRegex = Regex("""(?<![a-zA-Z0-9])(\d+|[०-९]+)(?![a-zA-Z0-9])""")
            processed = numberRegex.replace(processed) { match ->
                val num = parseIndicOrAsciiNumber(match.groupValues[1])
                if (num != null) {
                    numberToMarathiWords(num)
                } else {
                    match.value
                }
            }

            // 7. Math symbols
            processed = processed.replace("&", " आणि ")
            processed = processed.replace("+", " अधिक ")
            processed = processed.replace(" - ", " वजा ")
            processed = processed.replace("=", " बरोबर ")
            processed = processed.replace("*", " गुणिले ")
            processed = processed.replace("/", " भागिले ")
        } else {
            // English speech processing
            val timeRegex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM|am|pm)?""")
            processed = timeRegex.replace(processed) { match ->
                val rawHour = match.groupValues[1].toIntOrNull() ?: return@replace match.value
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                val amPm = match.groupValues[4].uppercase(Locale.ROOT)

                val hour12 = if (rawHour > 12) rawHour - 12 else if (rawHour == 0) 12 else rawHour
                val hourWord = numberToEnglishWords(hour12)
                val minuteWord = when {
                    minute == 0 -> if (amPm.isNotEmpty()) "" else "o'clock"
                    minute in 1..9 -> "oh " + numberToEnglishWords(minute)
                    else -> numberToEnglishWords(minute)
                }
                if (minuteWord.isEmpty()) {
                    "$hourWord $amPm".trim()
                } else {
                    "$hourWord $minuteWord $amPm".trim()
                }
            }

            processed = processed.replace(Regex("""(\d+)\s*%""")) { match ->
                val num = match.groupValues[1]
                "$num percent"
            }

            processed = processed.replace("&", " and ")
            processed = processed.replace("+", " plus ")
        }

        // Replace remaining standalone colons with commas so TTS never speaks the word "colon"
        processed = processed.replace(Regex("""(?<=\w):"""), ",")
        processed = processed.replace(Regex("""\s+:\s+"""), ", ")

        // Clean up multiple whitespaces
        processed = processed.replace(Regex("""\s+"""), " ").trim()

        return processed
    }

    /**
     * Identifies if a voice is explicitly female / woman.
     * Checks voice features, names, tags, and standard Google/Samsung TTS naming schemes.
     */
    fun isExplicitFemaleVoice(voice: Voice): Boolean {
        val name = voice.name.lowercase(Locale.ROOT)
        val features = voice.features?.map { it.lowercase(Locale.ROOT) }?.toSet() ?: emptySet()
        if (features.any { it.contains("female") || it.contains("gender=female") || it.contains("woman") }) return true
        if (name.contains("female") || name.contains("woman") || name.contains("#fem") || name.contains("_female") || name.contains("-fem")) return true

        // Google TTS voice naming scheme:
        // [lang]-[country]-x-[voice-code]-[local/network]
        // Examples:
        // mr-in-x-mrf-local (mrf ends with 'f' -> female!)
        // hi-in-x-hif-local (hif ends with 'f' -> female!)
        // en-in-x-enc-local (enc is Google's primary Indian English female voice!)
        // en-in-x-end-local, en-in-x-ene-local (female)
        // en-us-x-sfg#female_1, en-us-x-tpd#female_2, en-us-x-iol#female_1
        val googleCodeMatch = Regex("""^[a-z]{2,3}-[a-z]{2,3}-x-([a-z0-9]{3})""").find(name)
        val code = googleCodeMatch?.groupValues?.getOrNull(1)
        if (code != null) {
            if (code.endsWith("f")) return true
            if (code in listOf("enc", "end", "ene", "cxx", "sfg", "tpd", "iol", "iob")) return true
        }
        return false
    }

    /**
     * Identifies if a voice is explicitly male. Used to strictly reject male voices for Jarvis.
     */
    fun isExplicitMaleVoice(voice: Voice): Boolean {
        val name = voice.name.lowercase(Locale.ROOT)
        val features = voice.features?.map { it.lowercase(Locale.ROOT) }?.toSet() ?: emptySet()
        if (features.any { it.contains("male") || it.contains("gender=male") || it.contains("man") } &&
            !features.any { it.contains("female") }
        ) {
            return true
        }
        if ((name.contains("male") || name.contains("#mal") || name.contains("_male") || name.contains("-mal")) &&
            !name.contains("female")
        ) {
            return true
        }
        val googleCodeMatch = Regex("""^[a-z]{2,3}-[a-z]{2,3}-x-([a-z0-9]{3})""").find(name)
        val code = googleCodeMatch?.groupValues?.getOrNull(1)
        if (code != null) {
            if (code.endsWith("m")) return true
            if (code in listOf("ena", "enb", "mrm", "him", "sfd", "tpb")) return true
        }
        return false
    }

    private fun selectBestVoiceForLocale(tts: TextToSpeech, targetLocale: Locale) {
        try {
            val allVoices: Set<Voice>? = tts.voices
            if (allVoices.isNullOrEmpty()) return

            val targetLang = targetLocale.language
            val targetCountry = targetLocale.country

            // Jarvis speaks ONLY in women / female voice!
            val candidateVoices = allVoices.filter { voice ->
                voice.locale.language.equals(targetLang, ignoreCase = true)
            }

            if (candidateVoices.isEmpty()) return

            // Ranking criteria for Female-Only Jarvis:
            // 1. Explicit Female voice matching language & country + local offline
            // 2. Explicit Female voice matching language & country
            // 3. Explicit Female voice matching language (any region)
            // 4. Non-male voice matching language & country + offline
            // 5. Non-male voice matching language & country
            // 6. Non-male voice matching language
            // 7. Fallback: Any voice matching language (if device has only 1 voice installed)
            val selectedVoice = candidateVoices.find { voice ->
                isExplicitFemaleVoice(voice) &&
                        voice.locale.country.equals(targetCountry, ignoreCase = true) &&
                        !voice.isNetworkConnectionRequired
            } ?: candidateVoices.find { voice ->
                isExplicitFemaleVoice(voice) &&
                        voice.locale.country.equals(targetCountry, ignoreCase = true)
            } ?: candidateVoices.find { voice ->
                isExplicitFemaleVoice(voice)
            } ?: candidateVoices.find { voice ->
                !isExplicitMaleVoice(voice) &&
                        voice.locale.country.equals(targetCountry, ignoreCase = true) &&
                        !voice.isNetworkConnectionRequired
            } ?: candidateVoices.find { voice ->
                !isExplicitMaleVoice(voice) &&
                        voice.locale.country.equals(targetCountry, ignoreCase = true)
            } ?: candidateVoices.find { voice ->
                !isExplicitMaleVoice(voice)
            } ?: candidateVoices.firstOrNull()

            if (selectedVoice != null) {
                tts.voice = selectedVoice
                Log.d(TAG, "Selected Female TTS Voice: ${selectedVoice.name} (${selectedVoice.locale}) for target $targetLocale")
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
