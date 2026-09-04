package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.UnauthorizedException;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.security.JwtUtil;
import com.resumeforge.ai.util.OtpUtil;
import com.resumeforge.ai.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ReferralService referralService;

    @Transactional
    public ApiResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        // SEC FIX: only the SHA-256 digest of the OTP is persisted, never the
        // plaintext. The plaintext goes out in the email and is gone from the DB.
        String otp = OtpUtil.generateOtp();

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .premium(false)
                .emailVerified(false)
                .emailOtp(sha256Hex(otp))
                .emailOtpExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        user = userRepository.save(user);

        referralService.ensureReferralCode(user);
        referralService.attachReferralAtSignup(user, request.getReferralCode());

        emailService.sendVerificationEmail(user.getEmail(), otp);
        return ApiResponse.success("Registration successful. Please check your email for OTP.");
    }

    @Transactional
    public AuthResponse verifyEmailOtp(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }

        // SEC FIX: compare digests in constant time (MessageDigest.isEqual) so
        // the comparison does not leak how many leading characters match.
        if (user.getEmailOtp() == null || !constantTimeOtpMatches(request.getOtp(), user.getEmailOtp())) {
            throw new BadRequestException("Invalid OTP");
        }

        if (user.getEmailOtpExpiresAt() == null || user.getEmailOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        user.setEmailVerified(true);
        user.setEmailOtp(null);
        user.setEmailOtpExpiresAt(null);
        userRepository.save(user);

        referralService.onUserEmailVerified(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public ApiResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }

        String otp = OtpUtil.generateOtp();
        user.setEmailOtp(sha256Hex(otp));
        user.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), otp);
        return ApiResponse.success("OTP resent successfully");
    }

    // B1 FIX: Unverified users are now blocked at login.
    // Previously a JWT was issued regardless of email verification status,
    // allowing unverified accounts full API access.
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // Password check first — prevents account-existence enumeration via the
        // "not verified" error message (attacker cannot distinguish bad password
        // from unverified account without first knowing the correct password).
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException("Email not verified. Please check your inbox for the OTP.");
        }

        // REMEMBER-ME: a ticked checkbox extends the session lifetime (JWT
        // expiry + matching cookie max-age) without changing password handling.
        // The flag is only used to select the token lifetime here.
        boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), rememberMe);

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public ApiResponse forgotPassword(ForgotPasswordRequest request) {
        // AUTH-01 FIX: Previously threw BadRequestException("User not found") which
        // exposed whether an email is registered (user enumeration vulnerability).
        // Now returns the SAME generic response whether the email exists or not,
        // so an attacker cannot determine account existence from the response.
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String resetToken = TokenUtil.generateToken();
            // SEC FIX: store only the SHA-256 digest; the raw token is emailed
            // and can never be recovered from the DB.
            user.setPasswordResetToken(sha256Hex(resetToken));
            user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
        });
        return ApiResponse.success("If that email is registered, a password reset link has been sent.");
    }

    // B3 FIX: Stamp tokenIssuedAt after every password reset.
    // JwtAuthenticationFilter rejects any token whose iat is before this watermark,
    // so tokens issued before the reset are invalidated immediately without needing
    // a blocklist or waiting up to 24 h for old tokens to expire naturally.
    @Transactional
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        // SEC FIX: the reset token stored in the DB is the SHA-256 digest, so
        // look up by the digest of the presented token.
        User user = userRepository.findByPasswordResetToken(sha256Hex(request.getToken()))
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setTokenIssuedAt(Instant.now());   // invalidates all prior JWTs
        userRepository.save(user);

        return ApiResponse.success("Password reset successful");
    }

    public UserResponse getCurrentUser(User user) {
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .premium(user.isPremium())
                .emailVerified(user.isEmailVerified())
                .referralCode(user.getReferralCode())
                // UX-05 FIX: include createdAt so "Member since" shows correctly on ProfilePage
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String sha256Hex(String value) {
        if (value == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean constantTimeOtpMatches(String rawOtp, String storedDigest) {
        if (rawOtp == null || storedDigest == null) return false;
        String candidate = sha256Hex(rawOtp);
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                storedDigest.getBytes(StandardCharsets.UTF_8));
    }
}