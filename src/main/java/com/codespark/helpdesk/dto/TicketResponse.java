package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.Priority;
import com.codespark.helpdesk.model.Status;
import com.codespark.helpdesk.model.Ticket;

import java.time.LocalDateTime;
import java.util.List;

public record TicketResponse(
        Long id, String title, String description,
        Long categoryId, String categoryName,
        Priority priority, Status status,
        UserSummary customer, UserSummary agent,
        LocalDateTime createdAt, LocalDateTime dueAt, LocalDateTime resolvedAt,
        boolean slaBreached, boolean aiClassified,
        List<CommentResponse> comments, List<HistoryResponse> statusHistory) {

    // For lists: no comments/history (keeps list queries light)
    public static TicketResponse summary(Ticket t) {
        return build(t, List.of(), List.of());
    }

    // For a single ticket: everything
    public static TicketResponse detail(Ticket t) {
        return build(t,
                t.getComments().stream().map(CommentResponse::from).toList(),
                t.getStatusHistory().stream().map(HistoryResponse::from).toList());
    }

    private static TicketResponse build(Ticket t, List<CommentResponse> comments, List<HistoryResponse> history) {
        return new TicketResponse(
                t.getId(), t.getTitle(), t.getDescription(),
                t.getCategory().getId(), t.getCategory().getName(),
                t.getPriority(), t.getStatus(),
                UserSummary.from(t.getCustomer()), UserSummary.from(t.getAgent()),
                t.getCreatedAt(), t.getDueAt(), t.getResolvedAt(),
                isSlaBreached(t), t.isAiClassified(),
                comments, history);
    }

    // Breached = still open past the due time, or resolved after the due time
    private static boolean isSlaBreached(Ticket t) {
        LocalDateTime finished = t.getResolvedAt() != null ? t.getResolvedAt() : LocalDateTime.now();
        return finished.isAfter(t.getDueAt());
    }
}
