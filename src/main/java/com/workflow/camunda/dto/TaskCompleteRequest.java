package com.workflow.camunda.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for completing a task
 */
@Data
public class TaskCompleteRequest {
    private Map<String, Object> variables = new HashMap<>();
}
