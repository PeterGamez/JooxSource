package com.github.petergamez.joox;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JooxRestHandlerTest {

    @Test
    void testOpenAccessWhenPasswordIsEmpty() {
        JooxPluginConfig config = new JooxPluginConfig();
        config.getRestApi().setPassword("");

        JooxPluginLoader loader = new JooxPluginLoader(config);
        JooxRestHandler handler = new JooxRestHandler(loader);

        ResponseEntity<Map<String, Object>> response = handler.getStatus(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("enabled"));
        assertEquals(false, response.getBody().get("directStream"));
        assertEquals(true, response.getBody().get("fallbackEnabled"));
        assertNotNull(response.getBody().get("fallbackProviders"));
    }

    @Test
    void testUnauthorizedWhenPasswordIsMissingOrIncorrect() {
        JooxPluginConfig config = new JooxPluginConfig();
        config.getRestApi().setPassword("secret123");

        JooxPluginLoader loader = new JooxPluginLoader(config);
        JooxRestHandler handler = new JooxRestHandler(loader);

        // Missing header
        ResponseEntity<Map<String, Object>> missingHeaderResponse = handler.getStatus(null);
        assertEquals(HttpStatus.UNAUTHORIZED, missingHeaderResponse.getStatusCode());
        assertEquals("Unauthorized", missingHeaderResponse.getBody().get("error"));

        // Wrong header
        ResponseEntity<Map<String, Object>> wrongHeaderResponse = handler.getStatus("wrongpassword");
        assertEquals(HttpStatus.UNAUTHORIZED, wrongHeaderResponse.getStatusCode());
    }

    @Test
    void testAuthorizedWhenPasswordMatches() {
        JooxPluginConfig config = new JooxPluginConfig();
        config.getRestApi().setPassword("secret123");

        JooxPluginLoader loader = new JooxPluginLoader(config);
        JooxRestHandler handler = new JooxRestHandler(loader);

        ResponseEntity<Map<String, Object>> response = handler.getStatus("secret123");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
