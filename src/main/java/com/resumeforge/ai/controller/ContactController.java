package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ContactRequest;
import com.resumeforge.ai.dto.MessageResponse;
import com.resumeforge.ai.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final EmailService emailService;

    public ContactController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping
    public MessageResponse sendContactMessage(@Valid @RequestBody ContactRequest request) {
        emailService.sendContactMessage(
                request.name(),
                request.email(),
                request.subject(),
                request.message()
        );
        return new MessageResponse("Your message has been sent successfully");
    }
}