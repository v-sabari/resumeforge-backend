package com.cvcraft.ai.service;

import com.cvcraft.ai.entity.User;
import com.cvcraft.ai.exception.UnauthorizedException;
import com.cvcraft.ai.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
