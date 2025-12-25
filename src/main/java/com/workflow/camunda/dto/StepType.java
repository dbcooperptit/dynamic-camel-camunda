package com.workflow.camunda.dto;

/**
 * Enum defining types of workflow steps
 */
public enum StepType {
    START_EVENT,
    END_EVENT,
    USER_TASK,
    SERVICE_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY
}
