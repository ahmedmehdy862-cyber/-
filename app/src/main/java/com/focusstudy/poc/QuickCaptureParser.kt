package com.focusstudy.poc

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * تحليل جملة عربية قصيرة إلى مهمة، بدون إنترنت وبدون أي نموذج ذكاء اصطناعي.
 * مثال: "راجع الفيزياء بكرة 5 العصر ساعة عاجل"
 */
data class ParsedCapture(
    val title: String,
    val date: LocalDate? = null,
    val hour: Int? = null,
    val minute: Int = 0,
    val durationMinutes: Int? = null,
    /** يطابق TaskPriority.level: 1 متوسطة، 2 عالية، 3 عاجلة. null = لم يُذكر. */
    val priorityLevel: Int? = null
)

object QuickCaptureParser {

    private val weekdays = mapOf(
        "السبت" to DayOfWeek.SATURDAY, "الأحد" to DayOfWeek.SUNDAY, "الاحد" to DayOfWeek.SUNDAY,
        "الاثنين" to DayOfWeek.MONDAY, "الإثنين" to DayOfWeek.MONDAY, "الثلاثاء" to DayOfWeek.TUESDAY,
        "الأربعاء" to DayOfWeek.WEDNESDAY, "الاربعاء" to DayOfWeek.WEDNESDAY,
        "الخميس" to DayOfWeek.THURSDAY, "الجمعة" to DayOfWeek.FRIDAY
    )
    private val tomorrow = setOf("بكرة", "بكره", "غدا", "غدًا", "غداً", "غدّا")
    private val pmWords = setOf("العصر", "المغرب", "العشاء", "مساء", "مساءً", "مساءا", "م", "بالليل", "ليلا")
    private val amWords = setOf("الصبح", "صباحا", "صباحًا", "صباحاً", "ص", "الفجر")
    private val noonWords = setOf("الظهر", "ظهرا", "ظهرًا")
    private val minuteUnits = setOf("دقيقة", "دقيقه", "دقايق", "دقائق")
    private val hourUnits = setOf("ساعة", "ساعه", "ساعات", "ساعتين")
    private val urgent = setOf("عاجل", "ضروري", "ضروري جدا")
    private val important = setOf("مهم", "مهمة")
    private val fillers = setOf("في", "ف", "على")

    private fun normalizeDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(
            when (ch) {
                in '٠'..'٩' -> ('0' + (ch - '٠'))
                in '۰'..'۹' -> ('0' + (ch - '۰'))
                else -> ch
            }
        )
        return sb.toString()
    }

    private fun num(t: String): Int? = t.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }?.toIntOrNull()

    private fun applyPeriod(hour: Int, period: String?): Int = when {
        period == null -> if (hour in 1..6) hour + 12 else hour
        period in pmWords -> if (hour < 12) hour + 12 else hour
        period in noonWords -> if (hour in 1..6) hour + 12 else hour
        period in amWords -> if (hour == 12) 0 else hour
        else -> hour
    }

    fun parse(text: String, today: LocalDate): ParsedCapture {
        val tokens = normalizeDigits(text.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val keep = mutableListOf<String>()
        var date: LocalDate? = null
        var hour: Int? = null
        var minute = 0
        var duration: Int? = null
        var priority: Int? = null

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            val next2 = tokens.getOrNull(i + 2)
            when {
                t == "بعد" && next != null && next in tomorrow -> { date = today.plusDays(2); i += 2 }
                t == "اليوم" -> { date = today; i++ }
                t in tomorrow -> { date = today.plusDays(1); i++ }
                t in weekdays -> {
                    val target = weekdays.getValue(t)
                    var diff = (target.value - today.dayOfWeek.value + 7) % 7
                    if (diff == 0) diff = 7
                    date = today.plusDays(diff.toLong()); i++
                }
                t == "الساعة" && next != null && num(next) != null -> {
                    val h = num(next)!!
                    val period = next2?.takeIf { it in pmWords || it in amWords || it in noonWords }
                    hour = applyPeriod(h, period)
                    i += if (period != null) 3 else 2
                }
                num(t) != null && next != null && next in minuteUnits -> {
                    duration = (duration ?: 0) + num(t)!!; i += 2
                }
                num(t) != null && next != null && (next == "ساعة" || next == "ساعات" || next == "ساعه") -> {
                    duration = (duration ?: 0) + num(t)!! * 60; i += 2
                }
                num(t) != null && next != null && (next in pmWords || next in amWords || next in noonWords) -> {
                    hour = applyPeriod(num(t)!!, next); i += 2
                }
                (t == "نص" || t == "نصف") && (next == "ساعة" || next == "ساعه") -> {
                    duration = (duration ?: 0) + 30; i += 2
                }
                t == "ساعتين" -> { duration = (duration ?: 0) + 120; i++ }
                t == "ساعة" || t == "ساعه" -> { duration = (duration ?: 0) + 60; i++ }
                t in urgent -> { priority = 3; i++ }
                t in important -> { priority = maxOf(priority ?: 0, 2); i++ }
                t in fillers && (keep.isEmpty() || i == tokens.lastIndex) -> i++
                else -> { keep += t; i++ }
            }
        }
        if (hour != null && hour !in 0..23) hour = null
        return ParsedCapture(
            title = keep.joinToString(" ").trim(),
            date = date,
            hour = hour,
            minute = minute,
            durationMinutes = duration,
            priorityLevel = priority
        )
    }
}
