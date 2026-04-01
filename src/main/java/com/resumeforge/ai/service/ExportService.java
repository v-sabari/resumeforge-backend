package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ExportAccessResponse;
import com.resumeforge.ai.dto.ExportRecordRequest;
import com.resumeforge.ai.dto.ExportRecordResponse;
import com.resumeforge.ai.dto.ExportStatusResponse;
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

    public ExportService(ExportUsageRepository exportUsageRepository, ResumeRepository resumeRepository) {
        this.exportUsageRepository = exportUsageRepository;
        this.resumeRepository = resumeRepository;
    }

    public ExportAccessResponse checkAccess(User user) {
        int usedExports = (int) exportUsageRepository.countByUser(user);
        int remaining = Math.max(0, FREE_EXPORT_LIMIT - usedExports);

        if (user.isPremium()) {
            return new ExportAccessResponse(
                    true,
                    true,
                    false,
                    false,
                    usedExports,
                    remaining,
                    "PREMIUM_ACTIVE",
                    "Premium active. Unlimited exports available."
            );
        }

        if (usedExports < FREE_EXPORT_LIMIT) {
            return new ExportAccessResponse(
                    true,
                    false,
                    false,
                    false,
                    usedExports,
                    remaining,
                    "FREE_EXPORT_AVAILABLE",
                    "Free export available."
            );
        }

        return new ExportAccessResponse(
                false,
                false,
                false,
                false,
                usedExports,
                0,
                "PAYMENT_REQUIRED",
                "Free export limit reached. Upgrade to Premium for more exports."
        );
    }

    @Transactional
    public ExportRecordResponse record(User user, ExportRecordRequest request) {
        if (request != null && request.resumeId() != null) {
            resumeRepository.findByIdAndUser(request.resumeId(), user)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found"));
        }

        int usedExports = (int) exportUsageRepository.countByUser(user);

        if (user.isPremium()) {
            exportUsageRepository.save(
                    ExportUsage.builder()
                            .user(user)
                            .exportCount(usedExports + 1)
                            .adCompleted(false)
                            .build()
            );

            return new ExportRecordResponse(
                    true,
                    usedExports + 1,
                    Math.max(0, FREE_EXPORT_LIMIT - (usedExports + 1)),
                    "Premium export recorded successfully."
            );
        }

        if (usedExports >= FREE_EXPORT_LIMIT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Free export limit reached. Premium required.");
        }

        exportUsageRepository.save(
                ExportUsage.builder()
                        .user(user)
                        .exportCount(usedExports + 1)
                        .adCompleted(false)
                        .build()
        );

        return new ExportRecordResponse(
                true,
                usedExports + 1,
                Math.max(0, FREE_EXPORT_LIMIT - (usedExports + 1)),
                "Free export recorded successfully."
        );
    }

    public ExportStatusResponse status(User user) {
        ExportAccessResponse access = checkAccess(user);
        return new ExportStatusResponse(
                access.premium(),
                access.usedExports(),
                access.remainingFreeExports(),
                false,
                access.allowed(),
                access.message()
        );
    }
}