package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.ExportUsage;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.ExportUsageRepository;
import com.resumeforge.ai.repository.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {
    private static final int FREE_EXPORT_LIMIT = 2;

    private final ExportUsageRepository exportUsageRepository;
    private final ResumeRepository resumeRepository;
    private final AdService adService;

    public ExportService(ExportUsageRepository exportUsageRepository, ResumeRepository resumeRepository, AdService adService) {
        this.exportUsageRepository = exportUsageRepository;
        this.resumeRepository = resumeRepository;
        this.adService = adService;
    }

    public ExportAccessResponse checkAccess(User user) {
        int usedExports = (int) exportUsageRepository.countByUser(user);
        int remaining = Math.max(0, FREE_EXPORT_LIMIT - usedExports);
        boolean adCompleted = adService.hasUnusedCompletedAd(user);

        if (user.isPremium()) {
            return new ExportAccessResponse(true, true, false, true, usedExports, remaining,
                    "PREMIUM_ACTIVE", "Premium active. Unlimited exports available with no ads.");
        }
        if (usedExports >= FREE_EXPORT_LIMIT) {
            return new ExportAccessResponse(false, false, false, false, usedExports, 0,
                    "PAYMENT_REQUIRED", "Free export limit reached. Upgrade to premium for unlimited exports.");
        }
        if (adCompleted) {
            return new ExportAccessResponse(true, false, false, true, usedExports, remaining,
                    "FREE_EXPORT_UNLOCKED", "Ad completed. One free export is unlocked.");
        }
        return new ExportAccessResponse(false, false, true, false, usedExports, remaining,
                "AD_REQUIRED", "Complete an ad to unlock this free export.");
    }

    @Transactional
    public ExportRecordResponse record(User user, ExportRecordRequest request) {
        if (request != null && request.resumeId() != null) {
            resumeRepository.findByIdAndUser(request.resumeId(), user)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found"));
        }

        int usedExports = (int) exportUsageRepository.countByUser(user);
        if (user.isPremium()) {
            exportUsageRepository.save(ExportUsage.builder()
                    .user(user)
                    .exportCount(usedExports + 1)
                    .adCompleted(false)
                    .build());
            return new ExportRecordResponse(true, usedExports + 1, Math.max(0, FREE_EXPORT_LIMIT - (usedExports + 1)),
                    "Premium export recorded successfully.");
        }

        if (usedExports >= FREE_EXPORT_LIMIT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Free export limit reached. Premium required.");
        }

        boolean consumedAd = adService.consumeCompletedAd(user);
        if (!consumedAd) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Ad completion required before free export.");
        }

        exportUsageRepository.save(ExportUsage.builder()
                .user(user)
                .exportCount(usedExports + 1)
                .adCompleted(true)
                .build());
        return new ExportRecordResponse(true, usedExports + 1, Math.max(0, FREE_EXPORT_LIMIT - (usedExports + 1)),
                "Free export recorded successfully.");
    }

    public ExportStatusResponse status(User user) {
        ExportAccessResponse access = checkAccess(user);
        return new ExportStatusResponse(
                access.premium(),
                access.usedExports(),
                access.remainingFreeExports(),
                access.adCompleted(),
                access.allowed(),
                access.message()
        );
    }
}
