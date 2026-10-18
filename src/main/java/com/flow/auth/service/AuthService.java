package com.flow.auth.service;

import com.flow.auth.dto.*;
import com.flow.auth.entity.RefreshToken;
import com.flow.auth.jwt.JwtService;
import com.flow.auth.security.UserPrincipal;
import com.flow.common.exception.ApiException;
import com.flow.common.exception.BadRequestException;
import com.flow.common.exception.ErrorCode;
import com.flow.user.entity.User;
import com.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailOtpService emailOtpService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException(ErrorCode.USER_ALREADY_EXISTS, "An account with this email already exists");
        }

        // Validate email verification OTP
        emailOtpService.validateEmailIsVerified(email, "REGISTRATION", request.getOtpCode());

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .currency(request.getCurrency() != null ? request.getCurrency().trim().toUpperCase() : "INR")
                .build();

        user = userRepository.save(user);
        log.info("Registered new user with id: {}", user.getId());

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(UserProfileResponse.from(user))
                .build();
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_FOUND, "User record not found"));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(UserProfileResponse.from(user))
                .build();
    }

    @Transactional
    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        RefreshToken rotatedRefreshToken = refreshTokenService.verifyAndRotate(request.getRefreshToken());
        User user = rotatedRefreshToken.getUser();

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(rotatedRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(UserProfileResponse.from(user))
                .build();
    }

    /**
     * Sends a reset code only when the account exists. The caller returns the same response either way,
     * so this method never reveals whether an email is registered.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            emailOtpService.issuePasswordResetOtp(normalizedEmail);
        }
    }

    // Keeps failed-attempt counts committed when the code is wrong (the exception is still returned to the client)
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String invalidCodeMessage = "Invalid or expired verification code. Please request a new one.";

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_REQUEST, invalidCodeMessage));

        if (!emailOtpService.checkPasswordResetOtp(email, request.getOtpCode().trim())) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST, invalidCodeMessage);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Sign out every existing session so a stolen refresh token stops working
        refreshTokenService.revokeAllForUser(user);
        emailOtpService.deletePasswordResetOtps(email);
        log.info("Password reset completed for user id: {}", user.getId());
    }

    @Transactional
    public void logout(String refreshToken, UUID userId) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        } else if (userId != null) {
            userRepository.findById(userId).ifPresent(refreshTokenService::revokeAllForUser);
        }
    }
}
