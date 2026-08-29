package com.flow.auth.service;

import com.flow.auth.entity.RefreshToken;
import com.flow.auth.jwt.JwtService;
import com.flow.auth.repository.RefreshTokenRepository;
import com.flow.common.exception.ErrorCode;
import com.flow.common.exception.UnauthorizedException;
import com.flow.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Remove existing tokens for clean single/multi session management
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .revoked(false)
                .build();

        refreshToken = refreshTokenRepository.save(refreshToken);
        log.info("Created new refresh token for user id: {}", user.getId());
        return refreshToken;
    }

    @Transactional
    public RefreshToken verifyAndRotate(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token. Please sign in again."));

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            log.warn("Attempted use of revoked refresh token for user id: {}", refreshToken.getUser().getId());
            refreshTokenRepository.deleteByUser(refreshToken.getUser());
            throw new UnauthorizedException("Refresh token was revoked. Please log in again.");
        }

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token has expired. Please sign in again.");
        }

        // Token rotation: update with fresh token string and extend expiry
        refreshToken.setToken(UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()));
        refreshToken = refreshTokenRepository.save(refreshToken);

        log.info("Rotated refresh token for user id: {}", refreshToken.getUser().getId());
        return refreshToken;
    }

    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Revoked refresh token for user id: {}", rt.getUser().getId());
        });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.deleteByUser(user);
        log.info("Revoked all refresh tokens for user id: {}", user.getId());
    }
}
