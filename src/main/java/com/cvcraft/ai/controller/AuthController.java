package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;
    public AuthController(AuthService a, CurrentUserService c) { this.authService = a; this.currentUserService = c; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) { return authService.register(req); }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) { return authService.login(req); }
    @GetMapping("/me")
    public UserResponse me() { return authService.getMe(currentUserService.getCurrentUser()); }
}
