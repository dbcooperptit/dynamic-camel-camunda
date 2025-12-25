package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * DTO for listing deployments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentDto {

    private String id;
    private String name;
    private String source;
    private Date deploymentTime;
    private String tenantId;
    private java.util.List<String> processDefinitionKeys;
    private String status;
}
