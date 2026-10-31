package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required") Status status,
        @Size(max = 255, message = "Note must be at most 255 characters") String note
) {}
