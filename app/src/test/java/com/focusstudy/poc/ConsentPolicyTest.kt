package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentPolicyTest {

    @Test fun firstGrantIsNotRevocation() {
        assertEquals(ConsentPolicy.Change.NEWLY_GRANTED, ConsentPolicy.classify(null, true))
    }

    @Test fun neverGrantedStaysQuiet() {
        assertEquals(ConsentPolicy.Change.NEVER_GRANTED, ConsentPolicy.classify(null, false))
        assertNull(ConsentPolicy.hint(ConsentPolicy.Change.NEVER_GRANTED))
    }

    @Test fun stillGrantedNeedsNothing() {
        assertEquals(ConsentPolicy.Change.KEPT, ConsentPolicy.classify(true, true))
        assertNull(ConsentPolicy.hint(ConsentPolicy.Change.KEPT))
        assertFalse(ConsentPolicy.wasRevokedBySystem(ConsentPolicy.Change.KEPT))
    }

    @Test fun grantedThenMissingIsExplainedAsSystemRevocation() {
        val change = ConsentPolicy.classify(true, false)
        assertEquals(ConsentPolicy.Change.REVOKED, change)
        assertTrue(ConsentPolicy.wasRevokedBySystem(change))
        assertNotNull(ConsentPolicy.hint(change))
    }

    @Test fun dismissedPermissionIsNotReportedAsRevoked() {
        assertEquals(ConsentPolicy.Change.NEVER_GRANTED, ConsentPolicy.classify(false, false))
        assertFalse(ConsentPolicy.wasRevokedBySystem(ConsentPolicy.Change.NEVER_GRANTED))
    }

    @Test fun regrantedAfterRevocationCountsAsKept() {
        assertEquals(ConsentPolicy.Change.KEPT, ConsentPolicy.classify(true, true))
    }

    @Test fun snapshotMissingListCoversEveryDeniedPermission() {
        val snapshot = PermissionSnapshot(
            notifications = true,
            accessibility = false,
            vpn = true,
            battery = false,
            dnd = true,
            exactAlarms = false
        )
        assertEquals(
            setOf(ConsentKey.ACCESSIBILITY, ConsentKey.BATTERY, ConsentKey.EXACT_ALARMS),
            snapshot.missing
        )
        assertTrue(snapshot.asMap()[ConsentKey.VPN] == true)
    }

    @Test fun emptySnapshotReportsAll() {
        val snapshot = PermissionSnapshot(false, false, false, false, false, false)
        assertEquals(ConsentKey.entries.toSet(), snapshot.missing)
    }

    @Test fun descriptionAddsHintOnlyForRevocation() {
        val base = "يخفي التطبيق المقفل."
        assertEquals(base, consentDescription(base, ConsentPolicy.Change.NEVER_GRANTED))
        assertEquals(base, consentDescription(base, null))
        assertTrue(consentDescription(base, ConsentPolicy.Change.REVOKED).startsWith(base))
        assertTrue(consentDescription(base, ConsentPolicy.Change.REVOKED).length > base.length)
    }

    @Test fun actionLabelIsRegrantWhenItWasRevoked() {
        assertEquals("إعادة التفعيل", consentActionLabel(ConsentPolicy.Change.REVOKED))
        assertEquals("منح", consentActionLabel(ConsentPolicy.Change.NEVER_GRANTED))
        assertEquals("منح", consentActionLabel(null))
    }
}
