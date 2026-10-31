package com.codespark.helpdesk.dto;

// agentId null = let the system auto-assign the least-loaded agent
public record AssignRequest(Long agentId) {}
