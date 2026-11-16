package com.focusstudy.poc

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickCaptureParserTest {
    private val today = LocalDate.of(2026, 11, 15) // الأحد
    private fun p(s: String) = QuickCaptureParser.parse(s, today)

    @Test fun fullSentence() {
        val r = p("راجع الفيزياء بكرة 5 العصر ساعة عاجل")
        assertEquals("راجع الفيزياء", r.title)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(17, r.hour); assertEquals(60, r.durationMinutes); assertEquals(3, r.priorityLevel)
    }

    @Test fun clockWithPeriodAndHalfHour() {
        val r = p("ذاكر كيمياء الساعة 8 مساء نص ساعة")
        assertEquals("ذاكر كيمياء", r.title); assertEquals(20, r.hour); assertEquals(30, r.durationMinutes)
    }

    @Test fun arabicIndicDigits() {
        val r = p("مذاكرة ٣٠ دقيقة")
        assertEquals("مذاكرة", r.title); assertEquals(30, r.durationMinutes)
    }

    @Test fun todayWord() = assertEquals(today, p("اتصل بالدكتور اليوم").date)
    @Test fun dayAfterTomorrow() = assertEquals(today.plusDays(2), p("تسليم التقرير بعد بكرة").date)

    @Test fun weekdayNextOccurrence() {
        val r = p("مراجعة الخميس 9 الصبح ساعتين")
        assertEquals(LocalDate.of(2026, 11, 19), r.date); assertEquals(9, r.hour); assertEquals(120, r.durationMinutes)
    }

    @Test fun sameWeekdayMeansNextWeek() = assertEquals(today.plusDays(7), p("اجتماع الأحد").date)
    @Test fun bareHourPeriod() = assertEquals(17, p("مذاكرة 5 العصر").hour)
    @Test fun hoursUnit() = assertEquals(120, p("حل واجب 2 ساعات").durationMinutes)
    @Test fun importantMapsToHigh() = assertEquals(2, p("مهم مراجعة الباب الأول").priorityLevel)
    @Test fun plainTitle() {
        val r = p("اشتري كتب")
        assertEquals("اشتري كتب", r.title); assertNull(r.date); assertNull(r.hour); assertNull(r.durationMinutes); assertNull(r.priorityLevel)
    }
    @Test fun noonHour() = assertEquals(12, p("غداء الساعة 12 ظهرا").hour)
    @Test fun morningTwelveIsMidnight() = assertEquals(0, p("نوم الساعة 12 صباحا").hour)
    @Test fun emptyInput() = assertEquals("", p("   ").title)
    @Test fun saturdayFromSunday() = assertEquals(LocalDate.of(2026, 11, 21), p("درس السبت").date)
    @Test fun bareSmallHourDefaultsToAfternoon() = assertEquals(15, p("اجتماع الساعة 3").hour)
}
