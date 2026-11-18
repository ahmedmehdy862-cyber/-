package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicCountTest {

    @Test fun singularDualPlural() {
        assertEquals("مهمة واحدة", taskCount(1))
        assertEquals("مهمتان", taskCount(2))
        assertEquals("3 مهام", taskCount(3))
        assertEquals("10 مهام", taskCount(10))
        assertEquals("11 مهمة", taskCount(11))
        assertEquals("100 مهمة", taskCount(100))
        assertEquals("لا مهام", taskCount(0))
    }

    @Test fun prefixKeepsTheVerbOutsideTheNumber() {
        assertEquals("عندك مهمة واحدة", taskCountWith(1, "عندك"))
        assertEquals("عندك مهمتان", taskCountWith(2, "عندك"))
        assertEquals("عندك 5 مهام", taskCountWith(5, "عندك"))
        assertEquals("بقيت لليوم 12 مهمة", taskCountWith(12, "بقيت لليوم"))
    }

    @Test fun minutesFollowTheSameRule() {
        assertEquals("دقيقة واحدة", minuteCount(1))
        assertEquals("دقيقتان", minuteCount(2))
        assertEquals("5 دقائق", minuteCount(5))
        assertEquals("40 دقيقة", minuteCount(40))
    }
}
