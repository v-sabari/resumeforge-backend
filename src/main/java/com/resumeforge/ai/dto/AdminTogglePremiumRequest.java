package com.resumeforge.ai.dto;

public class AdminTogglePremiumRequest {
    private boolean premium;

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }
}