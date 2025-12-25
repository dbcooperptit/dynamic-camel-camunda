package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {
    private String taskId;
    private String type; // e.g., "SAGA_NODE", "CAMEL_ROUTE", "CAMUNDA_TASK"
    private String status; // "STARTED", "COMPLETED", "FAILED"
    private String message;
    private long timestamp;

    // New fields for comprehensive step information
    private String nodeType; // e.g., "log", "transform", "filter", "serviceTask", "userTask"
    private String routeId; // Camel route ID
    private String processInstanceId; // Camunda process instance ID
    private String activityId; // Camunda activity ID
    private String activityName; // Human-readable name
    private Object result; // Step result/output (body, transformed data, etc.)
    private String error; // Error message if failed
    private Long durationMs; // Execution duration in milliseconds
}
