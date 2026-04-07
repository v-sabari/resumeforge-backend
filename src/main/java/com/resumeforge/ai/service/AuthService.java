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
    private static final String OTP_PURPOSE_VERIFY_EMAIL = "VERIFY_EMAIL";
    private static final int OTP_EXPIRY_SECONDS = 300;

    private final UserRepository userRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       EmailOtpRepository emailOtpRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .premium(false)
                .emailVerified(false)
                .enabled(false)
                .build();

        userRepository.save(user);

        invalidatePreviousUnusedOtps(email, OTP_PURPOSE_VERIFY_EMAIL);

        String otpCode = generateOtp();

        EmailOtp otp = EmailOtp.builder()
                .email(email)
                .otpCode(otpCode)
                .purpose(OTP_PURPOSE_VERIFY_EMAIL)
                .expiresAt(Instant.now().plusSeconds(OTP_EXPIRY_SECONDS))
                .used(false)
                .build();

        emailOtpRepository.save(otp);
        emailService.sendVerificationOtp(email, otpCode);

        return new RegisterResponse(
                "Verification OTP sent to your email address",
                email
        );
    }

    public MessageResponse verifyEmailOtp(VerifyOtpRequest request) {
        String email = request.email().trim().toLowerCase();

        EmailOtp otp = emailOtpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, OTP_PURPOSE_VERIFY_EMAIL)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP not found"));

        if (otp.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP already used");
        }

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired");
        }

        if (!otp.getOtpCode().equals(request.otp().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        otp.setUsed(true);
        emailOtpRepository.save(otp);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);

        return new MessageResponse("Email verified successfully");
    }

    public MessageResponse resendVerificationOtp(ResendOtpRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No account found for this email"));

        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email is already verified");
        }

        invalidatePreviousUnusedOtps(email, OTP_PURPOSE_VERIFY_EMAIL);

        String otpCode = generateOtp();

        EmailOtp otp = EmailOtp.builder()
                .email(email)
                .otpCode(otpCode)
                .purpose(OTP_PURPOSE_VERIFY_EMAIL)
                .expiresAt(Instant.now().plusSeconds(OTP_EXPIRY_SECONDS))
                .used(false)
                .build();

        emailOtpRepository.save(otp);
        emailService.sendVerificationOtp(email, otpCode);

        return new MessageResponse("A new OTP has been sent to your email");
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.isEmailVerified() || !user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please verify your email before logging in");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, toUserResponse(user));
    }

    public UserResponse getMe(User user) {
        return toUserResponse(user);
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isPremium(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }

    private void invalidatePreviousUnusedOtps(String email, String purpose) {
        List<EmailOtp> oldOtps = emailOtpRepository.findByEmailIgnoreCaseAndPurposeAndUsedFalse(email, purpose);
        for (EmailOtp oldOtp : oldOtps) {
            oldOtp.setUsed(true);
        }
        emailOtpRepository.saveAll(oldOtps);
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int value = 100000 + random.nextInt(900000);
        return String.valueOf(value);
    }
}