package com.cvcraft.ai.service;

import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.entity.User;
import com.cvcraft.ai.exception.ApiException;
import com.cvcraft.ai.repository.UserRepository;
import com.cvcraft.ai.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository       = userRepository;
        this.passwordEncoder      = passwordEncoder;
        this.jwtService           = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        User user = User.builder()
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .premium(false)
                .build();
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user.getEmail()), toUserResponse(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(), request.password()));
        } catch (BadCredentialsException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return new AuthResponse(jwtService.generateToken(user.getEmail()), toUserResponse(user));
    }

    public UserResponse getMe(User user) {
        return toUserResponse(user);
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.isPremium(), user.getCreatedAt());
    }
}
