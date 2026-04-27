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

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .premium(false)
                .emailVerified(false)
                .emailOtp(OtpUtil.generateOtp())
                .emailOtpExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        user = userRepository.save(user);

        referralService.ensureReferralCode(user);
        referralService.attachReferralAtSignup(user, request.getReferralCode());

        emailService.sendVerificationEmail(user.getEmail(), user.getEmailOtp());
        return ApiResponse.success("Registration successful. Please check your email for OTP.");
    }

    @Transactional
    public AuthResponse verifyEmailOtp(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }

        if (user.getEmailOtp() == null || !user.getEmailOtp().equals(request.getOtp())) {
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

        user.setEmailOtp(OtpUtil.generateOtp());
        user.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getEmailOtp());
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

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public ApiResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String resetToken = TokenUtil.generateToken();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
        return ApiResponse.success("Password reset link sent to your email");
    }

    // B3 FIX: Stamp tokenIssuedAt after every password reset.
    // JwtAuthenticationFilter rejects any token whose iat is before this watermark,
    // so tokens issued before the reset are invalidated immediately without needing
    // a blocklist or waiting up to 24 h for old tokens to expire naturally.
    @Transactional
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
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
                .build();
    }
}