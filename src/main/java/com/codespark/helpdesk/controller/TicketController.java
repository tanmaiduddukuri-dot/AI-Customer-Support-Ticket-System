package com.codespark.helpdesk.controller;

import com.codespark.helpdesk.dto.*;
import com.codespark.helpdesk.model.Status;
import com.codespark.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// The logged-in user's id comes from the token (see TokenAuthFilter).
// Per-ticket permission checks happen in TicketService based on that user's role.
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody CreateTicketRequest req) {
        return ticketService.create(userId, req);
    }

    // Customers see their own, agents see assigned, admins see all. Optional ?status=OPEN
    @GetMapping
    public List<TicketResponse> list(@AuthenticationPrincipal Long userId,
                                     @RequestParam(name = "status", required = false) Status status) {
        return ticketService.list(userId, status);
    }

    @GetMapping("/sla-breaches")
    public List<TicketResponse> slaBreaches(@AuthenticationPrincipal Long userId) {
        return ticketService.slaBreaches(userId);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id) {
        return ticketService.get(userId, id);
    }

    @PatchMapping("/{id}/status")
    public TicketResponse updateStatus(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id,
                                       @Valid @RequestBody StatusUpdateRequest req) {
        return ticketService.updateStatus(userId, id, req);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse addComment(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id,
                                     @Valid @RequestBody CommentRequest req) {
        return ticketService.addComment(userId, id, req);
    }

    // Admin only. Body {"agentId": 3} to pick an agent, or {"agentId": null} to auto-assign
    @PutMapping("/{id}/assign")
    public TicketResponse assign(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id,
                                 @RequestBody(required = false) AssignRequest req) {
        return ticketService.assign(userId, id, req);
    }
}
