package com.codespark.helpdesk.dto;

import com.codespark.helpdesk.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
        String description,

        Long categoryId,     // optional: defaults to the General category
        Priority priority    // optional: defaults to MEDIUM
) {}
