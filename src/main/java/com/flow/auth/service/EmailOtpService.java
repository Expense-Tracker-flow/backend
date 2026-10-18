package com.flow.auth.service;

import com.flow.auth.dto.SendOtpRequest;
import com.flow.auth.dto.VerifyOtpRequest;
import com.flow.auth.entity.EmailOtp;
import com.flow.auth.repository.EmailOtpRepository;
import com.flow.common.exception.BadRequestException;
import com.flow.common.exception.ErrorCode;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final EmailOtpRepository emailOtpRepository;
    private final UserRepository userRepository;
    private final OtpEmailSender otpEmailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String purpose = request.getPurpose() != null ? request.getPurpose().toUpperCase() : "REGISTRATION";

        // 1. If registering, block duplicate registered email
        if ("REGISTRATION".equalsIgnoreCase(purpose) && userRepository.existsByEmail(email)) {
            throw new BadRequestException(ErrorCode.USER_ALREADY_EXISTS, "An account with this email address already exists. Please sign in.");
        }

        issueOtp(email, purpose);
    }

    /**
     * Sends a password reset code. Callers must check the account exists first; this method does not.
     * Skips sending if a code was issued within the cooldown window.
     */
    @Transactional
    public void issuePasswordResetOtp(String email) {
        boolean recentlySent = emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, PURPOSE_PASSWORD_RESET)
                .map(otp -> otp.getCreatedAt().isAfter(Instant.now().minusSeconds(RESEND_COOLDOWN_SECONDS)))
                .orElse(false);

        if (recentlySent) {
            log.info("Password reset code for [{}] was issued within the last {}s; not sending another", email, RESEND_COOLDOWN_SECONDS);
            return;
        }
        issueOtp(email, PURPOSE_PASSWORD_RESET);
    }

    private void issueOtp(String email, String purpose) {
        // Generate secure 6-digit numeric OTP
        int number = secureRandom.nextInt(900000) + 100000;
        String otpCode = String.valueOf(number);

        Instant expiryDate = Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        EmailOtp emailOtp = EmailOtp.builder()
                .email(email)
                .otpCode(otpCode)
                .purpose(purpose)
                .expiryDate(expiryDate)
                .verified(false)
                .build();

        emailOtpRepository.save(emailOtp);

        // Reset codes grant account access, so they are never written to logs
        if (!PURPOSE_PASSWORD_RESET.equals(purpose)) {
            log.info("=================================================");
            log.info("📧 FLOW EMAIL OTP FOR [{}]: [{}] (Expires in {}m)", email, otpCode, OTP_EXPIRY_MINUTES);
            log.info("=================================================");
        }

        // Runs in the background, so the request does not wait for SMTP
        otpEmailSender.sendOtpEmail(email, otpCode, purpose);
    }

    @Transactional
    public boolean verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String purpose = request.getPurpose() != null ? request.getPurpose().toUpperCase() : "REGISTRATION";
        String otpCode = request.getOtpCode().trim();

        EmailOtp emailOtp = emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_REQUEST, "No verification code was sent for this email address."));

        if (emailOtp.isExpired()) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST, "Verification code has expired. Please request a new code.");
        }

        if (!emailOtp.getOtpCode().equals(otpCode)) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST, "Invalid verification code. Please check and try again.");
        }

        emailOtp.setVerified(true);
        emailOtpRepository.save(emailOtp);
        log.info("Email [{}] successfully verified with OTP for purpose [{}]", email, purpose);
        return true;
    }

    /**
     * Checks a password reset code. Returns false for a wrong code, an expired code, or too many attempts.
     * Returns instead of throwing so the failed-attempt count is committed to the database.
     */
    @Transactional
    public boolean checkPasswordResetOtp(String email, String otpCode) {
        return emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, PURPOSE_PASSWORD_RESET)
                .map(emailOtp -> {
                    if (emailOtp.isExpired() || emailOtp.getAttempts() >= MAX_VERIFY_ATTEMPTS) {
                        return false;
                    }
                    if (emailOtp.getOtpCode().equals(otpCode)) {
                        return true;
                    }
                    emailOtp.setAttempts(emailOtp.getAttempts() + 1);
                    emailOtpRepository.save(emailOtp);
                    return false;
                })
                .orElse(false);
    }

    @Transactional
    public void deletePasswordResetOtps(String email) {
        emailOtpRepository.deleteByEmailAndPurpose(email, PURPOSE_PASSWORD_RESET);
    }

    @Transactional(readOnly = true)
    public void validateEmailIsVerified(String email, String purpose, String otpCode) {
        String cleanEmail = email.trim().toLowerCase();

        // If otpCode is provided directly, verify it
        if (otpCode != null && !otpCode.isBlank()) {
            verifyOtp(VerifyOtpRequest.builder()
                    .email(cleanEmail)
                    .otpCode(otpCode.trim())
                    .purpose(purpose)
                    .build());
            return;
        }

        // Otherwise verify prior verification status
        EmailOtp emailOtp = emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(cleanEmail, purpose)
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_REQUEST, "Please verify your email address before continuing."));

        if (!emailOtp.isVerified()) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST, "Email address has not been verified. Please enter your verification code.");
        }
    }
}
