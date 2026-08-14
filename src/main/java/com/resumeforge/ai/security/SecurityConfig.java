package com.resumeforge.ai.security;

import tools.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.ApiResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsRaw;

    // BUG-008 FIX: LoginRateLimitFilter (AUTH-02 FIX) was fully implemented —
    // per-IP sliding window, 5 failed attempts / 15 min, HTTP 429 + Retry-After —
    // but was never registered as a bean or added to the security filter chain,
    // so it never actually ran. Live testing confirmed 6 rapid failed logins all
    // returned plain 401s with no throttling. Registering it here and inserting
    // it before JwtAuthenticationFilter, matching its own javadoc ("runs BEFORE
    // Spring Security").
    @Bean
    public LoginRateLimitFilter loginRateLimitFilter() {
        return new LoginRateLimitFilter();
    }

    // SEC FIX: rate-limit OTP verification / resend, password reset and contact
    // endpoints per IP so they cannot be spammed or brute-forced.
    @Bean
    public SensitiveEndpointRateLimitFilter sensitiveEndpointRateLimitFilter() {
        return new SensitiveEndpointRateLimitFilter();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------- STARTUP VALIDATION --------------------
    @PostConstruct
    public void validateCors() {
        List<String> origins = parseOrigins(allowedOriginsRaw);

        if (origins.isEmpty()) {
            throw new IllegalStateException("CORS origins cannot be empty");
        }

        if (origins.contains("*")) {
            throw new IllegalStateException("Wildcard CORS is not allowed in production");
        }

        System.out.println("CORS allowed origins: " + origins);
    }

    // -------------------- SECURITY CHAIN --------------------
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(unauthorizedEntryPoint())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/contact").permitAll()
                        .requestMatchers("/api/testimonials/**").permitAll()
                        .requestMatchers("/health", "/api/health/**").permitAll()

                        // IMPORTANT: Razorpay webhook must be public
                        .requestMatchers("/api/payments/webhook").permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // BUG-008 FIX: rate-limit login attempts before JWT processing.
                // NOTE: must anchor to a *built-in* Spring Security filter class
                // (UsernamePasswordAuthenticationFilter has a registered order).
                // Anchoring to JwtAuthenticationFilter.class instead throws
                // "The Filter class ... does not have a registered order" at
                // startup, because that's a custom filter with no known
                // position either. Adding both here, in this order, before the
                // same built-in filter correctly places loginRateLimitFilter
                // ahead of jwtAuthenticationFilter in the chain.
                .addFilterBefore(loginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(sensitiveEndpointRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // -------------------- CORS CONFIG --------------------
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = parseOrigins(allowedOriginsRaw);
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));

        config.setExposedHeaders(List.of("Authorization"));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    // -------------------- 401 HANDLER --------------------
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse body = ApiResponse.error(
                    "Unauthorized. Please login again with valid token."
            );

            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }

    // -------------------- AUTH MANAGER --------------------
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    // -------------------- PASSWORD ENCODER --------------------
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // -------------------- HELPER --------------------
    private List<String> parseOrigins(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.equals("*"))
                .collect(Collectors.toList());
    }
}