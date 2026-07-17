package com.puduvandi.auth.service;

import com.puduvandi.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated from AuthService's main transaction on purpose: revoke-all-on-reuse
 * must commit even when the caller immediately throws (which would otherwise
 * roll back everything done inside that same transaction, including this).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenSecurityService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUserIndependently(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }
}
