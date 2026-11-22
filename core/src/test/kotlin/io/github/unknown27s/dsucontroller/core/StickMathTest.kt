package io.github.unknown27s.dsucontroller.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickMathTest {
    @Test fun neutralAndDeadZoneAreCentered() {
        assertEquals(128 to 128, StickMath.encode(0f, 0f, 0f))
        assertEquals(128 to 128, StickMath.encode(.05f, -.05f, .12f))
        assertEquals(128 to 128, StickMath.encode(Float.NaN, 0f, .12f))
    }

    @Test fun cardinalDirectionsAndOutsideDragsReachEndpoints() {
        assertEquals(255 to 128, StickMath.encode(1f, 0f, .12f))
        assertEquals(0 to 128, StickMath.encode(-1f, 0f, .12f))
        assertEquals(128 to 255, StickMath.encode(0f, 1f, .12f))
        assertEquals(128 to 0, StickMath.encode(0f, -3f, .12f))
    }

    @Test fun diagonalsRemainRadialAndRescalingIsContinuous() {
        val diagonal = StickMath.encode(1f, 1f, .12f)
        assertEquals(diagonal.first, diagonal.second)
        assertTrue(diagonal.first in 217..219)
        assertEquals(128 to 128, StickMath.encode(.12001f, 0f, .12f))
        assertTrue(StickMath.encode(.6f, 0f, .12f).first > 128)
    }
}
