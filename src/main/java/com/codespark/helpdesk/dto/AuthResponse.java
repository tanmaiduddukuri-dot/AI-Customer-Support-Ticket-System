package com.codespark.helpdesk.dto;

import java.time.LocalDateTime;

public record AuthResponse(String token, LocalDateTime expiresAt, UserResponse user) {}
