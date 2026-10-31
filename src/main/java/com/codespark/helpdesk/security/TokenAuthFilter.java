package com.codespark.helpdesk.security;

import com.codespark.helpdesk.model.User;
import com.codespark.helpdesk.repository.AuthTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

// Runs on every request: reads "Authorization: Bearer <token>" and, if valid,
// tells Spring Security who the user is (principal = user id, authority = ROLE_<role>)
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AuthTokenRepository tokenRepo;

    public TokenAuthFilter(AuthTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String raw = header.substring(7).trim();
            tokenRepo.findValid(TokenHasher.sha256(raw), LocalDateTime.now()).ifPresent(token -> {
                User user = token.getUser();
                if (user.isActive()) {
                    var auth = new UsernamePasswordAuthenticationToken(user.getId(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        }
        chain.doFilter(request, response);
    }
}
