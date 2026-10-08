package com.eventpay.identity.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final Duration EXPIRATION = Duration.ofHours(1);

  private final SecretKey key;
  private final Clock clock;

  @Autowired
  public JwtService(@Value("${security.jwt.secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  JwtService(String secret, Clock clock) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("El secreto JWT debe tener al menos 32 caracteres");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.clock = clock;
  }

  public String generateToken(UUID userId) {
    Date issuedAt = Date.from(clock.instant());
    Date expiresAt = Date.from(clock.instant().plus(EXPIRATION));
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(issuedAt)
        .expiration(expiresAt)
        .signWith(key)
        .compact();
  }
}
