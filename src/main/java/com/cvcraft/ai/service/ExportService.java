package com.cvcraft.ai.service;

import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.entity.*;
import com.cvcraft.ai.exception.ApiException;
import com.cvcraft.ai.repository.ExportUsageRepository;
import com.cvcraft.ai.repository.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {

    // BUG FIX: Must match frontend FREE_EXPORT_LIMIT = 2 (was inconsistent)
    private static final int FREE_EXPORT_LIMIT = 2;

    private final ExportUsageRepository exportUsageRepository;
    private final ResumeRepository      resumeRepository;

    public ExportService(ExportUsageRepository exportUsageRepository,
                         ResumeRepository resumeRepository) {
        this.exportUsageRepository = exportUsageRepository;
        this.resumeRepository      = resumeRepository;
    }

    public ExportAccessResponse checkAccess(User user) {
        int used      = (int) exportUsageRepository.countByUser(user);
        int remaining = Math.max(0, FREE_EXPORT_LIMIT - used);

        if (user.isPremium()) {
            return new ExportAccessResponse(true, true, false, false, used, remaining,
                    "PREMIUM_ACTIVE", "Premium active — unlimited exports available.");
        }
        if (used < FREE_EXPORT_LIMIT) {
            return new ExportAccessResponse(true, false, false, false, used, remaining,
                    "FREE_EXPORT_AVAILABLE", "Free export available. " + remaining + " remaining.");
        }
        return new ExportAccessResponse(false, false, false, false, used, 0,
                "PAYMENT_REQUIRED", "Free export limit reached. Upgrade to Premium for unlimited exports.");
    }

    @Transactional
    public ExportRecordResponse record(User user, ExportRecordRequest request) {
        // Validate resume belongs to this user
        if (request != null && request.resumeId() != null) {
            resumeRepository.findByIdAndUser(request.resumeId(), user)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found"));
        }

        int used = (int) exportUsageRepository.countByUser(user);

        if (!user.isPremium() && used >= FREE_EXPORT_LIMIT) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Free export limit reached. Upgrade to Premium for unlimited exports.");
        }

        exportUsageRepository.save(ExportUsage.builder()
                .user(user)
                .exportCount(used + 1)
                .adCompleted(false)
                .build());

        int newUsed = used + 1;
        int newRemaining = user.isPremium() ? Integer.MAX_VALUE : Math.max(0, FREE_EXPORT_LIMIT - newUsed);
        String msg = user.isPremium()
                ? "Premium export recorded."
                : "Free export recorded. " + newRemaining + " free exports remaining.";

        return new ExportRecordResponse(true, newUsed, newRemaining, msg);
    }

    public ExportStatusResponse status(User user) {
        int used      = (int) exportUsageRepository.countByUser(user);
        int remaining = user.isPremium() ? Integer.MAX_VALUE : Math.max(0, FREE_EXPORT_LIMIT - used);
        boolean canExport = user.isPremium() || used < FREE_EXPORT_LIMIT;
        String msg = user.isPremium()
                ? "Premium active — unlimited exports."
                : (canExport ? remaining + " free exports remaining." : "Limit reached — upgrade to Premium.");

        return new ExportStatusResponse(user.isPremium(), used, remaining, false, canExport, msg);
    }
}
