package com.workflow.camunda.listener;

import com.workflow.camunda.dto.ActivityEventDto;
import com.workflow.camunda.dto.TaskEvent;
import com.workflow.camunda.service.ActivityStreamService;
import com.workflow.camunda.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global execution listener that captures all activity events
 * and broadcasts them via SSE for realtime visualization
 */
@Component("activityEventListener")
@RequiredArgsConstructor
@Slf4j
public class ActivityEventListener implements ExecutionListener {

    private final ActivityStreamService activityStreamService;
    private final NotificationService notificationService;

    // Track start times for duration calculation
    private final Map<String, Long> activityStartTimes = new ConcurrentHashMap<>();

    @Override
    public void notify(DelegateExecution execution) {
        String eventName = execution.getEventName();
        String activityId = execution.getCurrentActivityId();
        String activityName = execution.getCurrentActivityName();
        String processInstanceId = execution.getProcessInstanceId();

        // Skip if no activity ID (e.g., process-level events)
        if (activityId == null) {
            return;
        }

        // Determine activity type
        String activityType = determineActivityType(execution);

        // Determine event type
        String eventType = mapEventName(eventName);
        if (eventType == null) {
            return; // Unknown event type
        }

        // Get all process variables
        Map<String, Object> variables = new HashMap<>();
        try {
            variables = execution.getVariables();
        } catch (Exception e) {
            log.warn("Could not get variables: {}", e.getMessage());
        }

        // Track timing
        String activityKey = processInstanceId + ":" + activityId;
        Long durationMs = null;

        if ("start".equals(eventType)) {
            activityStartTimes.put(activityKey, System.currentTimeMillis());
        } else if ("end".equals(eventType)) {
            Long startTime = activityStartTimes.remove(activityKey);
            if (startTime != null) {
                durationMs = System.currentTimeMillis() - startTime;
            }
        }

        // Create and broadcast event to ActivityStreamService
        ActivityEventDto event = ActivityEventDto.builder()
                .processInstanceId(processInstanceId)
                .activityId(activityId)
                .activityName(activityName != null ? activityName : activityId)
                .activityType(activityType)
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .variables(variables)
                .durationMs(durationMs)
                .message(buildMessage(eventType, activityType, activityName))
                .build();

        activityStreamService.broadcastEvent(event);

        // Also send notification via NotificationService for frontend Toast
        // Only send for "end" events (COMPLETED) to avoid too many notifications
        if ("end".equals(eventType)) {
            sendCamundaNotification(activityId, activityName, activityType,
                    processInstanceId, variables, durationMs, null);
        }

        log.info("📡 Activity Event: {} {} - {} ({})",
                eventType.toUpperCase(), activityType, activityName, activityId);
    }

    /**
     * Send notification for Camunda task completion/failure
     */
    private void sendCamundaNotification(String activityId, String activityName,
            String activityType, String processInstanceId, Map<String, Object> variables,
            Long durationMs, String error) {
        try {
            String status = error == null ? "COMPLETED" : "FAILED";
            String friendlyType = getFriendlyTypeName(activityType);
            String message = error == null
                    ? String.format("%s completed: %s", friendlyType, activityName != null ? activityName : activityId)
                    : String.format("%s failed: %s - %s", friendlyType,
                            activityName != null ? activityName : activityId, error);

            notificationService.sendEvent(TaskEvent.builder()
                    .taskId(activityId)
                    .type("CAMUNDA_TASK")
                    .nodeType(activityType)
                    .processInstanceId(processInstanceId)
                    .activityId(activityId)
                    .activityName(activityName)
                    .status(status)
                    .message(message)
                    .result(variables)
                    .error(error)
                    .durationMs(durationMs)
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send Camunda notification: {}", e.getMessage());
        }
    }

    private String getFriendlyTypeName(String activityType) {
        return switch (activityType) {
            case "startEvent" -> "Start Event";
            case "endEvent" -> "End Event";
            case "userTask" -> "User Task";
            case "serviceTask" -> "Service Task";
            case "exclusiveGateway" -> "Gateway";
            case "parallelGateway" -> "Parallel Gateway";
            case "scriptTask" -> "Script Task";
            case "sendTask" -> "Send Task";
            case "receiveTask" -> "Receive Task";
            case "callActivity" -> "Call Activity";
            case "subProcess" -> "Sub Process";
            default -> activityType;
        };
    }

    private String mapEventName(String eventName) {
        return switch (eventName) {
            case EVENTNAME_START -> "start";
            case EVENTNAME_END -> "end";
            default -> null;
        };
    }

    private String determineActivityType(DelegateExecution execution) {
        if (execution instanceof ExecutionEntity entity) {
            var activity = entity.getActivity();
            if (activity != null) {
                Object typeName = activity.getProperty("type");
                if (typeName != null) {
                    return typeName.toString();
                }
            }
        }

        // Fallback: try to infer from activity ID naming
        String activityId = execution.getCurrentActivityId();
        if (activityId != null) {
            if (activityId.contains("Start") || activityId.startsWith("start"))
                return "startEvent";
            if (activityId.contains("End") || activityId.startsWith("end"))
                return "endEvent";
            if (activityId.contains("Gateway") || activityId.startsWith("Gateway"))
                return "exclusiveGateway";
            if (activityId.contains("Task") || activityId.startsWith("Task"))
                return "userTask";
            if (activityId.contains("Service") || activityId.startsWith("Service"))
                return "serviceTask";
        }

        return "unknown";
    }

    private String buildMessage(String eventType, String activityType, String activityName) {
        String action = "start".equals(eventType) ? "Started" : "Completed";
        String typeFriendly = getFriendlyTypeName(activityType);
        return String.format("%s %s: %s", action, typeFriendly, activityName);
    }
}
