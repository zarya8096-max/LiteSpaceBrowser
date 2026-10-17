package org.litespace.browser

import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.domain.RuntimePermissionGate

class RuntimePermissionGateTest {
    @Test fun cancellationAndPartialResultsNeverGrant() {
        for (result in listOf(emptyMap(), mapOf("camera" to true), mapOf("other" to true))) {
            val gate = RuntimePermissionGate()
            var granted: Boolean? = null
            gate.begin(setOf("camera", "microphone")) { granted = it }
            gate.resolve(result)
            assertEquals(false, granted)
        }
    }
    @Test fun oldResultCannotAuthorizeNewPage() {
        val gate = RuntimePermissionGate()
        val old = mutableListOf<Boolean>()
        val next = mutableListOf<Boolean>()
        assertTrue(gate.begin(setOf("camera")) { old.add(it) })
        gate.cancel()
        assertTrue(gate.busy)
        assertFalse(gate.begin(setOf("camera")) { next.add(it) })
        gate.resolve(mapOf("camera" to true))
        assertEquals(listOf(false), old)
        assertEquals(listOf(false), next)
        assertFalse(gate.busy)
        assertTrue(gate.begin(setOf("camera")) { next.add(it) })
        gate.resolve(mapOf("camera" to true))
        assertEquals(listOf(false, true), next)
    }
    @Test fun approximateLocationIsAcceptedOnlyForRequestedResources() {
        val gate = RuntimePermissionGate()
        var granted = false
        gate.begin(setOf("fine", "coarse"), anyOf = true) { granted = it }
        gate.resolve(mapOf("fine" to false, "coarse" to true))
        assertTrue(granted)
        gate.begin(setOf("fine", "coarse"), anyOf = true) { granted = it }
        gate.resolve(mapOf("camera" to true))
        assertFalse(granted)
    }
}
