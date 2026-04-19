package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resumeforge.ai.dto.ResumeDto;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GDPR-compliant account deletion and data export.
 *
 * Delete:
 *   - Permanently deletes the User record (CASCADE removes all child data:
 *     resumes, experiences, education, projects, payments, referrals, AI logs, OTPs).
 *   - This is irreversible. We do NOT soft-delete.
 *
 * Data export:
 *   - Returns a JSON export of all the user's data before deletion.
 *   - Suitable for downloading and archiving prior to account deletion.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final ResumeService  resumeService;

    public AccountDeletionService(UserRepository userRepository, ResumeService resumeService) {
        this.userRepository = userRepository;
        this.resumeService  = resumeService;
    }

    /**
     * Exports all user data as a pretty-printed JSON byte array.
     * Called before deletion — or independently if the user just wants their data.
     */
    public byte[] exportUserData(User user) {
        try {
            List<ResumeDto> resumes = resumeService.getAll(user);

            Map<String, Object> export = new HashMap<>();
            export.put("exportedAt",   java.time.Instant.now().toString());
            export.put("userId",       user.getId());
            export.put("name",         user.getName());
            export.put("email",        user.getEmail());
            export.put("createdAt",    user.getCreatedAt().toString());
            export.put("isPremium",    user.isPremium());
            export.put("referralCode", user.getReferralCode());
            export.put("resumes",      resumes);

            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.INDENT_OUTPUT);

            return mapper.writeValueAsBytes(export);

        } catch (Exception e) {
            log.error("Data export failed for userId={}: {}", user.getId(), e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not generate data export. Please try again.");
        }
    }

    /**
     * Permanently deletes the user account and all associated data.
     * This operation is irreversible.
     *
     * The cascade delete on User removes:
     *   - resumes (+ experiences, education, projects via resume cascade)
     *   - payments
     *   - ad_events
     *   - export_usage
     *   - email_otps
     *   - ai_usage_logs
     *   - referrals (as referrer and as referred_user via unique FK)
     *   - referral_rewards
     *   - resume_snapshots
     */
    @Transactional
    public void deleteAccount(User user) {
        log.info("Account deletion initiated: userId={} email={}", user.getId(), user.getEmail());
        userRepository.delete(user);
        log.info("Account permanently deleted: userId={}", user.getId());
    }
}
