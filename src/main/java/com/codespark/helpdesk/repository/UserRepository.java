package com.codespark.helpdesk.repository;

import com.codespark.helpdesk.model.Role;
import com.codespark.helpdesk.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleAndCategory_IdAndActiveTrue(Role role, Long categoryId);

    @EntityGraph(attributePaths = "category")
    List<User> findByRoleOrderByIdAsc(Role role);

    @EntityGraph(attributePaths = "category")
    List<User> findAllByOrderByIdAsc();
}
