package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.TestimonialDto;
import com.resumeforge.ai.entity.Testimonial;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.TestimonialRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestimonialService {

    private final TestimonialRepository repo;

    public TestimonialService(TestimonialRepository repo) { this.repo = repo; }

    /** Public — only approved, ordered by displayOrder. */
    @Transactional(readOnly = true)
    public List<TestimonialDto> listApproved() {
        return repo.findByApprovedTrueOrderByDisplayOrderAscCreatedAtDesc()
                   .stream().map(this::toDto).toList();
    }

    /** Admin — all testimonials. */
    @Transactional(readOnly = true)
    public List<TestimonialDto> listAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    /** Admin — create. */
    @Transactional
    public TestimonialDto create(TestimonialDto req) {
        Testimonial t = Testimonial.builder()
                .authorName(req.authorName())
                .authorRole(req.authorRole())
                .quote(req.quote())
                .rating(Math.max(1, Math.min(5, req.rating())))
                .approved(req.approved())
                .displayOrder(req.displayOrder())
                .build();
        return toDto(repo.save(t));
    }

    /** Admin — approve / update display order / edit text. */
    @Transactional
    public TestimonialDto update(Long id, TestimonialDto req) {
        Testimonial t = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Testimonial not found"));
        if (req.authorName() != null) t.setAuthorName(req.authorName());
        if (req.authorRole() != null) t.setAuthorRole(req.authorRole());
        if (req.quote()       != null) t.setQuote(req.quote());
        t.setRating(Math.max(1, Math.min(5, req.rating())));
        t.setApproved(req.approved());
        t.setDisplayOrder(req.displayOrder());
        return toDto(repo.save(t));
    }

    /** Admin — delete. */
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id))
            throw new ApiException(HttpStatus.NOT_FOUND, "Testimonial not found");
        repo.deleteById(id);
    }

    private TestimonialDto toDto(Testimonial t) {
        return new TestimonialDto(t.getId(), t.getAuthorName(), t.getAuthorRole(),
                t.getQuote(), t.getRating(), t.isApproved(), t.getDisplayOrder(), t.getCreatedAt());
    }
}
