package com.workflow.camunda.service;

import com.workflow.camunda.config.SseProperties;
import com.workflow.camunda.dto.TaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class NotificationService {

    private final SseProperties sseProperties;

    public NotificationService(SseProperties sseProperties) {
        this.sseProperties = sseProperties;
    }

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<TaskEvent> eventHistory = new ConcurrentLinkedDeque<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(sseProperties.getNotificationTimeoutMs());

        if (emitters.size() >= sseProperties.getNotificationMaxEmitters()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Too many notification subscribers"));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        this.emitters.add(emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((e) -> this.emitters.remove(emitter));

        return emitter;
    }

    public void sendEvent(TaskEvent event) {
        eventHistory.addLast(event);
        trimHistory();

        List<SseEmitter> deadEmitters = new ArrayList<>();
        this.emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("task-event").data(event));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        this.emitters.removeAll(deadEmitters);
    }

    public List<TaskEvent> getHistory(String type, String routeId) {
        List<TaskEvent> filtered = eventHistory.stream()
                .filter(e -> type == null || type.isBlank() || type.equalsIgnoreCase(e.getType()))
                .filter(e -> routeId == null || routeId.isBlank() || routeId.equalsIgnoreCase(e.getRouteId()))
                .toList();

        // Return newest first for UI consumption
        List<TaskEvent> copy = new ArrayList<>(filtered);
        Collections.reverse(copy);
        return copy;
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval-ms:25000}")
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private void trimHistory() {
        int max = sseProperties.getNotificationMaxHistory();
        if (max <= 0) {
            eventHistory.clear();
            return;
        }
        while (eventHistory.size() > max) {
            eventHistory.pollFirst();
        }
    }
}
