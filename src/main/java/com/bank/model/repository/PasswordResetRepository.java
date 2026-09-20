package com.bank.model.repository;

import com.bank.model.entity.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findLatestActiveToken(Long userId);
    Optional<PasswordResetToken> findByUserIdAndOtp(Long userId, String otpCode);
    void updateAttempts(Long tokenId, int attempts);
    void markAsUsed(Long tokenId);
    void invalidateAllForUser(Long userId);
}
