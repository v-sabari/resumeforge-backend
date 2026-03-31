package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.AuthResponse;
import com.resumeforge.ai.dto.LoginRequest;
import com.resumeforge.ai.dto.RegisterRequest;
import com.resumeforge.ai.dto.UserResponse;
import com.resumeforge.ai.service.AuthService;
import com.resumeforge.ai.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.getMe(currentUserService.getCurrentUser());
    }
}
