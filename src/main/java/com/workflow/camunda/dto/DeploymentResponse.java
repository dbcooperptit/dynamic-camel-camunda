package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Response DTO for deployment result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {

    /**
     * Camunda deployment ID
     */
    private String deploymentId;

    /**
     * Process definition ID
     */
    private String processDefinitionId;

    /**
     * Process definition key
     */
    private String processKey;

    /**
     * Process name
     */
    private String processName;

    /**
     * Deployment timestamp
     */
    private Date deployedAt;

    /**
     * Generated BPMN XML (optional, for preview)
     */
    private String bpmnXml;

    /**
     * Deployment status message
     */
    private String message;
}
