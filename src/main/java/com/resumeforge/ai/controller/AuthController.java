package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.security.JwtUtil;
import com.resumeforge.ai.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // BUG-004 FIX: cookie name for the httpOnly session token. Kept distinct
    // from the old localStorage key ("resumeforge_token") only in spirit —
    // reusing the same name is fine and keeps things easy to recognise in
    // browser devtools.
    private static final String AUTH_COOKIE_NAME = "resumeforge_token";

    /**
     * BUG-004 FIX: The JWT used to be returned in the JSON response body and
     * stored in localStorage by the frontend, which meant any XSS on the site
     * could read and exfiltrate it via JavaScript. It is now delivered only
     * as an httpOnly cookie, which client-side JS cannot read at all.
     *
     * COOKIE FIX: SameSite was originally "None" because the frontend and
     * this API were on different registrable domains, which made the cookie
     * third-party and got it blocked outright by any browser/user with
     * third-party cookie blocking enabled. The frontend now proxies /api/*
     * through its own domain (see vercel.json), so this cookie is first-party
     * from the browser's perspective — no Domain attribute is set here, so
     * it's scoped to whatever host the browser thinks it talked to (its own
     * origin, via the proxy). SameSite=Lax is the more appropriate, more
     * secure setting for a first-party session cookie (adds baseline CSRF
     * protection); "None" is no longer needed.
     *
     * REMEMBER-ME: maxAgeSeconds is derived from the same JWT lifetime that
     * was used to sign the token (jwtUtil.sessionExpirationMs()), so the
     * cookie persistence and the token expiry never drift apart.
     */
    private void setAuthCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** BUG-004 FIX: logout clears the httpOnly cookie server-side, since
     *  client-side JS has no way to delete an httpOnly cookie itself.
     *  COOKIE FIX: SameSite=Lax to match setAuthCookie — see its doc comment. */
    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * REMEMBER-ME: revoke every outstanding session for the user who owns the
     * presented token by stamping their token_issued_at watermark. The JWT
     * filter rejects any token whose iat precedes it, so a remember-me token
     * (valid for up to 14 days) is cut short the moment the user logs out.
     * Best effort — if the token is missing, already invalid, or the lookup
     * fails, we simply continue with the cookie already cleared.
     */
    private void revokeAllUserSessions(HttpServletRequest request) {
        String token = resolveCookieToken(request);
        if (token == null) return;
        try {
            String email = jwtUtil.extractEmail(token);
            if (email == null) return;
            userRepository.findByEmail(email).ifPresent(user -> {
                user.setTokenIssuedAt(Instant.now());
                userRepository.save(user);
            });
        } catch (Exception e) {
            log.warn("Logout session revocation skipped: {}", e.getMessage());
        }
    }

    private String resolveCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/verify-email-otp")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmailOtp(request));
    }

    @PostMapping("/resend-email-otp")
    public ResponseEntity<ApiResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request);

        // REMEMBER-ME: pick the token lifetime the backend just used (regular
        // session, or the longer remember-me session) and mirror it in the
        // cookie's max-age so the browser persists it for exactly as long as
        // the JWT is valid. A ticked checkbox is never trusted by itself —
        // the flag is confirmed and selected server-side.
        boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
        long maxAgeSeconds = jwtUtil.sessionExpirationMs(rememberMe) / 1000;

        // BUG-004 FIX: deliver the token via httpOnly cookie instead of the
        // JSON body, then strip it from the body so it's never reachable
        // from JavaScript at all.
        setAuthCookie(response, authResponse.getToken(), maxAgeSeconds);
        authResponse.setToken(null);

        return ResponseEntity.ok(authResponse);
    }

    // BUG-004 FIX: new endpoint — client-side JS cannot delete an httpOnly
    // cookie itself, so logout has to be a server round-trip that clears it.
    // REMEMBER-ME: on top of clearing the cookie, logout also stamps the
    // user's token_issued_at watermark. This revokes every outstanding JWT for
    // that user server-side (including any long-lived remember-me token), so
    // a logout cannot be silently undone by replaying a previously-issued or
    // stolen cookie. New logins issue a fresh token past the new watermark.
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        clearAuthCookie(response);
        revokeAllUserSessions(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        // SEC-07 FIX: When no Authorization header is sent (or JWT is invalid),
        // Spring injects null for @AuthenticationPrincipal. Without this guard,
        // authService.getCurrentUser(null) calls null.getId() → NullPointerException → 500.
        //
        // PROBE FIX: /api/auth/me doubles as the anonymous "am I logged in?"
        // probe that AuthContext fires on every page load — including public
        // pages like /pricing. Returning 401 there made the browser log
        // "Failed to load resource: 401" for every anonymous visit and forced
        // the frontend interceptor to special-case it. Anonymous visitors now
        // get 200 with an all-null UserResponse (id == null), and the frontend
        // treats me.id == null as logged-out. Authenticated callers are
        // unaffected and still receive the full user profile.
        if (user == null) {
            return ResponseEntity.ok(new UserResponse());
        }
        return ResponseEntity.ok(authService.getCurrentUser(user));
    }
}