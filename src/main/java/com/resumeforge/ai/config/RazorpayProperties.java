package com.resumeforge.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay configuration properties.
 *
 * application.properties keys:
 *   app.razorpay.key-id
 *   app.razorpay.key-secret
 *   app.razorpay.payment-link
 *   app.razorpay.webhook-secret
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(
        String keyId,
        String keySecret,
        String paymentLink,
        String webhookSecret
) {}
