package com.workflow.camunda.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for exposing deployed process definitions to the frontend.
 */
@Data
@Builder
public class ProcessDefinitionDto {
    private String id;
    private String key;
    private String name;
    private Integer version;
}
