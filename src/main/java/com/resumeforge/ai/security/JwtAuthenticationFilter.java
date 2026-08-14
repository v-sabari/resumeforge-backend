package com.resumeforge.ai.security;

import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // BUG-004 FIX: must match AuthController.AUTH_COOKIE_NAME.
    private static final String AUTH_COOKIE_NAME = "resumeforge_token";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    /**
     * BUG-004 FIX: the JWT now arrives as an httpOnly cookie for browser
     * clients instead of an Authorization header. The header is still
     * checked first and still works for any non-browser API client that
     * sends one directly (e.g. scripts, Postman) — this is purely additive.
     */
    private String resolveToken(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String jwt = resolveToken(request);
        log.debug("JWT filter: uri={} tokenPresent={}", request.getRequestURI(), jwt != null);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtUtil.extractEmail(jwt);
            log.debug("JWT filter: email extracted for authentication attempt");
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByEmail(email).orElse(null);
                log.debug("JWT filter: user found={}", user != null);
                boolean valid = jwtUtil.validateToken(jwt, email);
                log.debug("JWT filter: token valid={}", valid);
                if (user != null && valid){

                    // B3 fix: reject tokens issued before the user's security watermark.
                    // tokenIssuedAt is stamped on password reset (and any future forced-logout).
                    // A null watermark means no invalidation has occurred — token is accepted.
                    Instant tokenIat = jwtUtil.extractIssuedAt(jwt);
                    Instant watermark = user.getTokenIssuedAt();

                    log.debug("JWT filter: tokenIssuedAt={} watermark={} expiresAt={}",
                            tokenIat, watermark, jwtUtil.extractExpiration(jwt));

                    if (watermark != null && tokenIat.isBefore(watermark)) {
                        log.warn("Rejected token on {} — issued before the user's security watermark", request.getRequestURI());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.singletonList(
                                            new SimpleGrantedAuthority("ROLE_" + user.getRole())
                                    )
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            // B4 FIX: replace single catch(Exception e) with specific exception types
            // so each failure reason is clearly identifiable in logs at the right severity.

        } catch (ExpiredJwtException e) {
            // Normal — token aged out. DEBUG to avoid flooding logs.
            log.debug("Expired JWT on {}: {}", request.getRequestURI(), e.getMessage());

        } catch (SecurityException | MalformedJwtException e) {
            // Tampered signature or structurally broken token — worth investigating.
            log.warn("Invalid JWT signature or structure on {}: {}", request.getRequestURI(), e.getMessage());

        } catch (UnsupportedJwtException e) {
            // Wrong algorithm or token type — likely a misconfigured client.
            log.warn("Unsupported JWT on {}: {}", request.getRequestURI(), e.getMessage());

        } catch (IllegalArgumentException e) {
            // Empty or null token string — malformed Authorization header.
            log.warn("Blank or null JWT on {}: {}", request.getRequestURI(), e.getMessage());

        } catch (Exception e) {
            // Unexpected failure (e.g. DB error while loading user). ERROR because
            // this is not a normal token rejection path — should be investigated.
            log.error("Unexpected error during JWT authentication on {}", request.getRequestURI(), e);
        }

        filterChain.doFilter(request, response);
    }
}