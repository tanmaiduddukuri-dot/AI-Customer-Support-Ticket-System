package com.codespark.helpdesk.repository;

import com.codespark.helpdesk.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    // A token is valid only if it exists and has not expired; the user is loaded in the same query
    @Query("select t from AuthToken t join fetch t.user where t.tokenHash = :hash and t.expiresAt > :now")
    Optional<AuthToken> findValid(@Param("hash") String hash, @Param("now") LocalDateTime now);

    void deleteByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime time);
}
