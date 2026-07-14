package com.example.myapplication.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTagOptionsTest {
    @Test
    fun `selection does not move catalog tags from their original positions`() {
        val catalog = listOf("K-Pop", "록", "R&B", "힙합")

        assertEquals(
            catalog,
            stableProfileTagOptions(catalog, selected = listOf("힙합", "록")),
        )
    }

    @Test
    fun `selected legacy tag is appended without reordering the catalog`() {
        assertEquals(
            listOf("K-Pop", "록", "재즈"),
            stableProfileTagOptions(
                catalog = listOf("K-Pop", "록"),
                selected = listOf("재즈", "K-Pop"),
            ),
        )
    }
}
