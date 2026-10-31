package com.codespark.helpdesk.repository;

import com.codespark.helpdesk.model.Role;
import com.codespark.helpdesk.model.Status;
import com.codespark.helpdesk.model.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // @EntityGraph loads category, customer and agent in the same query (avoids N+1 queries)
    @EntityGraph(attributePaths = {"category", "customer", "agent"})
    List<Ticket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"category", "customer", "agent"})
    List<Ticket> findByAgent_IdOrderByCreatedAtDesc(Long agentId);

    @EntityGraph(attributePaths = {"category", "customer", "agent"})
    List<Ticket> findAllByOrderByCreatedAtDesc();

    // SLA breach check: due date has passed and the ticket is not resolved/closed
    @EntityGraph(attributePaths = {"category", "customer", "agent"})
    List<Ticket> findByDueAtBeforeAndStatusNotInOrderByDueAtAsc(LocalDateTime now, Collection<Status> doneStatuses);

    // Least-loaded agent: every active agent in the category with their count of active tickets.
    // LEFT JOIN keeps agents who have zero tickets. Lowest count first; ties go to the lower id.
    @Query("""
            select u.id as agentId, u.name as agentName, count(t.id) as openCount
            from User u
            left join Ticket t on t.agent = u and t.status in :activeStatuses
            where u.role = :role and u.category.id = :categoryId and u.active = true
            group by u.id, u.name
            order by count(t.id) asc, u.id asc
            """)
    List<AgentLoad> findAgentLoads(@Param("categoryId") Long categoryId,
                                   @Param("role") Role role,
                                   @Param("activeStatuses") Collection<Status> activeStatuses);
}
