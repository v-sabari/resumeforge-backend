package com.resumeforge.ai.security;

import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            String email = jwtUtil.extractEmail(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByEmail(email).orElse(null);

                if (user != null && jwtUtil.validateToken(jwt, email)) {

                    // B3 fix: reject tokens issued before the user's security watermark.
                    // tokenIssuedAt is stamped on password reset (and any future forced-logout).
                    // A null watermark means no invalidation has occurred — token is accepted.
                    Instant tokenIat = jwtUtil.extractIssuedAt(jwt);
                    Instant watermark = user.getTokenIssuedAt();

                    if (watermark != null && tokenIat.isBefore(watermark)) {
                        log.warn("Rejected token for user={} — issued at {} which is before watermark {}",
                                email, tokenIat, watermark);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.singletonList(
                                            new SimpleGrantedAuthority("ROLE_" + user.getRole())
                                    )
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // B4 fix (logged here rather than silently ignored — full fix in B4 task)
            log.debug("JWT validation failed for request {}: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}