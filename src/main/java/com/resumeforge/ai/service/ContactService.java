package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.dto.ContactRequest;
import com.resumeforge.ai.entity.ContactMessage;
import com.resumeforge.ai.repository.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    @Autowired
    private EmailService emailService;

    @Transactional
    public ApiResponse submitContact(ContactRequest request) {
        ContactMessage message = ContactMessage.builder()
                .name(request.getName())
                .email(request.getEmail())
                .message(request.getMessage())
                .status("NEW")
                .build();

        contactMessageRepository.save(message);

        emailService.sendContactNotification(
                request.getName(),
                request.getEmail(),
                request.getMessage()
        );

        return ApiResponse.success("Your message has been sent successfully");
    }
}
