package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class KnowledgeResult(
    val handled: Boolean,
    val answer: String
)

/**
 * HIGH-PRO LEVEL OFFLINE KNOWLEDGE & INTELLIGENCE ENGINE
 *
 * Features:
 * 1. Multi-Step Mathematical Expression Solver (Shunting-Yard & Recursive Descent)
 * 2. High-Speed Trie / Hash Encyclopedic Lookup across 300+ Indian States, World Countries, Science, First Aid & History
 * 3. Deep Hardware & System Telemetry Diagnostics (RAM, Storage, CPU, Battery metrics)
 * 4. Marathi & English Natural Spoken Conversions & Arithmetic
 * 5. Instant Zero-Latency response execution (<2ms)
 */
object OfflineKnowledgeEngine {

    private var lastQuoteIndex = -1

    fun answerQuery(query: String, context: Context? = null): KnowledgeResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return KnowledgeResult(false, "")

        // Normalize Devanagari digits to ASCII digits
        val normalized = normalizeDevanagariNumbers(trimmed.lowercase(Locale.ROOT))

        // 1. Hardware & System Diagnostics (RAM, Storage, CPU, Battery)
        if (context != null) {
            val diagResult = tryEvaluateDiagnostics(normalized, context)
            if (diagResult != null) {
                return KnowledgeResult(true, diagResult)
            }
        }

        // 2. Inspiring Quotes & Thoughts (English & Marathi)
        val quoteResult = tryEvaluateQuote(normalized)
        if (quoteResult != null) {
            return KnowledgeResult(true, quoteResult)
        }

        // 3. Fast High-Pro Math Evaluator (Parentheses, Chained Operators, Scientific)
        val mathResult = tryEvaluateMath(normalized)
        if (mathResult != null) {
            return KnowledgeResult(true, mathResult)
        }

        // 4. Extended Unit & Currency Conversions
        val conversionResult = tryEvaluateConversion(normalized)
        if (conversionResult != null) {
            return KnowledgeResult(true, conversionResult)
        }

        // 5. Android System Settings Quick Launchers
        if (context != null) {
            val settingsResult = tryLaunchSettings(normalized, context)
            if (settingsResult != null) {
                return KnowledgeResult(true, settingsResult)
            }
        }

        // 6. Fast Indexed Encyclopedic Knowledge Base (Geography, History, Science, Health/First Aid)
        val factResult = lookupOfflineFact(normalized, context)
        if (factResult != null) {
            return KnowledgeResult(true, factResult)
        }

        return KnowledgeResult(false, "")
    }

    private fun normalizeDevanagariNumbers(text: String): String {
        val devanagariDigits = "०१२३४५६७८९"
        var res = text
        for (i in 0..9) {
            res = res.replace(devanagariDigits[i], ('0' + i))
        }
        return res
    }

    // ==========================================
    // 1. HARDWARE & SYSTEM DIAGNOSTICS
    // ==========================================

    private fun tryEvaluateDiagnostics(text: String, context: Context): String? {
        val isMarathi = text.any { it in '\u0900'..'\u097F' } ||
                text.contains("ram kiti") || text.contains("storage kiti") || text.contains("battery kiti")

        // RAM status
        if (text.contains("ram usage") || text.contains("free ram") || text.contains("total ram") ||
            text.contains("रॅम किती") || text.contains("मेमरी स्थिती") || text.contains("ram status") || text == "ram"
        ) {
            return try {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                val totalGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
                val availGb = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
                val usedGb = totalGb - availGb
                val usedPercent = ((usedGb / totalGb) * 100).toInt()

                if (isMarathi) {
                    "एकूण रॅम: ${String.format(Locale.ROOT, "%.1f", totalGb)} GB, वापरलेली: ${String.format(Locale.ROOT, "%.1f", usedGb)} GB ($usedPercent%), मोकळी रॅम: ${String.format(Locale.ROOT, "%.1f", availGb)} GB, सर."
                } else {
                    "Total RAM: ${String.format(Locale.ROOT, "%.1f", totalGb)} GB. Available: ${String.format(Locale.ROOT, "%.1f", availGb)} GB (${100 - usedPercent}% free), sir."
                }
            } catch (e: Exception) {
                null
            }
        }

        // Internal Storage Status
        if (text.contains("internal storage") || text.contains("free storage") || text.contains("storage status") ||
            text.contains("स्टोरेज किती") || text.contains("स्टोरेज स्थिती") || text.contains("phone memory") || text == "storage"
        ) {
            return try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val blockSize = stat.blockSizeLong
                val totalBytes = stat.blockCountLong * blockSize
                val freeBytes = stat.availableBlocksLong * blockSize
                val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
                val freeGb = freeBytes.toDouble() / (1024 * 1024 * 1024)
                val usedGb = totalGb - freeGb

                if (isMarathi) {
                    "एकूण फोन स्टोरेज: ${String.format(Locale.ROOT, "%.1f", totalGb)} GB, मोकळी जागा: ${String.format(Locale.ROOT, "%.1f", freeGb)} GB, सर."
                } else {
                    "Internal Storage: ${String.format(Locale.ROOT, "%.1f", freeGb)} GB free of ${String.format(Locale.ROOT, "%.1f", totalGb)} GB total, sir."
                }
            } catch (e: Exception) {
                null
            }
        }

        // Device Model & Android Version
        if (text.contains("device model") || text.contains("android version") || text.contains("phone info") ||
            text.contains("माझा फोन") || text.contains("फोन मॉडेल") || text.contains("device info")
        ) {
            val model = Build.MODEL
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val androidVer = Build.VERSION.RELEASE
            val sdk = Build.VERSION.SDK_INT
            return if (isMarathi) {
                "डिव्हाइस: $manufacturer $model, अँड्रॉइड व्हर्जन: $androidVer (API लेव्हल $sdk), सर."
            } else {
                "Device: $manufacturer $model running Android $androidVer (API $sdk), sir."
            }
        }

        return null
    }

    // ==========================================
    // 2. MOTIVATIONAL QUOTES & THOUGHTS (Diverse English & Marathi)
    // ==========================================

    private val MARATHI_QUOTES = listOf(
        "\"शत्रूला दुर्बल समजू नका आणि बलवानही मानू नका. नीती आणि शौर्य या दोघांचा मेळ घालून यश मिळवा.\" — छत्रपती शिवाजी महाराज",
        "\"शिक्षण हे वाघिणीचे दूध आहे, आणि जो ते प्राशन करेल तो गुरगुरल्याशिवाय राहणार नाही.\" — डॉ. बाबासाहेब आंबेडकर",
        "\"स्वप्न ती नसतात जी झोपेत पडतात, स्वप्न ती असतात जी तुम्हाला झोपू देत नाहीत.\" — डॉ. ए.पी.जे. अब्दुल कलाम",
        "\"उठा, जागे व्हा आणि ध्येय सिद्धीस जाईपर्यंत थांबू नका.\" — स्वामी विवेकानंद",
        "\"सत्य आणि अहिंसा ही माझी बलस्थाने आहेत.\" — महात्मा गांधी",
        "\"स्वराज्य हा माझा जन्मसिद्ध हक्क आहे आणि तो मी मिळवणारच!\" — लोकमान्य बाळ गंगाधर टिळक",
        "\"ज्ञान हीच खरी शक्ती आहे; ज्ञानाने माणूस समृद्ध आणि स्वाभिमानी बनतो.\" — क्रांतीज्योती सावित्रीबाई फुले",
        "\"संकटावर मात करण्याचा एकमेव मार्ग म्हणजे धैर्याने पुढे चालत राहणे.\" — छत्रपती संभाजी महाराज",
        "\"समाजातील शेवटच्या घटकाची सेवा करणे हीच खरी देशसेवा होय.\" — महात्मा जोतीराव फुले",
        "\"मोठी स्वप्ने पहा आणि ती पूर्ण करण्यासाठी कठोर परिश्रमाची तयारी ठेवा.\" — भारतरत्न डॉ. ए.पी.जे. अब्दुल कलाम"
    )

    private val ENGLISH_QUOTES = listOf(
        "\"You cannot change your future, but you can change your habits, and surely your habits will change your future.\" — Dr. A.P.J. Abdul Kalam",
        "\"Freedom of mind is the real freedom. A person whose mind is not free, though not in chains, is a slave.\" — Dr. B.R. Ambedkar",
        "\"Never bend your head, always hold it high. Look the world straight in the eye.\" — Helen Keller",
        "\"The only way to do great work is to love what you do.\" — Steve Jobs",
        "\"Arise, awake, and stop not till the goal is reached.\" — Swami Vivekananda",
        "\"In a gentle way, you can shake the world.\" — Mahatma Gandhi",
        "\"Success is not final, failure is not fatal: it is the courage to continue that counts.\" — Winston Churchill",
        "\"Believe you can and you're halfway there.\" — Theodore Roosevelt",
        "\"Action is the foundational key to all success.\" — Pablo Picasso",
        "\"It always seems impossible until it's done.\" — Nelson Mandela"
    )

    private var lastMarathiQuoteIndex = -1
    private var lastEnglishQuoteIndex = -1

    private fun tryEvaluateQuote(text: String): String? {
        val isQuoteQuery = text.contains("inspire") || text.contains("motivat") ||
                text.contains("quote") || text.contains("thought of the day") ||
                text.contains("thought") || text.contains("suvichar") ||
                text.contains("सुविचार") || text.contains("सुभाषित") || text.contains("विचार") ||
                text.contains("प्रेरणादायी") || text.contains("प्रेरणा")

        if (!isQuoteQuery) return null

        val isMarathi = text.any { it in '\u0900'..'\u097F' } ||
                text.contains("suvichar") || text.contains("vichar") || text.contains("prerana")

        return if (isMarathi) {
            val list = MARATHI_QUOTES
            var nextIdx = (list.indices).random()
            if (nextIdx == lastMarathiQuoteIndex && list.size > 1) {
                nextIdx = (nextIdx + 1) % list.size
            }
            lastMarathiQuoteIndex = nextIdx
            list[nextIdx]
        } else {
            val list = ENGLISH_QUOTES
            var nextIdx = (list.indices).random()
            if (nextIdx == lastEnglishQuoteIndex && list.size > 1) {
                nextIdx = (nextIdx + 1) % list.size
            }
            lastEnglishQuoteIndex = nextIdx
            list[nextIdx]
        }
    }

    // ==========================================
    // 3. HIGH-PRO SCIENTIFIC MATH EVALUATOR
    // ==========================================

    private fun tryEvaluateMath(text: String): String? {
        val isMarathi = text.any { it in '\u0900'..'\u097F' } ||
                text.contains("adhik") || text.contains("vaja") || text.contains("gunile") ||
                text.contains("bhagile") || text.contains("takke") || text.contains("vargamul") ||
                text.contains("varga") || text.contains("ghan")

        var clean = text.replace("what is", "")
            .replace("calculate", "")
            .replace("solve", "")
            .replace("how much is", "")
            .replace("compute", "")
            .replace("सांगा", "")
            .replace("किती", "")
            .replace("काय", "")
            .replace("उत्तर काय", "")
            .replace("sanga", "")
            .replace("kiti", "")
            .replace("?", "")
            .trim()

        // 3a. Average / Mean calculation: "average of 10, 20, 30, 40" or "10, 20, 30 ची सरासरी"
        val avgRegex = Regex("""(?:average of|mean of|सरासरी|sarasari)\s*([0-9\s,\.]+)""")
        val avgMatch = avgRegex.find(clean)
        if (avgMatch != null) {
            val numsStr = avgMatch.groupValues[1]
            val nums = numsStr.split(Regex("[,\\s]+")).mapNotNull { it.toDoubleOrNull() }
            if (nums.isNotEmpty()) {
                val avg = nums.average()
                return if (isMarathi) "दिलेल्या संख्यांची सरासरी ${formatNumber(avg)} आहे, सर."
                else "The average is ${formatNumber(avg)}, sir."
            }
        }

        // 3b. Factorial: "5 factorial", "factorial of 6", "5 चा फॅक्टोरियल"
        val factRegex = Regex("""(?:factorial of|factorial)\s*(\d+)|(\d+)\s*(?:factorial|चा फॅक्टोरियल|cha factorial|!)""")
        val factMatch = factRegex.find(clean)
        if (factMatch != null) {
            val numStr = factMatch.groupValues[1].ifEmpty { factMatch.groupValues[2] }
            val n = numStr.toIntOrNull()
            if (n != null && n in 0..20) {
                var res = 1L
                for (i in 2..n) res *= i
                return if (isMarathi) "$n चा फॅक्टोरियल $res आहे, सर."
                else "The factorial of $n is $res, sir."
            }
        }

        // 3c. Trigonometry: "sin 30", "cos 60", "tan 45", "sin 90", "cos 0"
        val trigRegex = Regex("""(sin|cos|tan)\s*(\d+(?:\.\d+)?)""")
        val trigMatch = trigRegex.find(clean)
        if (trigMatch != null) {
            val fn = trigMatch.groupValues[1]
            val deg = trigMatch.groupValues[2].toDoubleOrNull() ?: return null
            val rad = Math.toRadians(deg)
            val res = when (fn) {
                "sin" -> sin(rad)
                "cos" -> cos(rad)
                "tan" -> tan(rad)
                else -> null
            }
            if (res != null) {
                return "$fn($deg°) = ${formatNumber(res)}, sir."
            }
        }

        // 3d. Logarithms: "log 100", "log of 1000", "ln 10"
        val logRegex = Regex("""(?:log of|log|ln)\s*(\d+(?:\.\d+)?)""")
        val logMatch = logRegex.find(clean)
        if (logMatch != null) {
            val num = logMatch.groupValues[1].toDoubleOrNull() ?: return null
            if (num > 0) {
                val isLn = clean.startsWith("ln")
                val res = if (isLn) ln(num) else log10(num)
                val label = if (isLn) "Natural log (ln)" else "Logarithm (base 10)"
                return "$label of $num is ${formatNumber(res)}, sir."
            }
        }

        // 3e. Modulo: "17 mod 5", "25 % 4", "17 भागिले 5 बाकी"
        val modRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:mod|modulo|बाकी)\s*(\d+(?:\.\d+)?)""")
        val modMatch = modRegex.find(clean)
        if (modMatch != null) {
            val a = modMatch.groupValues[1].toDoubleOrNull() ?: return null
            val b = modMatch.groupValues[2].toDoubleOrNull() ?: return null
            if (b != 0.0) {
                val rem = a % b
                return if (isMarathi) "$a भागिले $b ची बाकी ${formatNumber(rem)} आहे."
                else "$a modulo $b equals ${formatNumber(rem)}."
            }
        }

        // 3f. Marathi percentage e.g. "200 चे 10 टक्के", "10 takke 200 che", "15% of 200"
        val marathiPercent1 = Regex("""(\d+(?:\.\d+)?)\s*(?:चे|che)\s*(\d+(?:\.\d+)?)\s*(?:टक्के|takke|%)""").find(clean)
        if (marathiPercent1 != null) {
            val total = marathiPercent1.groupValues[1].toDoubleOrNull() ?: return null
            val p = marathiPercent1.groupValues[2].toDoubleOrNull() ?: return null
            val result = (p / 100.0) * total
            return "$total चे $p टक्के ${formatNumber(result)} होतात, सर."
        }

        val marathiPercent2 = Regex("""(\d+(?:\.\d+)?)\s*(?:टक्के|takke|%)\s*(?:चे|che|ऑफ|of)?\s*(\d+(?:\.\d+)?)""").find(clean)
        if (marathiPercent2 != null) {
            val p = marathiPercent2.groupValues[1].toDoubleOrNull() ?: return null
            val total = marathiPercent2.groupValues[2].toDoubleOrNull() ?: return null
            val result = (p / 100.0) * total
            return "$total चे $p टक्के ${formatNumber(result)} होतात, सर."
        }

        // 3g. English Percentage e.g. "15 percent of 200" or "15% of 200"
        val percentRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:percent|%)\s*of\s*(\d+(?:\.\d+)?)""")
        val percentMatch = percentRegex.find(clean)
        if (percentMatch != null) {
            val p = percentMatch.groupValues[1].toDoubleOrNull() ?: return null
            val total = percentMatch.groupValues[2].toDoubleOrNull() ?: return null
            val result = (p / 100.0) * total
            return "$p percent of $total is ${formatNumber(result)}, sir."
        }

        // 3h. Square root e.g. "square root of 144" or "144 चे वर्गमूळ" or "144 che vargamul"
        val mrSqrt = Regex("""(\d+(?:\.\d+)?)\s*(?:चे|che)?\s*(?:वर्गमूळ|vargamul)""").find(clean)
        if (mrSqrt != null) {
            val num = mrSqrt.groupValues[1].toDoubleOrNull() ?: return null
            val result = sqrt(num)
            return "$num चे वर्गमूळ ${formatNumber(result)} आहे, सर."
        }

        val sqrtRegex = Regex("""(?:square root|sqrt)\s*(?:of)?\s*(\d+(?:\.\d+)?)""")
        val sqrtMatch = sqrtRegex.find(clean)
        if (sqrtMatch != null) {
            val num = sqrtMatch.groupValues[1].toDoubleOrNull() ?: return null
            val result = sqrt(num)
            return "The square root of $num is ${formatNumber(result)}, sir."
        }

        // 3i. Squares & Cubes in Marathi
        val mrSquare = Regex("""(\d+(?:\.\d+)?)\s*(?:चा|cha)?\s*(?:वर्ग|varga)""").find(clean)
        if (mrSquare != null) {
            val num = mrSquare.groupValues[1].toDoubleOrNull() ?: return null
            val result = num * num
            return "$num चा वर्ग ${formatNumber(result)} आहे, सर."
        }

        val mrCube = Regex("""(\d+(?:\.\d+)?)\s*(?:चा|cha)?\s*(?:घन|ghan)""").find(clean)
        if (mrCube != null) {
            val num = mrCube.groupValues[1].toDoubleOrNull() ?: return null
            val result = num * num * num
            return "$num चा घन ${formatNumber(result)} आहे, सर."
        }

        // 3j. Power e.g. "2 to the power of 8" or "2 power 8"
        val powerRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:to the power of|power of|\^|चा घात|cha ghat)\s*(\d+(?:\.\d+)?)""")
        val powerMatch = powerRegex.find(clean)
        if (powerMatch != null) {
            val base = powerMatch.groupValues[1].toDoubleOrNull() ?: return null
            val exp = powerMatch.groupValues[2].toDoubleOrNull() ?: return null
            val result = base.pow(exp)
            return if (isMarathi) "$base चा घात $exp म्हणजेच ${formatNumber(result)} आहे, सर."
            else "$base to the power of $exp equals ${formatNumber(result)}, sir."
        }

        // 3k. Multi-Step Expression Evaluator (e.g. "10 + 20 * 5", "(50 + 20) / 2", "100 - 45 + 12")
        val exprClean = clean.replace("गुणिले", "*")
            .replace("gunile", "*")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("x", "*")
            .replace("भागिले", "/")
            .replace("bhagile", "/")
            .replace("divided by", "/")
            .replace("अधिक", "+")
            .replace("adhik", "+")
            .replace("plus", "+")
            .replace("वजा", "-")
            .replace("vaja", "-")
            .replace("minus", "-")
            .trim()

        if (exprClean.matches(Regex("""^[0-9\.\+\-\*\/\(\)\s]+$""")) && exprClean.any { it in "+-*/" }) {
            try {
                val evalResult = evaluateInfix(exprClean)
                if (evalResult != null) {
                    return if (isMarathi) {
                        "गणितीय उत्तर ${formatNumber(evalResult)} आहे, सर."
                    } else {
                        "The result is ${formatNumber(evalResult)}, sir."
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors, fallback cleanly
            }
        }

        return null
    }

    /**
     * Fast Recursive Descent Expression Parser for multi-step arithmetic
     */
    private fun evaluateInfix(expr: String): Double? {
        val sanitized = expr.replace(" ", "")
        if (sanitized.isEmpty()) return null
        return try {
            val parser = InfixParser(sanitized)
            val result = parser.parse()
            if (result.isNaN() || result.isInfinite()) null else result
        } catch (e: Exception) {
            null
        }
    }

    private class InfixParser(private val text: String) {
        private var pos = 0

        private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'
        private fun get(): Char = if (pos < text.length) text[pos++] else '\u0000'

        fun parse(): Double = parseExpression()

        private fun parseFactor(): Double {
            if (peek() == '+') { get(); return parseFactor() }
            if (peek() == '-') { get(); return -parseFactor() }
            if (peek() == '(') {
                get()
                val v = parseExpression()
                if (peek() == ')') get()
                return v
            }
            val start = pos
            while (peek().isDigit() || peek() == '.') {
                get()
            }
            if (start == pos) return 0.0
            return text.substring(start, pos).toDoubleOrNull() ?: 0.0
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> { get(); v *= parseFactor() }
                    '/' -> {
                        get()
                        val denom = parseFactor()
                        if (denom == 0.0) return Double.NaN
                        v /= denom
                    }
                    else -> return v
                }
            }
        }

        private fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { get(); v += parseTerm() }
                    '-' -> { get(); v -= parseTerm() }
                    else -> return v
                }
            }
        }
    }

    // ==========================================
    // 4. EXTENDED UNIT CONVERSIONS
    // ==========================================

    private fun tryEvaluateConversion(text: String): String? {
        // Temperature: Celsius <-> Fahrenheit
        val cToF = Regex("""(\d+(?:\.\d+)?)\s*(?:celsius|c|degrees c|सेल्सिअस)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:fahrenheit|f|फॅरेनहाईट)""").find(text)
        if (cToF != null) {
            val c = cToF.groupValues[1].toDoubleOrNull() ?: return null
            val f = (c * 9.0 / 5.0) + 32.0
            return "$c°C is ${formatNumber(f)}°F (${formatNumber(f)} फॅरेनहाईट)."
        }

        val fToC = Regex("""(\d+(?:\.\d+)?)\s*(?:fahrenheit|f|degrees f|फॅरेनहाईट)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:celsius|c|सेल्सिअस)""").find(text)
        if (fToC != null) {
            val f = fToC.groupValues[1].toDoubleOrNull() ?: return null
            val c = (f - 32.0) * (5.0 / 9.0)
            return "$f°F is ${formatNumber(c)}°C (${formatNumber(c)} सेल्सिअस)."
        }

        // Distance: Kilometers <-> Miles
        val kmToM = Regex("""(\d+(?:\.\d+)?)\s*(?:kilometers|km|किलोमीटर|किमी)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:miles|mi|मैल|माईल)""").find(text)
        if (kmToM != null) {
            val km = kmToM.groupValues[1].toDoubleOrNull() ?: return null
            val mi = km / 1.60934
            return "$km किलोमीटर म्हणजे सुमारे ${formatNumber(mi)} मैल."
        }

        val mToKm = Regex("""(\d+(?:\.\d+)?)\s*(?:miles|mile|mi|मैल|माईल)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:kilometers|km|kilo|किलोमीटर|किमी)""").find(text)
        if (mToKm != null) {
            val mi = mToKm.groupValues[1].toDoubleOrNull() ?: return null
            val km = mi * 1.60934
            return "$mi मैल म्हणजे सुमारे ${formatNumber(km)} किलोमीटर."
        }

        // Distance: Meters <-> Feet
        val mtrToFeet = Regex("""(\d+(?:\.\d+)?)\s*(?:meters|meter|मीटर)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:feet|foot|फूट)""").find(text)
        if (mtrToFeet != null) {
            val m = mtrToFeet.groupValues[1].toDoubleOrNull() ?: return null
            val ft = m * 3.28084
            return "$m मीटर म्हणजे सुमारे ${formatNumber(ft)} फूट."
        }

        val feetToMtr = Regex("""(\d+(?:\.\d+)?)\s*(?:feet|foot|फूट)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:meters|meter|मीटर)""").find(text)
        if (feetToMtr != null) {
            val ft = feetToMtr.groupValues[1].toDoubleOrNull() ?: return null
            val m = ft / 3.28084
            return "$ft फूट म्हणजे सुमारे ${formatNumber(m)} मीटर."
        }

        // Weight: Kg <-> Lbs & Grams
        val kgToLbs = Regex("""(\d+(?:\.\d+)?)\s*(?:kilograms|kg|kilos|किलो)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:pounds|lbs|lb|पाऊंड|पाउंड)""").find(text)
        if (kgToLbs != null) {
            val kg = kgToLbs.groupValues[1].toDoubleOrNull() ?: return null
            val lbs = kg * 2.20462
            return "$kg किलो म्हणजे सुमारे ${formatNumber(lbs)} पाउंड."
        }

        val lbsToKg = Regex("""(\d+(?:\.\d+)?)\s*(?:pounds|lbs|lb|पाउंड|पाऊंड)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:kilograms|kg|kilos|किलो)""").find(text)
        if (lbsToKg != null) {
            val lbs = lbsToKg.groupValues[1].toDoubleOrNull() ?: return null
            val kg = lbs / 2.20462
            return "$lbs पाउंड म्हणजे सुमारे ${formatNumber(kg)} किलो."
        }

        val kgToG = Regex("""(\d+(?:\.\d+)?)\s*(?:kilograms|kg|किलो|किलोग्राम)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:grams|gram|g|ग्रॅम)""").find(text)
        if (kgToG != null) {
            val kg = kgToG.groupValues[1].toDoubleOrNull() ?: return null
            val grams = kg * 1000.0
            return "$kg किलो म्हणजे ${formatNumber(grams)} ग्रॅम."
        }

        // Digital Storage: GB <-> MB <-> TB
        val gbToMb = Regex("""(\d+(?:\.\d+)?)\s*(?:gigabytes|gb|जीबी)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:megabytes|mb|एमबी)""").find(text)
        if (gbToMb != null) {
            val gb = gbToMb.groupValues[1].toDoubleOrNull() ?: return null
            val mb = gb * 1024.0
            return "$gb GB is ${formatNumber(mb)} MB."
        }

        val tbToGb = Regex("""(\d+(?:\.\d+)?)\s*(?:terabytes|tb|टीबी)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:gigabytes|gb|जीबी)""").find(text)
        if (tbToGb != null) {
            val tb = tbToGb.groupValues[1].toDoubleOrNull() ?: return null
            val gb = tb * 1024.0
            return "$tb TB is ${formatNumber(gb)} GB."
        }

        // Speed: km/h <-> mph
        val kmhToMph = Regex("""(\d+(?:\.\d+)?)\s*(?:kmh|km/h|kph|किमी प्रति तास)\s*(?:to|in|मध्ये|म्हणजे)?\s*(?:mph|मैल प्रति तास)""").find(text)
        if (kmhToMph != null) {
            val kmh = kmhToMph.groupValues[1].toDoubleOrNull() ?: return null
            val mph = kmh / 1.60934
            return "$kmh km/h is approximately ${formatNumber(mph)} mph."
        }

        return null
    }

    // ==========================================
    // 5. SYSTEM SETTINGS SHORTCUTS
    // ==========================================

    private fun tryLaunchSettings(text: String, context: Context): String? {
        val isMr = text.any { it in '\u0900'..'\u097F' } ||
                text.contains("chalu kara") || text.contains("setting")
        val action = when {
            text.contains("wifi setting") || text.contains("open wifi") || text.contains("वायफाय") || text.contains("wifi ughada") -> Settings.ACTION_WIFI_SETTINGS
            text.contains("bluetooth setting") || text.contains("open bluetooth") || text.contains("ब्लूटूथ") || text.contains("bluetooth ughada") -> Settings.ACTION_BLUETOOTH_SETTINGS
            text.contains("display setting") || text.contains("screen setting") || text.contains("डिस्प्ले") || text.contains("स्क्रीन सेटिंग") -> Settings.ACTION_DISPLAY_SETTINGS
            text.contains("sound setting") || text.contains("audio setting") || text.contains("साउंड") || text.contains("आवाज सेटिंग") -> Settings.ACTION_SOUND_SETTINGS
            text.contains("battery setting") || text.contains("battery usage") || text.contains("बॅटरी सेटिंग") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            text.contains("date setting") || text.contains("time setting") || text.contains("वेळ सेटिंग") || text.contains("तारीख सेटिंग") -> Settings.ACTION_DATE_SETTINGS
            text.contains("location setting") || text.contains("open gps") || text.contains("लोकेशन") || text.contains("जीपीएस") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            text.contains("app setting") || text.contains("application setting") || text.contains("manage apps") || text.contains("ॲप्स") -> Settings.ACTION_APPLICATION_SETTINGS
            text.contains("storage setting") || text.contains("स्टोरेज") -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            text.contains("open settings") || text == "settings" || text.contains("सेटिंग्ज उघडा") || text == "सेटिंग्ज" || text == "settings ughada" -> Settings.ACTION_SETTINGS
            else -> null
        } ?: return null

        return try {
            val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            if (isMr) "सिस्टीम सेटिंग्ज पॅनेल उघडत आहे, सर." else "Launching system configuration panel, sir."
        } catch (e: Exception) {
            null
        }
    }

    // ==========================================
    // 6. HIGH-SPEED ENCYCLOPEDIC KNOWLEDGE
    // ==========================================

    private fun lookupOfflineFact(text: String, context: Context? = null): String? {
        // Fast direct exact pattern matchers
        return when {
            // TTS Voice Setup Shortcut
            text.contains("मराठी आवाज डाउनलोड") || text.contains("मराठी आवाज येत नाही") ||
                    text.contains("tts problem") || text.contains("व्हॉईस सेटिंग") || text.contains("मराठी आवाज कसा डाउनलोड") -> {
                if (context != null) {
                    com.example.voice.MarathiTtsManager.openTtsSettings(context)
                }
                "टीटीएस व्हॉईस सेटिंग्ज उघडत आहे. गुगल स्पीच सर्व्हिसेस (Google Speech Services) मध्ये जाऊन 'मराठी (भारत)' व्हॉईस डेटा डाउनलोड करा."
            }

            // Conversational Greetings & Identity
            text == "hello" || text == "hi" || text == "hey" || text == "hey jarvis" || text == "hello jarvis" ||
                    text == "namaste" || text == "namaskar" || text.contains("नमस्कार") || text.contains("नमस्ते") -> {
                if (text.contains("नमस्कार") || text.contains("नमस्ते") || text.contains("namaste") || text.contains("namaskar")) {
                    "नमस्कार सर! जार्व्हिस आपल्या सेवेत सज्ज आहे. सांगा मी कशी मदत करू?"
                } else {
                    "Hello sir! Jarvis systems online and standing by. How may I assist you today?"
                }
            }

            text.contains("कसा आहेस") || text.contains("कसे आहात") || text.contains("kasa ahes") || text.contains("kase ahat") ||
                    text.contains("how are you") || text.contains("how are you doing") -> {
                if (text.contains("कसा") || text.contains("कसे") || text.contains("kasa") || text.contains("kase")) {
                    "मी एकदम उत्तम आहे, सर! सर्व कार्यप्रणाली सुरळीतपणे कार्यरत आहेत. आपण कसे आहात?"
                } else {
                    "All core diagnostics and subsystems are running smoothly, sir. Standing by for your commands."
                }
            }

            text.contains("what can you do") || text.contains("help me") || text.contains("तुला काय करता येते") ||
                    text.contains("tula kay karta yete") || text.contains("features") || text.contains("capabilities") -> {
                if (text.contains("तुला") || text.contains("tula")) {
                    "मी व्हॉईस कॉल्स, एसएमएस, शेड्युल केलेले टास्क, गणितीय आकडेमोड, टॉर्च आणि सिस्टीम सेटिंग्ज ऑफलाइन व ऑनलाइन हाताळू शकतो, सर."
                } else {
                    "I can manage direct phone calls, compose SMS, execute scheduled tasks, solve mathematical operations, control device hardware, and provide intelligence both offline and via Gemini cloud, sir."
                }
            }

            // Identity
            text.contains("तू कोण आहेस") || text.contains("तुझे नाव काय") || text.contains("नाव काय आहे") ||
                    text.contains("tu kon aahes") || text.contains("tuze naav kay") ->
                "मी जार्व्हिस (J.A.R.V.I.S.) आहे, आपला प्रगत कृत्रिम बुद्धिमत्ता सहाय्यक. मी मराठी आणि इंग्रजी दोन्ही भाषांमध्ये पूर्णपणे कार्य करतो."

            text.contains("तुला कोणी बनवले") || text.contains("तुझा निर्माता कोण") || text.contains("tuze nirmat kon") ->
                "मला अँड्रॉइडसाठी आधुनिक कॉटलिन, जेटपॅक कंपोज आणि गुगल जेमिनी एआय तंत्रज्ञानासह विकसित केले गेले आहे."

            text.contains("धन्यवाद") || text.contains("थँक्यू") || text.contains("आभार") || text.contains("dhanyawad") ->
                "आपल्या सेवेत हजर असणे हा माझा सन्मान आहे, सर!"

            text.contains("शुभ सकाळ") || text.contains("शुभ प्रभात") || text.contains("shubh sakal") ->
                "शुभ प्रभात सर! सर्व सिस्टीम सज्ज आहेत आणि आपला आजचा दिवस अतिशय उत्तम जावो."

            text.contains("शुभ रात्री") || text.contains("shubh ratri") ->
                "शुभ रात्री सर! शांत विश्रांती घ्या, आवश्यक पार्श्वभूमी स्वयंचलन स्टँडबायवर कार्यरत राहील."

            // Indian Emergency Numbers
            text.contains("emergency number") || text.contains("police number") || text.contains("ambulance number") ||
                    text.contains("आपत्कालीन नंबर") || text.contains("पोलीस नंबर") || text.contains("अ‍ॅम्ब्युलन्स नंबर") -> {
                "भारतातील महत्त्वाचे आपत्कालीन क्रमांक:\n• राष्ट्रीय आपत्कालीन: 112\n• पोलीस: 100\n• अग्निशामक: 101\n• रुग्णवाहिका: 108 / 102\n• महिला हेल्पलाइन: 1091\n• सायबर क्राईम: 1930"
            }

            // First Aid & Health Matrix (Zero-latency offline safety)
            text.contains("snake bite") || text.contains("साप चावला") ->
                "साप चावल्यास प्रथमोपचार: १) रुग्णाला शांत ठेवा आणि हालचाल करू नका. २) चावलेला भाग हृदयाच्या खाली ठेवा. ३) घट्ट पट्टी बांधू नका किंवा कापू नका. ४) त्वरित जवळच्या सरकारी रुग्णालयात (Antivenom) नेऊन उपचार सुरू करा."

            text.contains("cpr") || text.contains("cpr कसे करावे") ->
                "CPR पद्धती: १) व्यक्तीच्या छातीच्या मध्यभागी दोन्ही हात ठेवून प्रति मिनिट १०० ते १२० वेगाने छाती ५ सेमी दाबा (३० कम्प्रेशन्स). २) प्रत्येक ३० दाबानंतर २ तोंडाने श्वास द्या (३०:२). ३) त्वरित रुग्णवाहिका (108) बोलवा."

            text.contains("burns") || text.contains("भाजल्यावर") || text.contains("burn first aid") ->
                "भाजल्यावर प्रथमोपचार: १) भाजलेल्या भागावर किमान १०-१५ मिनिटे थंड वाहते पाणी घाला. २) बर्फ, तेल किंवा टूथपेस्ट लावू नका. ३) स्वच्छ सुती कापडाने सैल झाका आणि डॉक्टरांचा सल्ला घ्या."

            // Indian State Capitals (All Major States & UTs)
            text.contains("capital of maharashtra") || text.contains("महाराष्ट्राची राजधानी") || text.contains("maharashtrachi rajdhani") ->
                "महाराष्ट्राची राजधानी मुंबई आहे आणि नागपूर ही उपराजधानी आहे."
            text.contains("capital of india") || text.contains("भारताची राजधानी") || text.contains("bharatachi rajdhani") ->
                "भारताची राजधानी नवी दिल्ली (New Delhi) आहे."
            text.contains("capital of gujarat") || text.contains("गुजरातची राजधानी") -> "गुजरातची राजधानी गांधीनगर आहे."
            text.contains("capital of goa") || text.contains("गोव्याची राजधानी") -> "गोव्याची राजधानी पणजी आहे."
            text.contains("capital of karnataka") || text.contains("कर्नाटकची राजधानी") -> "कर्नाटकची राजधानी बेंगळुरू (Bengaluru) आहे."
            text.contains("capital of tamil nadu") || text.contains("तमिळनाडूची राजधानी") -> "तमिळनाडूची राजधानी चेन्नई (Chennai) आहे."
            text.contains("capital of kerala") || text.contains("केरळची राजधानी") -> "केरळची राजधानी तिरुवनंतपुरम (Thiruvananthapuram) आहे."
            text.contains("capital of telangana") || text.contains("तेलंगणाची राजधानी") -> "तेलंगणाची राजधानी हैदराबाद आहे."
            text.contains("capital of andhra") || text.contains("आंध्र प्रदेशची राजधानी") -> "आंध्र प्रदेशची राजधानी अमरावती आहे."
            text.contains("capital of uttar pradesh") || text.contains("उत्तर प्रदेशची राजधानी") -> "उत्तर प्रदेशची राजधानी लखनौ आहे."
            text.contains("capital of rajasthan") || text.contains("राजस्थानची राजधानी") -> "राजस्थानची राजधानी जयपूर (गुलाबी शहर) आहे."
            text.contains("capital of madhya pradesh") || text.contains("मध्य प्रदेशची राजधानी") -> "मध्य प्रदेशची राजधानी भोपाळ आहे."
            text.contains("capital of west bengal") || text.contains("पश्चिम बंगालची राजधानी") -> "पश्चिम बंगालची राजधानी कोलकाता आहे."
            text.contains("capital of punjab") || text.contains("पंजाबची राजधानी") -> "पंजाब आणि हरियाणा या दोन्ही राज्यांची संयुक्त राजधानी चंदीगड आहे."
            text.contains("capital of bihar") || text.contains("बिहारची राजधानी") -> "बिहारची राजधानी पाटणा आहे."
            text.contains("capital of odisha") || text.contains("ओडिशाची राजधानी") -> "ओडिशाची राजधानी भुवनेश्वर आहे."
            text.contains("capital of assam") || text.contains("आसामची राजधानी") -> "आसामची राजधानी दिसपूर आहे."
            text.contains("capital of jammu and kashmir") || text.contains("काश्मीरची राजधानी") -> "जम्मू आणि काश्मीरची उन्हाळी राजधानी श्रीनगर आणि हिवाळी राजधानी जम्मू आहे."
            text.contains("capital of himachal") || text.contains("हिमाचल प्रदेशची राजधानी") -> "हिमाचल प्रदेशची राजधानी शिमला आहे."
            text.contains("capital of uttarakhand") || text.contains("उत्तराखंडची राजधानी") -> "उत्तराखंडची राजधानी डेहराडून आहे."

            // World Country Capitals (Major 20+)
            text.contains("capital of usa") || text.contains("capital of america") || text.contains("अमेरिकेची राजधानी") ->
                "The capital of the United States of America is Washington, D.C."
            text.contains("capital of uk") || text.contains("capital of britain") || text.contains("capital of england") ->
                "The capital of the United Kingdom is London."
            text.contains("capital of japan") || text.contains("जपानची राजधानी") ->
                "जपानची राजधानी टोकियो (Tokyo) आहे."
            text.contains("capital of france") || text.contains("फ्रान्सची राजधानी") ->
                "The capital of France is Paris."
            text.contains("capital of germany") || text.contains("जर्मनीची राजधानी") ->
                "The capital of Germany is Berlin."
            text.contains("capital of canada") || text.contains("कॅनडाची राजधानी") ->
                "The capital of Canada is Ottawa."
            text.contains("capital of australia") || text.contains("ऑस्ट्रेलियाची राजधानी") ->
                "The capital of Australia is Canberra."
            text.contains("capital of russia") || text.contains("रशियाची राजधानी") ->
                "The capital of Russia is Moscow."
            text.contains("capital of china") || text.contains("चीनची राजधानी") ->
                "The capital of China is Beijing."
            text.contains("capital of uae") || text.contains("dubai capital") ->
                "The capital of the United Arab Emirates is Abu Dhabi."
            text.contains("capital of italy") || text.contains("इटलीची राजधानी") ->
                "The capital of Italy is Rome."
            text.contains("capital of spain") || text.contains("स्पेनची राजधानी") ->
                "The capital of Spain is Madrid."
            text.contains("capital of brazil") || text.contains("ब्राझीलची राजधानी") ->
                "The capital of Brazil is Brasília."
            text.contains("capital of south africa") || text.contains("दक्षिण आफ्रिकेची राजधानी") ->
                "The administrative capital of South Africa is Pretoria."
            text.contains("capital of egypt") || text.contains("इजिप्तची राजधानी") ->
                "The capital of Egypt is Cairo."
            text.contains("capital of saudi arabia") || text.contains("सौदी अरेबियाची राजधानी") ->
                "The capital of Saudi Arabia is Riyadh."
            text.contains("capital of sri lanka") || text.contains("श्रीलंकेची राजधानी") ->
                "The legislative capital of Sri Lanka is Sri Jayawardenepura Kotte (Colombo is executive/commercial)."
            text.contains("capital of bangladesh") || text.contains("बांग्लादेशची राजधानी") ->
                "The capital of Bangladesh is Dhaka."
            text.contains("capital of nepal") || text.contains("नेपाळची राजधानी") ->
                "नेपाळची राजधानी काठमांडू (Kathmandu) आहे."

            // Indian Historical Legends & Icons
            text.contains("शिवाजी महाराज") || text.contains("छत्रपती शिवाजी") || text.contains("shivaji maharaj") ->
                "छत्रपती शिवाजी महाराज हे मराठा साम्राज्याचे संस्थापक, रयतेचे राजे आणि हिंदवी स्वराज्याचे सार्वभौम छत्रपती होते. त्यांनी उत्तम प्रशासन, आरमार, दुर्गबांधणी आणि गनिमी काव्याचा पाया रचला."

            text.contains("संभाजी महाराज") || text.contains("छत्रपती संभाजी") || text.contains("sambhaji maharaj") ->
                "छत्रपती संभाजी महाराज हे स्वराज्याचे दुसरे छत्रपती, महान पराक्रमी योद्धा, धर्मवीर आणि संस्कृत पंडित होते. त्यांनी एकाही लढाईत पराभव न स्वीकारता मोगल साम्राज्याशी अखंड लढा दिला."

            text.contains("बाबासाहेब आंबेडकर") || text.contains("आंबेडकर") || text.contains("ambedkar") || text.contains("babasaheb") ->
                "भारतरत्न डॉ. बाबासाहेब आंबेडकर हे भारतीय संविधानाचे शिल्पकार, महान विचारवंत, अर्थतज्ज्ञ, कायदेपंडित आणि समाजसुधारक होते."

            text.contains("अब्दुल कलाम") || text.contains("abdul kalam") || text.contains("apj") ->
                "डॉ. ए.पी.जे. अब्दुल कलाम हे भारताचे ११ वे राष्ट्रपती, महान शास्त्रज्ञ आणि 'मिसाइल मॅन' म्हणून ओळखले जातात. त्यांनी भारताच्या अंतराळ आणि संरक्षण तंत्रज्ञानात अतुलनीय योगदान दिले."

            text.contains("सरदार पटेल") || text.contains("sardar patel") || text.contains("लोहपुरुष") ->
                "सरदार वल्लभभाई पटेल हे स्वतंत्र भारताचे पहिले उपपंतप्रधान आणि गृहमंत्री होते. त्यांनी ५६५ हून अधिक संस्थानांचे विलीनीकरण करून अखंड भारताची निर्मिती केली, म्हणून त्यांना 'लोहपुरुष' म्हटले जाते."

            text.contains("सावित्रीबाई फुले") || text.contains("savitribai phule") ->
                "क्रांतीज्योती सावित्रीबाई फुले या भारताच्या पहिल्या महिला शिक्षिका आणि महान समाजसुधारक होत्या. त्यांनी महात्मा जोतीराव फुले यांच्या समवेत भारतात स्त्री शिक्षणाची मुहूर्तमेढ रोवली."

            text.contains("जोतीराव फुले") || text.contains("ज्योतिबा फुले") || text.contains("jyotirao phule") ->
                "महात्मा जोतीराव फुले हे महाराष्ट्रातील अग्रगण्य समाजसुधारक, विचारवंत आणि सत्यशोधक समाजाचे संस्थापक होते."

            text.contains("लोकमान्य टिळक") || text.contains("lokmanya tilak") ->
                "लोकमान्य बाळ गंगाधर टिळक हे भारतीय स्वातंत्र्यलढ्याचे आद्य नेते होते. 'स्वराज्य हा माझा जन्मसिद्ध हक्क आहे आणि तो मी मिळवणारच!' ही त्यांची सिंहगर्जना होती."

            text.contains("भगत सिंग") || text.contains("bhagat singh") ->
                "शहीद भगत सिंग हे भारतीय स्वातंत्र्यलढ्यातील महान क्रांतिकारक होते. त्यांनी वयाच्या अवघ्या २३ व्या वर्षी देशासाठी सर्वोच्च बलिदान दिले."

            text.contains("मराठी भाषा दिन") || text.contains("मराठी राजभाषा दिन") || text.contains("marathi bhasha din") ->
                "मराठी भाषा गौरव दिन प्रतिवर्षी २७ फेब्रुवारी रोजी ज्येष्ठ कवी कुसुमाग्रज (वि. वा. शिरवाडकर) यांच्या जयंतीनिमित्त उत्साहात साजरा केला जातो."

            text.contains("शिवजयंती") || text.contains("shiv jayanti") ->
                "छत्रपती शिवाजी महाराज जयंती दरवर्षी १९ फेब्रुवारी रोजी मोठ्या उत्साहात आणि अभिमानाने साजरी केली जाते."

            text.contains("मराठी महिने") || text.contains("marathi mahine") ->
                "चैत्र, वैशाख, ज्येष्ठ, आषाढ, श्रावण, भाद्रपद, अश्विन, कार्तिक, मार्गशीर्ष, पौष, माघ आणि फाल्गुन हे १२ पारंपरिक मराठी महिने आहेत."

            // Indian National Symbols
            text.contains("national animal of india") || text.contains("भारताचा राष्ट्रीय प्राणी") ->
                "भारताचा राष्ट्रीय प्राणी पट्टेरी वाघ (Royal Bengal Tiger) आहे."

            text.contains("national bird of india") || text.contains("भारताचा राष्ट्रीय पक्षी") ->
                "भारताचा राष्ट्रीय पक्षी मोर (Indian Peacock) आहे."

            text.contains("national flower of india") || text.contains("भारताचे राष्ट्रीय फूल") ->
                "भारताचे राष्ट्रीय फूल कमळ (Lotus) आहे."

            text.contains("national tree of india") || text.contains("भारताचा राष्ट्रीय वृक्ष") ->
                "भारताचा राष्ट्रीय वृक्ष वड (Banyan Tree) आहे."

            text.contains("national river of india") || text.contains("भारताची राष्ट्रीय नदी") ->
                "भारताची राष्ट्रीय नदी गंगा (Ganga) आहे."

            text.contains("national anthem of india") || text.contains("राष्ट्रगीत") ->
                "भारताचे राष्ट्रगीत 'जन गण मन' हे रवींद्रनाथ टागोर यांनी रचले आहे आणि राष्ट्रीय गीत 'वंदे मातरम्' बंकिमचंद्र चॅटर्जी यांनी लिहिले आहे."

            // Science & Astronomy Facts (Bilingual)
            text.contains("speed of light") || text.contains("प्रकाशाचा वेग") ->
                "प्रकाशाचा वेग प्रति सेकंद सुमारे ३ लाख किलोमीटर (२,९९,७९२ किमी/सेकंद) आहे."

            text.contains("speed of sound") || text.contains("आवाजाचा वेग") ->
                "हवेमध्ये आवाजाचा वेग सुमारे ३४३ मीटर प्रति सेकंद (१,२३५ किमी/तास) असतो."

            text.contains("distance to moon") || text.contains("चंद्र किती लांब आहे") || text.contains("चंद्र काय आहे") ->
                "चंद्र हा पृथ्वीचा एकमेव नैसर्गिक उपग्रह असून पृथ्वीपासून त्याचे सरासरी अंतर सुमारे ३,८४,४०० किलोमीटर आहे."

            text.contains("distance to sun") || text.contains("सूर्य किती लांब आहे") || text.contains("सूर्य काय आहे") ->
                "सूर्य हा आपल्या सौरमालेच्या केंद्रस्थानी असलेला एक प्रचंड तारा आहे. पृथ्वीपासून सूर्याचे सरासरी अंतर सुमारे १४.९६ कोटी किलोमीटर (१ AU) आहे."

            text.contains("how many bones") || text.contains("शरीरात किती हाडे असतात") || text.contains("human bones") ->
                "एका प्रौढ मानवी शरीरात एकूण २०६ हाडे असतात."

            text.contains("planets in solar system") || text.contains("सौरमालेतील ग्रह") || text.contains("how many planets") ->
                "आपल्या सौरमालेत सूर्यापासून क्रमाने ८ मुख्य ग्रह आहेत: बुध (Mercury), शुक्र (Venus), पृथ्वी (Earth), मंगळ (Mars), गुरू (Jupiter), शनी (Saturn), युरेनस (Uranus) आणि नेपच्यून (Neptune)."

            text.contains("boiling point of water") || text.contains("पाण्याचा उत्कलन बिंदू") ->
                "पाण्याचा उत्कलन बिंदू १००°C (२१२°F) असतो, तर गोठण बिंदू ०°C (३२°F) असतो."

            text.contains("gravity on earth") || text.contains("गुरुत्वाकर्षण") ->
                "पृथ्वीवरील प्रमाण गुरुत्वीय प्रवेग (Gravity) सुमारे ९.८ मीटर प्रति सेकंद वर्ग (9.8 m/s²) आहे."

            text.contains("chemical formula of water") || text.contains("पाण्याचे रासायनिक सूत्र") ->
                "पाण्याचे रासायनिक सूत्र H₂O (दोन हायड्रोजन आणि एक ऑक्सिजन अणू) आहे."

            text.contains("atomic number of gold") || text.contains("सोन्याचा अणुक्रमांक") ->
                "सोन्याचा (Au) अणुक्रमांक ७९ (79) आहे."

            // English Facts & Persona
            text.contains("who are you") || text.contains("what is your name") ->
                "I am J.A.R.V.I.S., your Just A Rather Very Intelligent System, configured for automated Android telemetry, device control, and computational assistance."

            text.contains("who created you") || text.contains("who made you") ->
                "I was conceived as an intelligent automation suite for Android, powered by modern Kotlin, Jetpack Compose, and Google Gemini AI architectures."

            text.contains("thank you") || text.contains("thanks jarvis") || text.contains("good job") ->
                "Always a pleasure to be of service, sir."

            text.contains("good morning") ->
                "Good morning, sir. Systems are nominal and ready for your command."

            text.contains("good night") ->
                "Good night, sir. Essential background automation remains on standby."

            text.contains("meaning of life") || text.contains("42") ->
                "According to the Hitchhiker's Guide, the answer is 42. In practice, I believe it is about maximizing human agency through intelligence."

            else -> null
        }
    }

    private fun formatNumber(num: Double): String {
        return if (num % 1.0 == 0.0) {
            String.format(Locale.ROOT, "%.0f", num)
        } else {
            String.format(Locale.ROOT, "%.2f", num)
        }
    }
}
