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

    // ---- additional coverage added while verifying the bundle against the shipped parser
    @Test fun weekdayNameGoesToNextOccurrence() {
        val r = p("مراجعة الرياضيات الخميس")
        // الأحد 2026-11-15 -> الخميس 2026-11-19
        assertEquals(java.time.LocalDate.of(2026, 11, 19), r.date)
        assertEquals("مراجعة الرياضيات", r.title)
    }

    @Test fun weekdaySameAsTodayMovesAWeek() {
        val r = p("جلسة الأحد")
        assertEquals(today.plusDays(7), r.date)
    }

    @Test fun persianDigitsAreUnderstood() {
        assertEquals(45, p("حل تمارين ۴۵ دقيقة").durationMinutes)
    }

    @Test fun hoursUnitConvertsToMinutes() {
        assertEquals(120, p("قراءة ساعتين").durationMinutes)
    }

    @Test fun noonPeriodShiftsAfternoonHours() {
        assertEquals(14, p("محاضرة 2 الظهر").hour)
    }

    @Test fun morningPeriodKeepsEarlyHour() {
        assertEquals(7, p("جري 7 الصبح").hour)
    }

    @Test fun midnightIsZero() {
        assertEquals(0, p("مراجعة 12 ص").hour)
    }

    @Test fun bareNumberStaysInTheTitle() {
        // A lone number is ambiguous, so the parser leaves it as text instead of guessing a clock.
        val r = p("مكالمة 4")
        assertNull(r.hour)
        assertEquals("مكالمة 4", r.title)
    }

    @Test fun outOfRangeHourIsDropped() {
        assertNull(p("اجتماع 27").hour)
    }

    @Test fun importantWordIsHighPriority() {
        assertEquals(2, p("تسليم مهم").priorityLevel)
    }

    @Test fun urgentBeatsImportant() {
        assertEquals(3, p("تسليم عاجل مهم").priorityLevel)
    }

    @Test fun plainSentenceHasNoDetails() {
        val r = p("اقرأ صفحتين من الكتاب")
        assertEquals("اقرأ صفحتين من الكتاب", r.title)
        assertNull(r.date); assertNull(r.hour); assertNull(r.durationMinutes); assertNull(r.priorityLevel)
    }

    @Test fun leadingFillerIsDroppedFromTitle() {
        assertEquals("الكتاب اقرأ", p("في الكتاب اقرأ").title)
    }

    @Test fun minuteAndHourDurationsAddUp() {
        assertEquals(90, p("تدريب ساعة 30 دقيقة").durationMinutes)
    }
    // ---- routines and relative times: what a personal organizer needs every day
    @Test fun dailyRoutinePhrase() {
        val r = p("كل يوم رياضة الصبح")
        assertEquals(Recurrence.DAILY, r.recurrence)
        assertEquals("رياضة", r.title)
        assertEquals(8, r.hour)
    }

    @Test fun weeklyRoutinePhrase() {
        val r = p("كل أسبوع تنظيف البيت")
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals("تنظيف البيت", r.title)
    }

    @Test fun monthlyRoutinePhrase() {
        val r = p("كل شهر دفع الإيجار")
        assertEquals(Recurrence.MONTHLY, r.recurrence)
        assertEquals("دفع الإيجار", r.title)
    }

    @Test fun weeklyOnANamedDay() {
        val r = p("كل السبت مباراة")
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals(LocalDate.of(2026, 11, 21), r.date)
        assertEquals("مباراة", r.title)
    }

    @Test fun routineWordAlone() {
        assertEquals(Recurrence.DAILY, p("صلاة يوميا").recurrence)
    }

    @Test fun inAnHourIsRelativeToNow() {
        val r = QuickCaptureParser.parse("اتصل بأمي بعد ساعة", today, java.time.LocalTime.of(9, 0))
        assertEquals(today, r.date)
        assertEquals(10, r.hour)
        assertEquals(0, r.minute)
        assertEquals("اتصل بأمي", r.title)
    }

    @Test fun inTwoHoursUsesTwoHours() {
        val r = QuickCaptureParser.parse("اتصل بأمي بعد ساعتين", today, java.time.LocalTime.of(11, 0))
        assertEquals(today, r.date)
        assertEquals(13, r.hour)
        assertEquals(0, r.minute)
        assertEquals("اتصل بأمي", r.title)
    }

    @Test fun relativeHalfHourCrossesMidnight() {
        val r = QuickCaptureParser.parse("مكالمة بعد نص ساعة", today, java.time.LocalTime.of(23, 40))
        assertEquals(today.plusDays(1), r.date)
        assertEquals(0, r.hour)
        assertEquals(10, r.minute)
    }

    @Test fun morningWithTomorrowUsesTheDefaultHour() {
        val r = p("بكرة الصبح جري")
        assertEquals(today.plusDays(1), r.date)
        assertEquals(8, r.hour)
        assertEquals("جري", r.title)
    }

    @Test fun plainTaskIsNotARoutine() {
        assertEquals(Recurrence.NONE, p("اشتري حليب").recurrence)
    }
}
