package com.resumeforge.ai.dto;

/**
 * Payload sent by the frontend after Razorpay Payment Link redirect.
 *
 * Razorpay appends these query params to the callback URL on success:
 *   razorpay_payment_id           — e.g. pay_ABC123
 *   razorpay_payment_link_id      — e.g. plink_ABC123
 *   razorpay_payment_link_reference_id — the reference_id you set when creating the link
 *   razorpay_payment_link_status  — "paid"
 *   razorpay_signature            — HMAC-SHA256 of the above fields
 *
 * The frontend must collect these from the URL and POST them here.
 * The backend NEVER trusts any "status" field sent by the client.
 * Premium is only granted after HMAC signature is verified.
 */
public record PaymentVerifyRequest(
        String razorpayPaymentId,
        String razorpayPaymentLinkId,
        String razorpayPaymentLinkReferenceId,
        String razorpayPaymentLinkStatus,
        String razorpaySignature
) {}
