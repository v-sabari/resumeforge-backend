    // ── Replace this method in AuthService.java ──────────────────────
    //
    // The existing toUserResponse() does not include `role` or `referralCode`.
    // Replace it with this version so the frontend can:
    //   1. Detect admin users and show the admin nav link (role)
    //   2. Pre-populate the referral hub without an extra API call (referralCode)
    //
    // Find the existing method:
    //   public UserResponse toUserResponse(User user) { ... }
    // Replace its body with:

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isPremium(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getRole() != null ? user.getRole() : "USER",
                user.getReferralCode()
        );
    }
