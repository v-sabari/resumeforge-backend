package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ExportStatusResponse;
import com.resumeforge.ai.entity.ExportHistory;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.RateLimitException;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ExportHistoryRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * EXPORT QUOTA FIX — the single source of truth for the per-user, rolling
 * 24-hour export limit.
 *
 * <p>Why this class exists: the quota logic previously lived inside
 * {@code ExportService} as a private {@code checkAndRecordExport} method that
 * was only ever invoked via {@code this.checkAndRecordDownload(...)}. Because
 * it was a self-invocation, Spring's proxy was bypassed and the
 * {@code @Transactional} annotation on it did nothing — the count and the
 * insert ran in separate transactions. Two concurrent downloads could both
 * observe {@code used = limit - 1} and both insert, exceeding the free cap.
 * The limits were also duplicated as literal {@code 3}/{@code 999} in three
 * places, and one constant ({@code FREE_LIMIT}) was declared but unused.</p>
 *
 * <p>This class is a separate Spring bean, so every call below goes through a
 * proxy and is genuinely transactional. {@link #recordExport} additionally
 * takes a pessimistic lock on the owning user row, making the count-then-insert
 * sequence atomic across concurrent requests.</p>
 *
 * <p>Quota is recorded only after a download's bytes have been produced
 * successfully (see {@code ExportService}), so a failed render no longer burns
 * one of the user's daily exports.</p>
 */
@Service
public class ExportQuotaService {

    /** Free users get this many exports per rolling 24 hours. */
    public static final int FREE_DAILY_LIMIT = 3;

    /**
     * Display cap for premium users. Premium is not actually metered, but the
     * status DTO needs a number for the UI ("X of Y used"); this was
     * previously hardcoded as 999 in three separate expressions.
     */
    public static final int PREMIUM_DAILY_LIMIT = 999;

    private static final String LIMIT_MESSAGE =
            "Free export limit reached (" + FREE_DAILY_LIMIT
                    + " per day). Upgrade to premium for unlimited exports.";

    @Autowired private ExportHistoryRepository exportHistoryRepository;
    @Autowired private UserRepository          userRepository;

    /**
     * Pure, side-effect-free quota snapshot used by GET /api/export/status and
     * by the legacy POST /api/export/record compatibility endpoint. Never
     * throws and never records anything.
     */
    public ExportStatusResponse status(User user) {
        long used  = countRecent(user.getId());
        long limit = limitFor(user);
        boolean ok = user.isPremium() || used < FREE_DAILY_LIMIT;
        return ExportStatusResponse.builder()
                .canExport(ok)
                .reason(ok ? "OK" : LIMIT_MESSAGE)
                .exportsToday(used)
                .exportLimit(limit)
                .remainingFreeExports(user.isPremium()
                        ? PREMIUM_DAILY_LIMIT
                        : Math.max(0, limit - used))
                .build();
    }

    /**
     * Cheap pre-flight check used before doing expensive render work (e.g. the
     * headless-Chromium PDF call). This is only an optimisation: the
     * authoritative check happens under the row lock in
     * {@link #recordExport}. Premium users are never blocked.
     */
    public void assertWithinQuota(User user) {
        if (user != null && user.isPremium()) return;
        long used = countRecent(user == null ? null : user.getId());
        if (used >= FREE_DAILY_LIMIT) throw new RateLimitException(LIMIT_MESSAGE);
    }

    /**
     * Records exactly one successful export and enforces the quota atomically.
     *
     * <p>The user row is locked first ({@code SELECT ... FOR UPDATE}), so
     * concurrent exports for the same user queue behind each other instead of
     * racing the count. If the lock cannot be taken the call fails closed
     * rather than silently allowing an uncounted download.</p>
     *
     * @throws RateLimitException       when a free user is already at the limit
     * @throws ResourceNotFoundException when the user row no longer exists
     */
    @Transactional
    public void recordExport(User user, Long resumeId, String format) {
        if (user == null || user.getId() == null) {
            throw new ResourceNotFoundException("User not found");
        }
        User locked = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long used = countRecent(locked.getId());
        if (!locked.isPremium() && used >= FREE_DAILY_LIMIT) {
            throw new RateLimitException(LIMIT_MESSAGE);
        }

        exportHistoryRepository.save(ExportHistory.builder()
                .userId(locked.getId())
                .resumeId(resumeId)
                .exportFormat(format == null ? "UNKNOWN" : format.toUpperCase())
                .build());
    }

    private long limitFor(User user) {
        return user.isPremium() ? PREMIUM_DAILY_LIMIT : FREE_DAILY_LIMIT;
    }

    private long countRecent(Long userId) {
        if (userId == null) return 0L;
        return exportHistoryRepository.countRecentExports(userId, LocalDateTime.now().minusDays(1));
    }
}
