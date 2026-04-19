package com.resumeforge.ai.controller;

/**
 * Admin testimonials — requires ROLE_ADMIN (enforced by SecurityConfig /api/admin/**).
 */
class AdminTestimonialController {
    // Endpoints are registered under /api/admin/testimonials by AdminController delegation.
    // See AdminController for the actual @RequestMapping.
    // This class is intentionally empty — the methods live directly in AdminController
    // to keep all admin endpoints in one place.
}
