package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // BUG-004 FIX: cookie name for the httpOnly session token. Kept distinct
    // from the old localStorage key ("resumeforge_token") only in spirit —
    // reusing the same name is fine and keeps things easy to recognise in
    // browser devtools.
    private static final String AUTH_COOKIE_NAME = "resumeforge_token";

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

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
     */
    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtExpirationMs / 1000)
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

        // BUG-004 FIX: deliver the token via httpOnly cookie instead of the
        // JSON body, then strip it from the body so it's never reachable
        // from JavaScript at all.
        setAuthCookie(response, authResponse.getToken());
        authResponse.setToken(null);

        return ResponseEntity.ok(authResponse);
    }

    // BUG-004 FIX: new endpoint — client-side JS cannot delete an httpOnly
    // cookie itself, so logout has to be a server round-trip that clears it.
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletResponse response) {
        clearAuthCookie(response);
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
        // The correct response is 401 Unauthorized.
        if (user == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getCurrentUser(user));
    }
}