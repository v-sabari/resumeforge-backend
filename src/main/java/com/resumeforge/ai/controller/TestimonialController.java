package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.TestimonialDto;
import com.resumeforge.ai.service.TestimonialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public testimonials — no auth required. */
@RestController
@RequestMapping("/api/testimonials")
public class TestimonialController {

    private final TestimonialService svc;
    public TestimonialController(TestimonialService svc) { this.svc = svc; }

    @GetMapping
    public List<TestimonialDto> list() { return svc.listApproved(); }
}

