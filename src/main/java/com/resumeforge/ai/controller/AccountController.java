package com.resumeforge.ai.controller;

import com.resumeforge.ai.service.AccountDeletionService;
import com.resumeforge.ai.service.CurrentUserService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountDeletionService deletionService;
    private final CurrentUserService     currentUserService;

    public AccountController(AccountDeletionService deletionService,
                              CurrentUserService currentUserService) {
        this.deletionService    = deletionService;
        this.currentUserService = currentUserService;
    }

    /**
     * GET /api/account/export
     *
     * Returns all user data as a downloadable JSON file.
     * GDPR data portability right.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportData() {
        var user  = currentUserService.getCurrentUser();
        byte[] data = deletionService.exportUserData(user);

        String filename = "resumeforgeai_data_" + user.getId() + ".json";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(data);
    }

    /**
     * DELETE /api/account
     *
     * Permanently deletes the authenticated user's account and all data.
     * Irreversible. Client should prompt for confirmation before calling.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount() {
        deletionService.deleteAccount(currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
