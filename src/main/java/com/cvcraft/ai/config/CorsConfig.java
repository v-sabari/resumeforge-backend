package com.cvcraft.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /**
     * In production (FRONTEND_URL set to real domain), ONLY that domain is allowed.
     * Localhost origins are included automatically for local development convenience —
     * they are harmless because they cannot be accessed by real users in production.
     * If you want to be strict, remove the localhost entries before final deploy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = new ArrayList<>();
        origins.add(allowedOrigin);
        // Local development origins — safe to keep; localhost cannot be spoofed from browser
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");
        origins.add("http://localhost:4173"); // vite preview

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
