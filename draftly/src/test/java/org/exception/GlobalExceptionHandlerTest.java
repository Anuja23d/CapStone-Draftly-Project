package org.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badRequestExceptionReturns400() {
        ResponseEntity<Map<String, String>> response =
                handler.handleBadRequest(new BadRequestException("Missing email"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing email", response.getBody().get("error"));
    }

    @Test
    void resourceNotFoundExceptionReturns404() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new ResourceNotFoundException("Draft not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Draft not found", response.getBody().get("error"));
    }

    @Test
    void unexpectedExceptionReturns500() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUnexpected(new RuntimeException("Something failed"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Something failed", response.getBody().get("error"));
    }
}
