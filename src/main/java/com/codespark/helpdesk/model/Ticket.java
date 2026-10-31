package com.codespark.helpdesk.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_agent_status", columnList = "agent_id, status"),
        @Index(name = "idx_ticket_due", columnList = "due_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")           // NULL until the ticket is assigned
    private User agent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;             // createdAt + category.slaHours

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "ai_classified", nullable = false)
    private boolean aiClassified;            // true if Python chose category/priority

    @Version
    private Long version;                    // optimistic locking

    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketComment> comments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<StatusChange> statusHistory = new ArrayList<>();

    // Helper methods keep both sides of the relationship in sync
    public void addComment(TicketComment c) { c.setTicket(this); comments.add(c); }
    public void addStatusChange(StatusChange s) { s.setTicket(this); statusHistory.add(s); }
}
