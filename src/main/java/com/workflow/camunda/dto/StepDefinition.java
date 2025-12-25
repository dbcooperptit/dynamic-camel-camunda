package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO defining a single step in the workflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {

    /**
     * Unique identifier for this step (e.g., "task1", "gateway1")
     */
    private String id;

    /**
     * Display name for this step
     */
    private String name;

    /**
     * Type of step: USER_TASK, SERVICE_TASK, EXCLUSIVE_GATEWAY, etc.
     */
    private StepType type;

    /**
     * Assignee for USER_TASK (e.g., "admin", "${initiator}")
     */
    private String assignee;

    /**
     * Candidate groups for USER_TASK (e.g., ["managers", "approvers"])
     */
    private List<String> candidateGroups;

    /**
     * Delegate expression for SERVICE_TASK (e.g., "${emailService}")
     */
    private String delegateExpression;

    /**
     * Java class for SERVICE_TASK delegate
     */
    private String delegateClass;

    /**
     * Condition expression for gateway branches (e.g., "${approved == true}")
     */
    private String conditionExpression;

    /**
     * Conditions for specific next steps (Key: nextStepId, Value: condition)
     * Overrides conditionExpression on target step if present.
     */
    private Map<String, String> nextStepConditions;

    /**
     * IDs of next steps (for sequence flows)
     */
    private List<String> nextSteps;

    /**
     * Whether this is the default flow from a gateway
     */
    private boolean defaultFlow;

    /**
     * Form key for user tasks
     */
    private String formKey;

    /**
     * Input/output variables for the step
     */
    private Map<String, Object> variables;
}
