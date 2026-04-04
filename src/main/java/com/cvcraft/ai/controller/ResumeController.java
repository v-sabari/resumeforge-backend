package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.ResumeDto;
import com.cvcraft.ai.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;
    private final CurrentUserService currentUserService;
    public ResumeController(ResumeService r, CurrentUserService c) { this.resumeService = r; this.currentUserService = c; }
    @GetMapping  public List<ResumeDto> getAll() { return resumeService.getAll(currentUserService.getCurrentUser()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ResumeDto create(@RequestBody ResumeDto req) { return resumeService.create(currentUserService.getCurrentUser(), req); }
    @GetMapping("/{id}") public ResumeDto getById(@PathVariable Long id) { return resumeService.getById(id, currentUserService.getCurrentUser()); }
    @PutMapping("/{id}") public ResumeDto update(@PathVariable Long id, @RequestBody ResumeDto req) { return resumeService.update(id, currentUserService.getCurrentUser(), req); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { resumeService.delete(id, currentUserService.getCurrentUser()); }
}
