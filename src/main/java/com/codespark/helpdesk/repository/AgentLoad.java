package com.codespark.helpdesk.repository;

// Projection: one row per agent with how many active tickets they currently hold
public interface AgentLoad {
    Long getAgentId();
    String getAgentName();
    Long getOpenCount();
}
