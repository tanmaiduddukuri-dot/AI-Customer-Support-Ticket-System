package com.codespark.helpdesk.config;

import com.codespark.helpdesk.dto.CommentRequest;
import com.codespark.helpdesk.dto.CreateTicketRequest;
import com.codespark.helpdesk.dto.StatusUpdateRequest;
import com.codespark.helpdesk.dto.TicketResponse;
import com.codespark.helpdesk.model.*;
import com.codespark.helpdesk.repository.CategoryRepository;
import com.codespark.helpdesk.repository.TicketRepository;
import com.codespark.helpdesk.repository.UserRepository;
import com.codespark.helpdesk.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Fills an empty database with demo data at startup. Each part only runs once.
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepo;
    private final UserRepository userRepo;
    private final TicketRepository ticketRepo;
    private final TicketService ticketService;
    private final PasswordEncoder encoder;
    private final PlatformTransactionManager txManager;

    @Override
    public void run(String... args) {
        if (categoryRepo.count() == 0) {
            seedCategoriesAndUsers();
        }
        if (ticketRepo.count() == 0) {
            seedTickets();
        }
    }

    private void seedCategoriesAndUsers() {
        List<Category> saved = categoryRepo.saveAll(List.of(
                Category.builder().name("Billing").slaHours(12).build(),
                Category.builder().name("Technical").slaHours(24).build(),
                Category.builder().name("Account").slaHours(24).build(),
                Category.builder().name("Delivery").slaHours(24).build(),
                Category.builder().name("General").slaHours(48).build()));

        Map<String, Category> cat = saved.stream()
                .collect(Collectors.toMap(Category::getName, Function.identity()));
        String pw = encoder.encode("password123");

        userRepo.saveAll(List.of(
                user("Admin", "admin@helpdesk.com", pw, Role.ADMIN, null),
                user("Ravi (Billing)", "ravi@helpdesk.com", pw, Role.AGENT, cat.get("Billing")),
                user("Sneha (Billing)", "sneha@helpdesk.com", pw, Role.AGENT, cat.get("Billing")),
                user("Arjun (Technical)", "arjun@helpdesk.com", pw, Role.AGENT, cat.get("Technical")),
                user("Priya (Technical)", "priya@helpdesk.com", pw, Role.AGENT, cat.get("Technical")),
                user("Kiran (Account)", "kiran@helpdesk.com", pw, Role.AGENT, cat.get("Account")),
                user("Meena (Delivery)", "meena@helpdesk.com", pw, Role.AGENT, cat.get("Delivery")),
                user("Vikram (General)", "vikram@helpdesk.com", pw, Role.AGENT, cat.get("General")),
                user("Anil Customer", "anil@gmail.com", pw, Role.CUSTOMER, null),
                user("Divya Customer", "divya@gmail.com", pw, Role.CUSTOMER, null)));

        System.out.println(">>> Seeded 5 categories and 10 users (password: password123)");
    }

    // Tickets go through TicketService, so they get real SLA dates, auto-assignment and history
    private void seedTickets() {
        Long anil = userId("anil@gmail.com");
        Long divya = userId("divya@gmail.com");

        TicketResponse t1 = ticketService.create(anil, req("Charged twice for my order",
                "I was charged twice for order #4521 last week. Please refund the extra payment.",
                "Billing", Priority.HIGH));
        TicketResponse t2 = ticketService.create(anil, req("App crashes on login",
                "The mobile app closes immediately after I enter my password and tap login.",
                "Technical", Priority.HIGH));
        TicketResponse t3 = ticketService.create(divya, req("Cannot reset my password",
                "The password reset email never arrives in my inbox or spam folder.",
                "Account", Priority.MEDIUM));
        TicketResponse t4 = ticketService.create(divya, req("Package not delivered",
                "My package was marked as shipped five days ago but tracking has not updated since.",
                "Delivery", Priority.HIGH));
        TicketResponse t5 = ticketService.create(anil, req("Refund still not received",
                "I returned my order two weeks ago and the refund has still not reached my account.",
                "Billing", Priority.MEDIUM));
        ticketService.create(divya, req("How do I change my address?",
                "I moved recently and want to update the delivery address on my account.",
                "General", Priority.LOW));

        // t2: the agent starts work and talks to the customer
        Long t2Agent = t2.agent().id();
        ticketService.updateStatus(t2Agent, t2.id(), new StatusUpdateRequest(Status.IN_PROGRESS, "Investigating the crash"));
        ticketService.addComment(t2Agent, t2.id(), new CommentRequest(
                "Thanks for reporting this. Which phone model and app version are you using?"));
        ticketService.addComment(anil, t2.id(), new CommentRequest("Samsung Galaxy S21, app version 3.2.1."));

        // t3: worked on and resolved
        Long t3Agent = t3.agent().id();
        ticketService.updateStatus(t3Agent, t3.id(), new StatusUpdateRequest(Status.IN_PROGRESS, null));
        ticketService.addComment(t3Agent, t3.id(), new CommentRequest(
                "Your email address had a typo. I have corrected it, so please try the reset again."));
        ticketService.updateStatus(t3Agent, t3.id(), new StatusUpdateRequest(Status.RESOLVED, "Email address corrected"));

        // t4 and t5: pretend they were raised earlier, so they are past their SLA for the demo
        backdate(t4.id(), 40);
        backdate(t5.id(), 30);

        System.out.println(">>> Seeded 6 demo tickets (ticket " + t1.id() + " is the 'charged twice' example)");
    }

    // Shifts a ticket and its history back in time, keeping the same SLA length
    private void backdate(Long ticketId, int hours) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Ticket t = ticketRepo.findById(ticketId).orElseThrow();
            Duration sla = Duration.between(t.getCreatedAt(), t.getDueAt());
            t.setCreatedAt(t.getCreatedAt().minusHours(hours));
            t.setDueAt(t.getCreatedAt().plus(sla));
            t.getStatusHistory().forEach(h -> h.setChangedAt(h.getChangedAt().minusHours(hours)));
        });
    }

    private CreateTicketRequest req(String title, String description, String category, Priority priority) {
        Long categoryId = categoryRepo.findByNameIgnoreCase(category).orElseThrow().getId();
        return new CreateTicketRequest(title, description, categoryId, priority);
    }

    private Long userId(String email) {
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    private User user(String name, String email, String pw, Role role, Category category) {
        return User.builder().name(name).email(email).password(pw)
                .role(role).category(category).build();
    }
}
