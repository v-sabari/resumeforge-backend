package com.resumeforge.ai.security;

import com.resumeforge.ai.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.resumeforge.ai.entity.User user = userRepository
                .findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Grant ROLE_ADMIN for admin users, ROLE_USER for everyone else.
        // Spring Security's hasRole("ADMIN") checks for the ROLE_ADMIN authority.
        String authority = "ROLE_" + (user.getRole() != null ? user.getRole() : "USER");

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authority)
                .build();
    }
}
