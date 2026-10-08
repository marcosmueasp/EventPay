package com.eventpay.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "test-secret-minimo-32-caracteres-01";
  private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

  private final JwtService service = new JwtService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void generaTokenConSubYExpiracionDeUnaHora() {
    UUID userId = UUID.randomUUID();

    String token = service.generateToken(userId);

    Claims claims = parse(token);
    assertEquals(userId.toString(), claims.getSubject());
    assertEquals(NOW, claims.getIssuedAt().toInstant());
    assertEquals(NOW.plus(Duration.ofHours(1)), claims.getExpiration().toInstant());
  }

  @Test
  void rechazaSecretoDeMenosDe32Caracteres() {
    assertThrows(IllegalArgumentException.class,
        () -> new JwtService("corto", Clock.systemUTC()));
  }

  private Claims parse(String token) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser()
        .verifyWith(key)
        .clock(() -> Date.from(NOW))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
