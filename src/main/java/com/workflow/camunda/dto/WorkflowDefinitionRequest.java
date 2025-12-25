package com.workflow.camunda.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a workflow definition dynamically
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionRequest {

    /**
     * Process definition key (used to start the process)
     * Must be alphanumeric with dashes/underscores, no spaces
     */
    @NotBlank(message = "Process key is required")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "Process key must start with letter and contain only alphanumeric, dash, underscore")
    private String processKey;

    /**
     * Human-readable process name
     */
    @NotBlank(message = "Process name is required")
    private String processName;

    /**
     * Optional description of the workflow
     */
    private String description;

    /**
     * History time to live in days (default: 180)
     */
    @Builder.Default
    private Integer historyTimeToLive = 180;

    /**
     * List of steps in the workflow
     */
    @NotEmpty(message = "At least one step is required")
    @Valid
    private List<StepDefinition> steps;
}
