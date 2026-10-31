package com.codespark.helpdesk.config;

import com.codespark.helpdesk.repository.AuthTokenRepository;
import com.codespark.helpdesk.repository.UserRepository;
import com.codespark.helpdesk.security.TokenAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/*
 * Security:
 *  - Passwords are stored as BCrypt hashes.
 *  - Logging in issues a random token; only its SHA-256 hash is stored (auth_tokens table), valid 8 hours.
 *  - TokenAuthFilter checks the token on every request. No server session (stateless).
 *  - URL rules below block whole areas by role; TicketService adds the per-ticket rules
 *    (e.g. an agent can only update tickets assigned to them).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Loads users from our own users table (also stops Spring printing a generated password)
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepo) {
        return email -> userRepo.findByEmail(email)
                .map(u -> org.springframework.security.core.userdetails.User
                        .withUsername(u.getEmail())
                        .password(u.getPassword())
                        .roles(u.getRole().name())
                        .disabled(!u.isActive())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthTokenRepository tokenRepo) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Pages (they redirect to login themselves if there is no token)
                    .requestMatchers("/", "/index.html", "/login.html", "/favicon.ico", "/error").permitAll()
                    // Public auth endpoints
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                    // Admin-only areas
                    .requestMatchers("/api/users/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/tickets/*/assign").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/tickets/sla-breaches").hasRole("ADMIN")
                    // Only customers raise tickets
                    .requestMatchers(HttpMethod.POST, "/api/tickets").hasRole("CUSTOMER")
                    // Everything else needs a logged-in user
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) ->
                            writeError(res, 401, "Unauthorized", "Please log in to continue"))
                    .accessDeniedHandler((req, res, ex) ->
                            writeError(res, 403, "Forbidden", "Your role is not allowed to do this")))
            .addFilterBefore(new TokenAuthFilter(tokenRepo), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Same JSON shape as GlobalExceptionHandler
    private static void writeError(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"fieldErrors\":null}");
    }
}
