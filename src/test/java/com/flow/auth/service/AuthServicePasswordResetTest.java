package com.flow.auth.service;

import com.flow.auth.dto.ResetPasswordRequest;
import com.flow.auth.jwt.JwtService;
import com.flow.common.exception.BadRequestException;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordResetTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailOtpService emailOtpService;

    @InjectMocks
    private AuthService authService;

    private final User user = User.builder()
            .email("a@b.com")
            .passwordHash("old-hash")
            .fullName("Test User")
            .currency("INR")
            .build();

    private ResetPasswordRequest request(String email, String otp, String newPassword) {
        return new ResetPasswordRequest(email, otp, newPassword);
    }

    @Test
    void wrongCode_rejectsAndLeavesPasswordAndSessionsUntouched() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(emailOtpService.checkPasswordResetOtp("a@b.com", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.resetPassword(request("a@b.com", "000000", "newpass")))
                .isInstanceOf(BadRequestException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
        verify(emailOtpService, never()).deletePasswordResetOtps(any());
    }

    @Test
    void validCode_updatesPasswordRevokesSessionsAndDeletesCode() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(emailOtpService.checkPasswordResetOtp("a@b.com", "123456")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");

        authService.resetPassword(request("  A@B.com ", "123456", "newpass"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(user);
        verify(emailOtpService).deletePasswordResetOtps("a@b.com");
    }

    @Test
    void unknownEmail_rejectsWithoutCheckingAnyCode() {
        when(userRepository.findByEmail("ghost@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request("ghost@b.com", "123456", "newpass")))
                .isInstanceOf(BadRequestException.class);

        verify(emailOtpService, never()).checkPasswordResetOtp(any(), any());
    }

    @Test
    void requestReset_unknownEmail_sendsNothing() {
        when(userRepository.existsByEmail("ghost@b.com")).thenReturn(false);

        authService.requestPasswordReset("ghost@b.com");

        verify(emailOtpService, never()).issuePasswordResetOtp(any());
    }

    @Test
    void requestReset_knownEmail_issuesCode() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        authService.requestPasswordReset("A@B.com");

        verify(emailOtpService).issuePasswordResetOtp("a@b.com");
    }
}
