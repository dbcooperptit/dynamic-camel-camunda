package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for deploying BPMN from XML string
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmnDeployRequest {

    /**
     * Name for the deployment (will be used as filename.bpmn)
     */
    private String name;

    /**
     * BPMN XML content
     */
    private String xml;

    /**
     * Optional: Enable duplicate filtering (skip if same content already deployed)
     */
    @Builder.Default
    private boolean enableDuplicateFiltering = false;
}
