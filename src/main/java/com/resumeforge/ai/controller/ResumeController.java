package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ResumeDto;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;
    private final CurrentUserService currentUserService;

    public ResumeController(ResumeService resumeService, CurrentUserService currentUserService) {
        this.resumeService = resumeService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<ResumeDto> getAll() {
        return resumeService.getAll(currentUserService.getCurrentUser());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDto create(@Valid @RequestBody ResumeDto request) {
        return resumeService.create(currentUserService.getCurrentUser(), request);
    }

    @GetMapping("/{id}")
    public ResumeDto getById(@PathVariable Long id) {
        return resumeService.getById(id, currentUserService.getCurrentUser());
    }

    @PutMapping("/{id}")
    public ResumeDto update(@PathVariable Long id, @Valid @RequestBody ResumeDto request) {
        return resumeService.update(id, currentUserService.getCurrentUser(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        resumeService.delete(id, currentUserService.getCurrentUser());
    }
}
