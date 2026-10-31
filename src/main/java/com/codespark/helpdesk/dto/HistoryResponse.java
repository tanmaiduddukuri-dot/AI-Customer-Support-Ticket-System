package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.Status;
import com.codespark.helpdesk.model.StatusChange;

import java.time.LocalDateTime;

public record HistoryResponse(Long id, Status fromStatus, Status toStatus,
                              String changedBy, LocalDateTime changedAt, String note) {
    public static HistoryResponse from(StatusChange s) {
        return new HistoryResponse(s.getId(), s.getFromStatus(), s.getToStatus(),
                s.getChangedBy() == null ? "System" : s.getChangedBy().getName(),
                s.getChangedAt(), s.getNote());
    }
}
