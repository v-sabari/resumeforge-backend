package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.dto.ContactRequest;
import com.resumeforge.ai.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping
    public ResponseEntity<ApiResponse> submitContact(@Valid @RequestBody ContactRequest request) {
        return ResponseEntity.ok(contactService.submitContact(request));
    }
}
