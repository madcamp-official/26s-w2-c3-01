package com.melodybubble.server.offlineexchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus

class OfflineExchangeControllerTest {
    @Test
    fun `invalid signatures are exposed as permanent bad requests`() {
        val controller = OfflineExchangeController(mock(OfflineExchangeService::class.java))

        val response = controller.invalidRequest(IllegalArgumentException("Invalid exchange signature"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Invalid exchange signature", response.body?.message)
    }
}
