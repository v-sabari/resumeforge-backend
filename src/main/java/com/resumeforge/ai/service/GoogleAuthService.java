package com.resumeforge.ai.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.resumeforge.ai.exception.UnauthorizedException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * GOOGLE SIGN-IN: thin facade over {@link GoogleIdTokenVerifier}. Keeps all
 * Google client machinery in one place so AuthService can ask a simple
 * question — "is this credential a valid token for our OAuth app, and for
 * which Google account?" — without caring about transport/factory details.
 *
 * Uses Gson (not Jackson) for the id-token JSON transport deliberately: this
 * project runs Jackson 3 ({@code tools.jackson.*}), and pulling in an old
 * Jackson 2 databind just to parse the credential would risk serializer
 * clashes inside Spring's ObjectMapper.
 */
@Service
public class GoogleAuthService {

    @Value("${app.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        // SEC FIX: fail fast at startup when GOOGLE_CLIENT_ID is missing,
        // rather than rejecting every Google login at runtime with one blob of
        // errors. Render supplies this via its env vars.
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException(
                    "app.google.client-id / GOOGLE_CLIENT_ID is not set. Google Sign-In will not work.");
        }

        List<String> issuers = Arrays.asList("accounts.google.com", "https://accounts.google.com");
        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(googleClientId))
                .setIssuers(issuers)
                .build();
    }

    /**
     * Verifies the presented Google ID token against our OAuth client id.
     * Returns the verified payload (email, name, sub{@literal =}googleId), or
     * throws {@link UnauthorizedException} when the token is invalid/expired
     * or does not match our audience.
     */
    public GoogleIdToken.Payload verify(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new UnauthorizedException("Missing Google credential");
        }
        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new UnauthorizedException("Invalid Google credential");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new UnauthorizedException("Invalid Google credential");
        }
    }
}