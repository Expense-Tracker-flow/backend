package com.flow.auth.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends OTP emails on a background thread so the HTTP request does not wait for the SMTP server.
 * Kept separate from EmailOtpService so the @Async call goes through the Spring proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpEmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:uksayoojjayarajan@gmail.com}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String purposeLabel = "PASSWORD_RESET".equals(purpose) ? "password reset" : purpose;

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
            """.formatted(purposeLabel, otpCode);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Successfully sent OTP email to [{}]", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email via SMTP to [{}]: {}", toEmail, e.getMessage());
            // Do not fail the request so user can still use logged OTP in case of network restriction
        }
    }
}
