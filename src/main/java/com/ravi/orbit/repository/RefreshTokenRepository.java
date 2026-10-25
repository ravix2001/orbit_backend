package com.ravi.orbit.repository;

import com.ravi.orbit.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.token = :token AND rt.revoked = false")
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    void deleteByExpiryDateBefore(LocalDateTime expiryDateBefore);

}

