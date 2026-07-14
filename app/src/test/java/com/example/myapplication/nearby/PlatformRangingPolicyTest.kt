package com.example.myapplication.nearby

import org.junit.Assert.assertFalse
import org.junit.Test

class PlatformRangingPolicyTest {
    @Test
    fun android16PlatformRangingRemainsDisabledUntilFrameworkCrashIsFixed() {
        assertFalse(PlatformRangingPolicy.enabled)
    }
}
