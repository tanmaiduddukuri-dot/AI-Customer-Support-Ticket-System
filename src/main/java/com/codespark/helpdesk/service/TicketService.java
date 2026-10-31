package com.codespark.helpdesk.service;

import com.codespark.helpdesk.dto.*;
import com.codespark.helpdesk.exception.BadRequestException;
import com.codespark.helpdesk.exception.ForbiddenException;
import com.codespark.helpdesk.exception.NotFoundException;
import com.codespark.helpdesk.model.*;
import com.codespark.helpdesk.repository.AgentLoad;
import com.codespark.helpdesk.repository.CategoryRepository;
import com.codespark.helpdesk.repository.TicketRepository;
import com.codespark.helpdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketService {

    // Tickets an agent is actively working on (used to measure workload)
    private static final List<Status> ACTIVE = List.of(Status.ASSIGNED, Status.IN_PROGRESS);
    private static final List<Status> DONE = List.of(Status.RESOLVED, Status.CLOSED);

    // The status workflow: which status can move to which
    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.OPEN, Set.of(Status.ASSIGNED),
            Status.ASSIGNED, Set.of(Status.IN_PROGRESS),
            Status.IN_PROGRESS, Set.of(Status.RESOLVED),
            Status.RESOLVED, Set.of(Status.CLOSED, Status.IN_PROGRESS),   // close, or reopen
            Status.CLOSED, Set.of());

    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;
    private final CategoryRepository categoryRepo;

    // ---------- Create ----------

    @Transactional
    public TicketResponse create(Long actorId, CreateTicketRequest req) {
        User customer = loadUser(actorId);
        if (customer.getRole() != Role.CUSTOMER) {
            throw new ForbiddenException("Only customers can raise tickets");
        }

        Category category = req.categoryId() != null
                ? categoryRepo.findById(req.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category " + req.categoryId() + " not found"))
                : categoryRepo.findByNameIgnoreCase("General")
                    .orElseThrow(() -> new NotFoundException("Default category 'General' is missing"));
        Priority priority = req.priority() != null ? req.priority() : Priority.MEDIUM;

        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = Ticket.builder()
                .title(req.title().trim())
                .description(req.description().trim())
                .category(category)
                .priority(priority)
                .status(Status.OPEN)
                .customer(customer)
                .createdAt(now)
                .dueAt(calculateDueAt(now, category, priority))
                .aiClassified(false)
                .build();
        ticket.addStatusChange(history(null, Status.OPEN, customer, "Ticket created"));

        autoAssign(ticket);   // stays OPEN if no agent is available
        return TicketResponse.detail(ticketRepo.saveAndFlush(ticket));
    }

    // Due time = category SLA x priority factor (e.g. Billing 12h x HIGH 0.5 = 6h)
    private LocalDateTime calculateDueAt(LocalDateTime created, Category category, Priority priority) {
        long minutes = Math.round(category.getSlaHours() * 60 * priority.getSlaFactor());
        return created.plusMinutes(minutes);
    }

    // ---------- Read ----------

    @Transactional(readOnly = true)
    public List<TicketResponse> list(Long actorId, Status statusFilter) {
        User actor = loadUser(actorId);
        List<Ticket> tickets = switch (actor.getRole()) {
            case CUSTOMER -> ticketRepo.findByCustomer_IdOrderByCreatedAtDesc(actor.getId());
            case AGENT -> ticketRepo.findByAgent_IdOrderByCreatedAtDesc(actor.getId());
            case ADMIN -> ticketRepo.findAllByOrderByCreatedAtDesc();
        };
        return tickets.stream()
                .filter(t -> statusFilter == null || t.getStatus() == statusFilter)
                .map(TicketResponse::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Long actorId, Long ticketId) {
        User actor = loadUser(actorId);
        Ticket ticket = loadTicket(ticketId);
        checkCanView(actor, ticket);
        return TicketResponse.detail(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> slaBreaches(Long actorId) {
        requireRole(loadUser(actorId), Role.ADMIN);
        return ticketRepo.findByDueAtBeforeAndStatusNotInOrderByDueAtAsc(LocalDateTime.now(), DONE)
                .stream().map(TicketResponse::summary).toList();
    }

    // ---------- Assign ----------

    @Transactional
    public TicketResponse assign(Long actorId, Long ticketId, AssignRequest req) {
        User admin = loadUser(actorId);
        requireRole(admin, Role.ADMIN);
        Ticket ticket = loadTicket(ticketId);
        if (DONE.contains(ticket.getStatus())) {
            throw new BadRequestException("Cannot reassign a " + ticket.getStatus() + " ticket");
        }

        if (req == null || req.agentId() == null) {
            if (!autoAssign(ticket)) {
                throw new BadRequestException("No active agent available for category "
                        + ticket.getCategory().getName());
            }
        } else {
            User agent = loadUser(req.agentId());
            if (agent.getRole() != Role.AGENT || !agent.isActive()) {
                throw new BadRequestException("User " + agent.getId() + " is not an active agent");
            }
            assignTo(ticket, agent, admin, "Assigned by admin to " + agent.getName());
        }
        return TicketResponse.detail(ticketRepo.saveAndFlush(ticket));
    }

    // Picks the active agent in the ticket's category with the fewest active tickets
    private boolean autoAssign(Ticket ticket) {
        List<AgentLoad> loads = ticketRepo.findAgentLoads(ticket.getCategory().getId(), Role.AGENT, ACTIVE);
        if (loads.isEmpty()) {
            return false;
        }
        AgentLoad best = loads.get(0);
        User agent = userRepo.getReferenceById(best.getAgentId());
        assignTo(ticket, agent, null, "Auto-assigned to " + best.getAgentName()
                + " (" + best.getOpenCount() + " active tickets)");
        return true;
    }

    private void assignTo(Ticket ticket, User agent, User by, String note) {
        Status current = ticket.getStatus();
        ticket.setAgent(agent);
        if (current == Status.OPEN) {
            ticket.setStatus(Status.ASSIGNED);
            ticket.addStatusChange(history(Status.OPEN, Status.ASSIGNED, by, note));
        } else {
            ticket.addStatusChange(history(current, current, by, note));   // reassignment, same status
        }
    }

    // ---------- Status workflow ----------

    @Transactional
    public TicketResponse updateStatus(Long actorId, Long ticketId, StatusUpdateRequest req) {
        User actor = loadUser(actorId);
        Ticket ticket = loadTicket(ticketId);
        Status from = ticket.getStatus();
        Status to = req.status();

        if (to == Status.ASSIGNED) {
            throw new BadRequestException("Use PUT /api/tickets/{id}/assign to assign a ticket");
        }
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new BadRequestException("Invalid status change " + from + " -> " + to
                    + ". Allowed from " + from + ": " + TRANSITIONS.get(from));
        }
        checkStatusPermission(actor, ticket, from, to);

        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(to);
        if (to == Status.RESOLVED) {
            ticket.setResolvedAt(now);
        }
        if (from == Status.RESOLVED && to == Status.IN_PROGRESS) {
            ticket.setResolvedAt(null);   // reopened
        }
        ticket.addStatusChange(history(from, to, actor, req.note()));
        return TicketResponse.detail(ticketRepo.saveAndFlush(ticket));
    }

    // Who may make which status change
    private void checkStatusPermission(User actor, Ticket ticket, Status from, Status to) {
        switch (actor.getRole()) {
            case ADMIN -> { /* admins may make any valid transition */ }
            case AGENT -> {
                if (!isAssignedAgent(actor, ticket)) {
                    throw new ForbiddenException("Only the assigned agent can update this ticket");
                }
                if (to == Status.CLOSED) {
                    throw new ForbiddenException("Agents cannot close tickets; the customer or an admin closes them");
                }
            }
            case CUSTOMER -> {
                if (!isOwner(actor, ticket)) {
                    throw new ForbiddenException("You can only update your own tickets");
                }
                if (from != Status.RESOLVED) {
                    throw new ForbiddenException("Customers can only close or reopen a resolved ticket");
                }
            }
        }
    }

    // ---------- Comments ----------

    @Transactional
    public TicketResponse addComment(Long actorId, Long ticketId, CommentRequest req) {
        User actor = loadUser(actorId);
        Ticket ticket = loadTicket(ticketId);
        checkCanView(actor, ticket);
        if (ticket.getStatus() == Status.CLOSED) {
            throw new BadRequestException("Cannot comment on a closed ticket");
        }
        ticket.addComment(TicketComment.builder()
                .author(actor)
                .message(req.message().trim())
                .createdAt(LocalDateTime.now())
                .build());
        return TicketResponse.detail(ticketRepo.saveAndFlush(ticket));
    }

    // ---------- Helpers ----------

    private void checkCanView(User actor, Ticket ticket) {
        boolean allowed = switch (actor.getRole()) {
            case ADMIN -> true;
            case AGENT -> isAssignedAgent(actor, ticket);
            case CUSTOMER -> isOwner(actor, ticket);
        };
        if (!allowed) {
            throw new ForbiddenException("You do not have access to ticket " + ticket.getId());
        }
    }

    private boolean isOwner(User actor, Ticket ticket) {
        return ticket.getCustomer().getId().equals(actor.getId());
    }

    private boolean isAssignedAgent(User actor, Ticket ticket) {
        return ticket.getAgent() != null && ticket.getAgent().getId().equals(actor.getId());
    }

    private void requireRole(User actor, Role role) {
        if (actor.getRole() != role) {
            throw new ForbiddenException("Only " + role + " users can do this");
        }
    }

    private User loadUser(Long id) {
        return userRepo.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    private Ticket loadTicket(Long id) {
        return ticketRepo.findById(id).orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
    }

    private StatusChange history(Status from, Status to, User by, String note) {
        return StatusChange.builder()
                .fromStatus(from).toStatus(to)
                .changedBy(by)
                .changedAt(LocalDateTime.now())
                .note(note)
                .build();
    }
}
