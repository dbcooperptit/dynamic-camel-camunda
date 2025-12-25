package com.workflow.camunda.service;

import com.workflow.camunda.dto.ActivityEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service to manage SSE connections and broadcast activity events
 */
@Service
@Slf4j
public class ActivityStreamService {

    // SSE emitters per process instance
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // Activity history per process instance (for late subscribers)
    private final Map<String, List<ActivityEventDto>> activityHistory = new ConcurrentHashMap<>();

    // Start times for duration calculation
    private final Map<String, Map<String, LocalDateTime>> activityStartTimes = new ConcurrentHashMap<>();

    /**
     * Register a new SSE emitter for a process instance
     */
    public SseEmitter subscribe(String processInstanceId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        emitters.computeIfAbsent(processInstanceId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(processInstanceId, emitter));
        emitter.onTimeout(() -> removeEmitter(processInstanceId, emitter));
        emitter.onError(e -> removeEmitter(processInstanceId, emitter));

        // Send history to new subscriber
        List<ActivityEventDto> history = activityHistory.get(processInstanceId);
        if (history != null && !history.isEmpty()) {
            try {
                for (ActivityEventDto event : history) {
                    emitter.send(SseEmitter.event()
                            .name("activity")
                            .data(event));
                }
            } catch (IOException e) {
                log.warn("Failed to send history to new subscriber: {}", e.getMessage());
            }
        }

        log.info("SSE subscriber added for process: {} (total: {})",
                processInstanceId, emitters.get(processInstanceId).size());

        return emitter;
    }

    /**
     * Broadcast an activity event to all subscribers
     */
    public void broadcastEvent(ActivityEventDto event) {
        String processInstanceId = event.getProcessInstanceId();

        // Store in history
        activityHistory.computeIfAbsent(processInstanceId, k -> new CopyOnWriteArrayList<>()).add(event);

        // Track start time for duration calculation
        if ("start".equals(event.getEventType())) {
            activityStartTimes
                    .computeIfAbsent(processInstanceId, k -> new ConcurrentHashMap<>())
                    .put(event.getActivityId(), event.getTimestamp());
        }

        // Calculate duration for end events
        if ("end".equals(event.getEventType())) {
            Map<String, LocalDateTime> startTimes = activityStartTimes.get(processInstanceId);
            if (startTimes != null) {
                LocalDateTime startTime = startTimes.get(event.getActivityId());
                if (startTime != null) {
                    long durationMs = java.time.Duration.between(startTime, event.getTimestamp()).toMillis();
                    event.setDurationMs(durationMs);
                }
            }
        }

        // Broadcast to all subscribers
        List<SseEmitter> processEmitters = emitters.get(processInstanceId);
        if (processEmitters != null && !processEmitters.isEmpty()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();

            for (SseEmitter emitter : processEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("activity")
                            .data(event));
                } catch (IOException e) {
                    log.warn("Failed to send event to subscriber: {}", e.getMessage());
                    deadEmitters.add(emitter);
                }
            }

            // Clean up dead emitters
            processEmitters.removeAll(deadEmitters);
        }

        log.debug("Broadcasted event: {} {} - {} ({})",
                event.getEventType(), event.getActivityType(),
                event.getActivityName(), event.getActivityId());
    }

    /**
     * Get activity history for a process instance
     */
    public List<ActivityEventDto> getHistory(String processInstanceId) {
        return activityHistory.getOrDefault(processInstanceId, Collections.emptyList());
    }

    /**
     * Clear history for a process instance (when process completes)
     */
    public void clearHistory(String processInstanceId) {
        activityHistory.remove(processInstanceId);
        activityStartTimes.remove(processInstanceId);

        // Close all emitters for this process
        List<SseEmitter> processEmitters = emitters.remove(processInstanceId);
        if (processEmitters != null) {
            for (SseEmitter emitter : processEmitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        log.info("Cleared history and emitters for process: {}", processInstanceId);
    }

    private void removeEmitter(String processInstanceId, SseEmitter emitter) {
        List<SseEmitter> processEmitters = emitters.get(processInstanceId);
        if (processEmitters != null) {
            processEmitters.remove(emitter);
            log.debug("Removed SSE emitter for process: {}", processInstanceId);
        }
    }
}
