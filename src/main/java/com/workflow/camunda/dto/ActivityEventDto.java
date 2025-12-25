package com.workflow.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for activity execution events used in SSE streaming
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEventDto {

    /**
     * Process instance ID
     */
    private String processInstanceId;

    /**
     * Activity (node) ID in BPMN
     */
    private String activityId;

    /**
     * Activity name (human-readable)
     */
    private String activityName;

    /**
     * Activity type (startEvent, serviceTask, userTask, endEvent, exclusiveGateway,
     * etc.)
     */
    private String activityType;

    /**
     * Event type: "start" or "end"
     */
    private String eventType;

    /**
     * Timestamp when the event occurred
     */
    private LocalDateTime timestamp;

    /**
     * Process variables at this point
     */
    private Map<String, Object> variables;

    /**
     * Duration in milliseconds (only for "end" events)
     */
    private Long durationMs;

    /**
     * Optional message/log for this step
     */
    private String message;
}
