package com.flow.auth.service;

import com.flow.auth.entity.EmailOtp;
import com.flow.auth.repository.EmailOtpRepository;
import com.flow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {

    @Mock
    private EmailOtpRepository emailOtpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailOtpService emailOtpService;

    private EmailOtp otp(String code, int attempts, Instant expiry) {
        return EmailOtp.builder()
                .email("a@b.com")
                .otpCode(code)
                .purpose(EmailOtpService.PURPOSE_PASSWORD_RESET)
                .expiryDate(expiry)
                .attempts(attempts)
                .build();
    }

    private void stubLatest(EmailOtp otp) {
        when(emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc("a@b.com", EmailOtpService.PURPOSE_PASSWORD_RESET))
                .thenReturn(Optional.of(otp));
    }

    @Test
    void correctCode_isAccepted() {
        stubLatest(otp("123456", 0, Instant.now().plusSeconds(300)));

        assertThat(emailOtpService.checkPasswordResetOtp("a@b.com", "123456")).isTrue();
    }

    @Test
    void wrongCode_countsAttempt() {
        EmailOtp stored = otp("123456", 0, Instant.now().plusSeconds(300));
        stubLatest(stored);

        assertThat(emailOtpService.checkPasswordResetOtp("a@b.com", "000000")).isFalse();

        assertThat(stored.getAttempts()).isEqualTo(1);
        verify(emailOtpRepository).save(stored);
    }

    @Test
    void afterFiveWrongAttempts_correctCodeIsRejected() {
        stubLatest(otp("123456", 5, Instant.now().plusSeconds(300)));

        assertThat(emailOtpService.checkPasswordResetOtp("a@b.com", "123456")).isFalse();
        verify(emailOtpRepository, never()).save(any());
    }

    @Test
    void expiredCode_isRejected() {
        stubLatest(otp("123456", 0, Instant.now().minusSeconds(1)));

        assertThat(emailOtpService.checkPasswordResetOtp("a@b.com", "123456")).isFalse();
    }

    @Test
    void noCodeIssued_isRejected() {
        when(emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThat(emailOtpService.checkPasswordResetOtp("a@b.com", "123456")).isFalse();
    }
}
