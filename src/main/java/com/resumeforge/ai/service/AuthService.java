package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.EmailOtp;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.EmailOtpRepository;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final String OTP_PURPOSE_VERIFY_EMAIL   = "VERIFY_EMAIL";
    private static final String OTP_PURPOSE_RESET_PASSWORD = "RESET_PASSWORD";
    private static final int    OTP_EXPIRY_SECONDS         = 300;

    private final UserRepository       userRepository;
    private final EmailOtpRepository   emailOtpRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService         emailService;
    private final ReferralService      referralService;
    private final OtpAttemptService    otpAttemptService;

    public AuthService(UserRepository userRepository,
                       EmailOtpRepository emailOtpRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       EmailService emailService,
                       ReferralService referralService,
                       OtpAttemptService otpAttemptService) {
        this.userRepository        = userRepository;
        this.emailOtpRepository    = emailOtpRepository;
        this.passwordEncoder       = passwordEncoder;
        this.jwtService            = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailService          = emailService;
        this.referralService       = referralService;
        this.otpAttemptService     = otpAttemptService;
    }

    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        String referralCode = referralService.generateUniqueReferralCode();

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .premium(false)
                .emailVerified(false)
                .enabled(false)
                .referralCode(referralCode)
                .build();

        userRepository.save(user);

        if (request.referralCode() != null && !request.referralCode().isBlank()) {
            referralService.recordReferralAtRegistration(user, request.referralCode().trim());
        }

        issueOtp(email, OTP_PURPOSE_VERIFY_EMAIL);
        emailService.sendVerificationOtp(email, getLatestOtp(email, OTP_PURPOSE_VERIFY_EMAIL));

        return new RegisterResponse("Verification OTP sent to your email address", email);
    }

    public MessageResponse verifyEmailOtp(VerifyOtpRequest request) {
        String email = request.email().trim().toLowerCase();

        // ── OTP brute-force protection ──────────────────────────────────
        if (otpAttemptService.isLockedOut(email)) {
            long remaining = otpAttemptService.lockoutSecondsRemaining(email);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please wait " + (remaining / 60 + 1) +
                    " minute(s) before trying again.");
        }

        EmailOtp otp = emailOtpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, OTP_PURPOSE_VERIFY_EMAIL)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP not found"));

        if (otp.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP already used");
        }
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otpAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        if (!otp.getOtpCode().equals(request.otp().trim())) {
            otpAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        // Correct OTP — clear lockout counter
        otpAttemptService.clearAttempts(email);

        otp.setUsed(true);
        emailOtpRepository.save(otp);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);

        try { referralService.checkAndQualifyReferral(user); } catch (Exception ignored) {}

        return new MessageResponse("Email verified successfully");
    }

    public MessageResponse resendVerificationOtp(ResendOtpRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No account found for this email"));

        if (user.isEmailVerified())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email is already verified");

        // Clear lockout counter when user requests a fresh OTP
        otpAttemptService.clearAttempts(email);

        invalidatePreviousUnusedOtps(email, OTP_PURPOSE_VERIFY_EMAIL);
        String otpCode = generateOtp();
        saveOtp(email, otpCode, OTP_PURPOSE_VERIFY_EMAIL);
        emailService.sendVerificationOtp(email, otpCode);

        return new MessageResponse("A new OTP has been sent to your email");
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.isEmailVerified() || !user.isEnabled())
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Please verify your email before logging in");

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        return new AuthResponse(jwtService.generateToken(user.getEmail()), toUserResponse(user));
    }

    public UserResponse getMe(User user) {
        return toUserResponse(user);
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.isPremium(), user.isEmailVerified(), user.getCreatedAt(),
                user.getRole() != null ? user.getRole() : "USER",
                user.getReferralCode()
        );
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !user.isEmailVerified() || !user.isEnabled()) {
            // Always return the same message to prevent email enumeration
            return new MessageResponse(
                    "If an account exists with this email, a password reset OTP has been sent");
        }

        invalidatePreviousUnusedOtps(email, OTP_PURPOSE_RESET_PASSWORD);
        String otpCode = generateOtp();
        saveOtp(email, otpCode, OTP_PURPOSE_RESET_PASSWORD);
        emailService.sendPasswordResetOtp(email, otpCode);

        return new MessageResponse(
                "If an account exists with this email, a password reset OTP has been sent");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = request.email().trim().toLowerCase();

        // ── OTP brute-force protection ──────────────────────────────────
        if (otpAttemptService.isLockedOut(email)) {
            long remaining = otpAttemptService.lockoutSecondsRemaining(email);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please wait " + (remaining / 60 + 1) +
                    " minute(s) before trying again.");
        }

        if (!request.newPassword().equals(request.confirmPassword()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwords do not match");

        EmailOtp otp = emailOtpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, OTP_PURPOSE_RESET_PASSWORD)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP not found"));

        if (otp.isUsed()) throw new ApiException(HttpStatus.BAD_REQUEST, "OTP already used");
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otpAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        if (!otp.getOtpCode().equals(request.otp().trim())) {
            otpAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        otpAttemptService.clearAttempts(email);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        otp.setUsed(true);
        emailOtpRepository.save(otp);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        invalidatePreviousUnusedOtps(email, OTP_PURPOSE_RESET_PASSWORD);

        return new MessageResponse("Password reset successfully");
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private void issueOtp(String email, String purpose) {
        invalidatePreviousUnusedOtps(email, purpose);
        saveOtp(email, generateOtp(), purpose);
    }

    private void saveOtp(String email, String code, String purpose) {
        emailOtpRepository.save(EmailOtp.builder()
                .email(email).otpCode(code).purpose(purpose)
                .expiresAt(Instant.now().plusSeconds(OTP_EXPIRY_SECONDS)).used(false).build());
    }

    private String getLatestOtp(String email, String purpose) {
        return emailOtpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, purpose)
                .map(EmailOtp::getOtpCode).orElse("");
    }

    private void invalidatePreviousUnusedOtps(String email, String purpose) {
        List<EmailOtp> old = emailOtpRepository
                .findByEmailIgnoreCaseAndPurposeAndUsedFalse(email, purpose);
        old.forEach(o -> o.setUsed(true));
        emailOtpRepository.saveAll(old);
    }

    private String generateOtp() {
        return String.valueOf(100000 + new SecureRandom().nextInt(900000));
    }
}
