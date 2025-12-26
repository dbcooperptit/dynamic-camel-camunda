package com.workflow.camunda.service;

import com.workflow.camunda.config.SseProperties;
import com.workflow.camunda.dto.ActivityEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Service to manage SSE connections and broadcast activity events
 */
@Service
@Slf4j
public class ActivityStreamService {

    private final SseProperties sseProperties;

    public ActivityStreamService(SseProperties sseProperties) {
        this.sseProperties = sseProperties;
    }

    // SSE emitters per process instance
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // Activity history per process instance (for late subscribers)
    private final Map<String, Deque<ActivityEventDto>> activityHistory = new ConcurrentHashMap<>();

    private final Map<String, LocalDateTime> lastEventAt = new ConcurrentHashMap<>();

    // Start times for duration calculation
    private final Map<String, Map<String, LocalDateTime>> activityStartTimes = new ConcurrentHashMap<>();

    /**
     * Register a new SSE emitter for a process instance
     */
    public SseEmitter subscribe(String processInstanceId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        List<SseEmitter> list = emitters.computeIfAbsent(processInstanceId, k -> new CopyOnWriteArrayList<>());
        if (list.size() >= sseProperties.getActivityMaxEmittersPerProcess()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Too many subscribers for process"));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }
        list.add(emitter);

        emitter.onCompletion(() -> removeEmitter(processInstanceId, emitter));
        emitter.onTimeout(() -> removeEmitter(processInstanceId, emitter));
        emitter.onError(e -> removeEmitter(processInstanceId, emitter));

        // Send history to new subscriber
        Deque<ActivityEventDto> history = activityHistory.get(processInstanceId);
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

        // Send an initial heartbeat so clients know the stream is alive
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
        } catch (Exception ignored) {
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

        lastEventAt.put(processInstanceId, LocalDateTime.now());

        // Store in history
        Deque<ActivityEventDto> history = activityHistory.computeIfAbsent(processInstanceId, k -> new ConcurrentLinkedDeque<>());
        history.addLast(event);
        while (history.size() > sseProperties.getActivityMaxHistory()) {
            history.pollFirst();
        }

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
                    // Client disconnected - this is normal (browser refresh, tab close, network issue)
                    // Log at debug level only to reduce noise
                    log.debug("SSE connection closed for process {}: {}", processInstanceId, e.getMessage());
                    deadEmitters.add(emitter);
                    // Complete the emitter to properly clean up resources
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                        // Emitter might already be completed
                    }
                } catch (Exception e) {
                    log.warn("Unexpected error sending SSE event: {}", e.getMessage());
                    deadEmitters.add(emitter);
                }
            }

            // Clean up dead emitters
            if (!deadEmitters.isEmpty()) {
                processEmitters.removeAll(deadEmitters);
                log.debug("Cleaned up {} dead emitter(s) for process: {}", deadEmitters.size(), processInstanceId);
            }
        }

        log.debug("Broadcasted event: {} {} - {} ({})",
                event.getEventType(), event.getActivityType(),
                event.getActivityName(), event.getActivityId());
    }

    /**
     * Get activity history for a process instance
     */
    public List<ActivityEventDto> getHistory(String processInstanceId) {
        Deque<ActivityEventDto> history = activityHistory.get(processInstanceId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    /**
     * Clear history for a process instance (when process completes)
     */
    public void clearHistory(String processInstanceId) {
        activityHistory.remove(processInstanceId);
        activityStartTimes.remove(processInstanceId);
        lastEventAt.remove(processInstanceId);

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
            boolean removed = processEmitters.remove(emitter);
            if (removed) {
                log.info("Removed SSE emitter for process: {} (remaining: {})", 
                    processInstanceId, processEmitters.size());
                
                // If no more emitters, clean up the empty list
                if (processEmitters.isEmpty()) {
                    emitters.remove(processInstanceId);
                    log.debug("No more active SSE connections for process: {}", processInstanceId);
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval-ms:25000}")
    public void sendHeartbeat() {
        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            String processId = entry.getKey();
            List<SseEmitter> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) {
                list.removeAll(dead);
                if (list.isEmpty()) {
                    emitters.remove(processId);
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval-ms:25000}")
    public void cleanupStaleProcesses() {
        long retentionMs = sseProperties.getActivityRetentionMs();
        LocalDateTime now = LocalDateTime.now();

        for (String processId : new ArrayList<>(activityHistory.keySet())) {
            List<SseEmitter> list = emitters.get(processId);
            boolean hasSubscribers = list != null && !list.isEmpty();
            if (hasSubscribers) continue;

            LocalDateTime last = lastEventAt.get(processId);
            if (last == null) {
                // No events ever; safe to purge
                activityHistory.remove(processId);
                activityStartTimes.remove(processId);
                lastEventAt.remove(processId);
                continue;
            }

            long ageMs = java.time.Duration.between(last, now).toMillis();
            if (ageMs >= retentionMs) {
                activityHistory.remove(processId);
                activityStartTimes.remove(processId);
                lastEventAt.remove(processId);
            }
        }
    }
}
