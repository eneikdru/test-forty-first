package com.eneik.generated.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenUtilTest {

    private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testTokenGenerationAndVerificationSuccess() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test.user@epidem.ru");
        claims.put("role", "Administrator");
        claims.put("fullName", "Test User");
        claims.put("userId", "12345");

        String token = jwtTokenUtil.generateToken(claims, objectMapper);
        assertNotNull(token);
        assertTrue(token.contains("."));
        assertEquals(3, token.split("\\.").length);

        // Verify correct token signature verification
        assertTrue(jwtTokenUtil.verifyToken(token, objectMapper));

        // Parse claims and assert correctness
        Map<String, Object> parsedClaims = jwtTokenUtil.parseClaims(token, objectMapper);
        assertNotNull(parsedClaims);
        assertEquals("test.user@epidem.ru", parsedClaims.get("sub"));
        assertEquals("Administrator", parsedClaims.get("role"));
        assertEquals("Test User", parsedClaims.get("fullName"));
        assertEquals("12345", parsedClaims.get("userId"));
    }

    @Test
    public void testTokenVerificationRejectsInvalidSignatures() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "malicious.user@epidem.ru");
        claims.put("role", "Student");

        String token = jwtTokenUtil.generateToken(claims, objectMapper);

        // Corrupt signature part
        String[] parts = token.split("\\.");
        String corruptedToken = parts[0] + "." + parts[1] + "." + parts[2] + "corrupted";

        assertFalse(jwtTokenUtil.verifyToken(corruptedToken, objectMapper));
    }

    @Test
    public void testTokenVerificationRejectsMalformedTokens() {
        assertFalse(jwtTokenUtil.verifyToken("invalidTokenWithoutDots", objectMapper));
        assertFalse(jwtTokenUtil.verifyToken("one.dot", objectMapper));
        assertFalse(jwtTokenUtil.verifyToken("too.many.dots.here", objectMapper));
    }
}
