package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.TicketComment;

import java.time.LocalDateTime;

public record CommentResponse(Long id, UserSummary author, String message, LocalDateTime createdAt) {
    public static CommentResponse from(TicketComment c) {
        return new CommentResponse(c.getId(), UserSummary.from(c.getAuthor()), c.getMessage(), c.getCreatedAt());
    }
}
