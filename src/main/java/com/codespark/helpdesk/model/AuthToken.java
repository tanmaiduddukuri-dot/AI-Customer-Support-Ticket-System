package com.codespark.helpdesk.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A login session. Only the SHA-256 hash of the token is stored, never the token itself.
@Entity
@Table(name = "auth_tokens", indexes = @Index(name = "idx_token_expires", columnList = "expires_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
