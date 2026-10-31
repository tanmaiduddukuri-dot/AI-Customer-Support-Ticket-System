package com.codespark.helpdesk.controller;

import com.codespark.helpdesk.dto.UserResponse;
import com.codespark.helpdesk.model.Role;
import com.codespark.helpdesk.model.User;
import com.codespark.helpdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;

    // GET /api/users  or  /api/users?role=AGENT
    @GetMapping
    public List<UserResponse> list(@RequestParam(name = "role", required = false) Role role) {
        List<User> users = role == null ? userRepo.findAllByOrderByIdAsc() : userRepo.findByRoleOrderByIdAsc(role);
        return users.stream().map(UserResponse::from).toList();
    }
}
