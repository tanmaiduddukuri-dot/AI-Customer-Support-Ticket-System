package com.codespark.helpdesk.model;

// slaFactor scales the category's SLA: a CRITICAL billing ticket gets 1/4 of the normal time
public enum Priority {
    LOW(1.5), MEDIUM(1.0), HIGH(0.5), CRITICAL(0.25);

    private final double slaFactor;

    Priority(double slaFactor) {
        this.slaFactor = slaFactor;
    }

    public double getSlaFactor() {
        return slaFactor;
    }
}
