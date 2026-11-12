package com.example.iot

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTest {
    @Test fun simulationDistinguishesUnknownRevokedAndActive() {
        assertEquals("RECHAZADO" to "DESCONOCIDA", AccessPolicy.decision(false, false))
        assertEquals("RECHAZADO" to "DESCONOCIDA", AccessPolicy.decision(false, true))
        assertEquals("RECHAZADO" to "REVOCADA", AccessPolicy.decision(true, false))
        assertEquals("PERMITIDO" to "ACTIVA", AccessPolicy.decision(true, true))
    }
    @Test fun normalizesRfidFormats() {
        assertEquals("AABBCCDD", AccessPolicy.normalizeUid("aa:bb cc-dd"))
        assertEquals("04AABBCCDDEEFF", AccessPolicy.normalizeUid("04 AA BB CC DD EE FF"))
        assertEquals("00112233445566778899", AccessPolicy.normalizeUid("00112233445566778899"))
    }
    @Test fun rejectsMalformedUids() {
        listOf("", "AABB", "GG112233", "AABB/CCD", "AABBCCDDEEFF", "AABBCCDD\n").forEach {
            assertNull(AccessPolicy.normalizeUid(it))
        }
    }
    @Test fun masksCredential() {
        assertEquals("•••• CCDD", AccessPolicy.maskedUid("AABBCCDD"))
    }
    @Test fun heartbeatExpiresAndRejectsFutureDates() {
        assertTrue(AccessPolicy.isOnline(40_000L, 100_000L))
        assertFalse(AccessPolicy.isOnline(39_999L, 100_000L))
        assertFalse(AccessPolicy.isOnline(100_001L, 100_000L))
        assertFalse(AccessPolicy.isOnline(null, 100_000L))
    }
}
