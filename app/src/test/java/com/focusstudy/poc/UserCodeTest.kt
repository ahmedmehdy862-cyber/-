package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Test

class UserCodeTest {

    @Test fun codeIsTheFirstEightCharactersGrouped() {
        assertEquals("3F2A-9C1E", UserIdentity.codeOf("3f2a9c1e-77aa-4c2f-9f1a-1234567890ab"))
    }

    @Test fun codeIgnoresDashesAndCase() {
        assertEquals("ABC1-2345", UserIdentity.codeOf("ABC12345"))
        assertEquals("3F2A-9C1E", UserIdentity.codeOf("3F2A9C1E"))
    }

    @Test fun shortIdStaysReadable() {
        assertEquals("AB12", UserIdentity.codeOf("ab12"))
    }
}
