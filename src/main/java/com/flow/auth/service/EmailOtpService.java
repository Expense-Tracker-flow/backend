package com.flow.auth.service;

import com.flow.auth.dto.SendOtpRequest;
import com.flow.auth.dto.VerifyOtpRequest;
import com.flow.auth.entity.EmailOtp;
import com.flow.auth.repository.EmailOtpRepository;
import com.flow.common.exception.BadRequestException;
import com.flow.common.exception.ErrorCode;
import com.flow.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private final EmailOtpRepository emailOtpRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username:uksayoojjayarajan@gmail.com}")
    private String fromEmail;

    private static final int OTP_EXPIRY_MINUTES = 5;

    @Transactional
    public void sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String purpose = request.getPurpose() != null ? request.getPurpose().toUpperCase() : "REGISTRATION";

        // 1. If registering, block duplicate registered email
        if ("REGISTRATION".equalsIgnoreCase(purpose) && userRepository.existsByEmail(email)) {
            throw new BadRequestException(ErrorCode.USER_ALREADY_EXISTS, "An account with this email address already exists. Please sign in.");
        }

        // 2. Generate secure 6-digit numeric OTP
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

        // 3. Log OTP clearly
        log.info("=================================================");
        log.info("📧 FLOW EMAIL OTP FOR [{}]: [{}] (Expires in {}m)", email, otpCode, OTP_EXPIRY_MINUTES);
        log.info("=================================================");

        // 4. Send Email via SMTP
        sendEmail(email, otpCode, purpose);
    }

    private void sendEmail(String toEmail, String otpCode, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "FLOW Finance");
            helper.setTo(toEmail);
            helper.setSubject("Your FLOW Verification Code: " + otpCode);

            String htmlContent = """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px 24px; background-color: #ffffff; border: 1px solid #e4e4e7; border-radius: 16px;">
                    <div style="margin-bottom: 24px; text-align: center;">
                        <h1 style="font-size: 24px; font-weight: 800; letter-spacing: -0.5px; color: #18181b; margin: 0;">FLOW</h1>
                        <p style="font-size: 13px; color: #71717a; margin-top: 4px;">Personal AI Finance Tracker</p>
                    </div>
                    <div style="padding: 24px; background-color: #f4f4f5; border-radius: 12px; text-align: center; margin-bottom: 20px;">
                        <p style="font-size: 14px; color: #3f3f46; margin: 0 0 12px 0;">Use the following verification code for %s:</p>
                        <div style="font-size: 32px; font-weight: 800; letter-spacing: 6px; font-family: monospace; color: #4f46e5; margin: 8px 0;">%s</div>
                        <p style="font-size: 12px; color: #71717a; margin: 8px 0 0 0;">This code expires in 5 minutes.</p>
                    </div>
                    <p style="font-size: 12px; color: #a1a1aa; line-height: 1.5; text-align: center; margin: 0;">
                        If you did not request this verification code, you can safely ignore this email.
                    </p>
                </div>
            """.formatted(purpose, otpCode);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Successfully sent OTP email to [{}]", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email via SMTP to [{}]: {}", toEmail, e.getMessage());
            // Do not fail the request so user can still use logged OTP in case of network restriction
        }
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
