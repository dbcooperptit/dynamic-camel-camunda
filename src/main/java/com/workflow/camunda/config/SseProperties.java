package com.workflow.camunda.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sse")
public class SseProperties {

    /**
     * Heartbeat interval for SSE connections.
     */
    private long heartbeatIntervalMs = 25_000L;

    /**
     * Max events kept per process instance in activity history.
     */
    private int activityMaxHistory = 1_000;

    /**
     * Max SSE emitters kept per process instance.
     */
    private int activityMaxEmittersPerProcess = 50;

    /**
     * Cleanup retention for activity history when there are no subscribers.
     */
    private long activityRetentionMs = 30 * 60 * 1000L;

    /**
     * Notification emitter timeout.
     */
    private long notificationTimeoutMs = 30 * 60 * 1000L;

    /**
     * Max number of notification emitters.
     */
    private int notificationMaxEmitters = 200;

    /**
     * Max notification events kept in in-memory history for replay.
     */
    private int notificationMaxHistory = 500;
}
