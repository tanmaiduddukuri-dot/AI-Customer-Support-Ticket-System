package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.User;

public record UserSummary(Long id, String name, String email) {
    public static UserSummary from(User u) {
        return u == null ? null : new UserSummary(u.getId(), u.getName(), u.getEmail());
    }
}
