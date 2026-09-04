package com.resumeforge.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${app.jwt-secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private Long expirationMs;

    // REMEMBER-ME: optional longer lifetime used when a user logs in with the
    // "Remember me" checkbox ticked. Configured independently of the regular
    // session lifetime so Remember Me can be a longer, revocable session without
    // extending every user's default session. Defaults to 14 days.
    @Value("${app.jwt.remember-me-expiration-ms:1209600000}")
    private Long rememberMeExpirationMs;

    // SEC FIX: fail fast at startup instead of silently signing tokens with an
    // empty or too-short key. Empty default + empty env would otherwise produce
    // an IllegalArgumentException only at runtime on the first request.
    @PostConstruct
    public void validateConfig() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt-secret / APP_JWT_SECRET is not set. Set it to a random value of at least 32 bytes.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt-secret is too short ("
                            + secret.getBytes(StandardCharsets.UTF_8).length
                            + " bytes). HMAC-SHA256 requires a key of at least 32 bytes.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, Long userId) {
        return generateToken(email, userId, false);
    }

    /**
     * Issues a JWT whose lifetime matches the requested session flavor:
     * Remember Me (true) uses the longer remember-me expiry, otherwise the
     * regular session expiry. The matching cookie max-age is derived through
     * {@link #sessionExpirationMs(boolean)} so the token and cookie can never
     * drift apart.
     */
    public String generateToken(String email, Long userId, boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return createToken(claims, email, sessionExpirationMs(rememberMe));
    }

    /** Single source of truth for the JWT expiration, in milliseconds. */
    public long sessionExpirationMs(boolean rememberMe) {
        if (rememberMe && rememberMeExpirationMs != null && rememberMeExpirationMs > 0) {
            return rememberMeExpirationMs;
        }
        return expirationMs;
    }

    private String createToken(Map<String, Object> claims, String subject, long lifetimeMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + lifetimeMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Returns the iat (issued-at) timestamp embedded in the token.
     * Used by JwtAuthenticationFilter to reject tokens issued before
     * the user's tokenIssuedAt watermark (e.g. after a password reset).
     */
    public Instant extractIssuedAt(String token) {
        Date iat = extractClaim(token, Claims::getIssuedAt);
        return iat != null ? iat.toInstant() : Instant.EPOCH;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, String email) {
        final String extractedEmail = extractEmail(token);
        return (extractedEmail.equals(email) && !isTokenExpired(token));
    }
}