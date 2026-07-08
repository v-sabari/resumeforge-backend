package com.resumeforge.ai.util;

import java.security.SecureRandom;

public class OtpUtil {
    private static final SecureRandom random = new SecureRandom();

    public static String generateOtp() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
