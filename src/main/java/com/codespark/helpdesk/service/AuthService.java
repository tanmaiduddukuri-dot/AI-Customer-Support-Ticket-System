package com.codespark.helpdesk.service;

import com.codespark.helpdesk.dto.AuthResponse;
import com.codespark.helpdesk.dto.LoginRequest;
import com.codespark.helpdesk.dto.RegisterRequest;
import com.codespark.helpdesk.dto.UserResponse;
import com.codespark.helpdesk.exception.BadRequestException;
import com.codespark.helpdesk.exception.NotFoundException;
import com.codespark.helpdesk.exception.UnauthorizedException;
import com.codespark.helpdesk.model.AuthToken;
import com.codespark.helpdesk.model.Role;
import com.codespark.helpdesk.model.User;
import com.codespark.helpdesk.repository.AuthTokenRepository;
import com.codespark.helpdesk.repository.UserRepository;
import com.codespark.helpdesk.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final AuthTokenRepository tokenRepo;
    private final PasswordEncoder encoder;

    // Sign-up always creates a CUSTOMER. Agents and admins are created by the organisation.
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = userRepo.save(User.builder()
                .name(req.name().trim())
                .email(email)
                .password(encoder.encode(req.password()))
                .role(Role.CUSTOMER)
                .build());
        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Same message for "no such email" and "wrong password", so attackers can't discover accounts
        User user = userRepo.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        tokenRepo.deleteByExpiresAtBefore(LocalDateTime.now());   // housekeeping
        return issueToken(user);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            tokenRepo.deleteByTokenHash(TokenHasher.sha256(authorizationHeader.substring(7).trim()));
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found")));
    }

    private AuthResponse issueToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(TOKEN_LIFETIME);
        tokenRepo.save(AuthToken.builder()
                .tokenHash(TokenHasher.sha256(rawToken))
                .user(user)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());
        return new AuthResponse(rawToken, expiresAt, UserResponse.from(user));
    }
}
