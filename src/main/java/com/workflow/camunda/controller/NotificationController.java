package com.workflow.camunda.controller;

import com.workflow.camunda.dto.TaskEvent;
import com.workflow.camunda.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/stream")
    public SseEmitter streamNotifications() {
        return notificationService.createEmitter();
    }

    @GetMapping("/history")
    public ResponseEntity<List<TaskEvent>> history(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String routeId) {
        return ResponseEntity.ok(notificationService.getHistory(type, routeId));
    }
}
