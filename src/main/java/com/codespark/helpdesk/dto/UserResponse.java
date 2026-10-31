package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.Role;
import com.codespark.helpdesk.model.User;

// Never expose the password hash: entities are always mapped to DTOs before leaving the API
public record UserResponse(Long id, String name, String email, Role role,
                           Long categoryId, String categoryName, boolean active) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                u.getCategory() == null ? null : u.getCategory().getId(),
                u.getCategory() == null ? null : u.getCategory().getName(),
                u.isActive());
    }
}
