package com.example.myapplication.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSimilarityGradientTest {
    @Test
    fun themeColorBecomesContinuouslyStrongerTowardOneHundredPercent() {
        val zero = similarityGradientEndAlpha(0)
        val halfway = similarityGradientEndAlpha(50)
        val full = similarityGradientEndAlpha(100)

        assertEquals(0.12f, zero, 0.0001f)
        assertTrue(halfway > zero)
        assertTrue(full > halfway)
        assertEquals(0.95f, full, 0.0001f)
    }

    @Test
    fun outOfRangeSimilarityScoresAreClamped() {
        assertEquals(similarityGradientEndAlpha(0), similarityGradientEndAlpha(-10), 0.0001f)
        assertEquals(similarityGradientEndAlpha(100), similarityGradientEndAlpha(140), 0.0001f)
    }
}
