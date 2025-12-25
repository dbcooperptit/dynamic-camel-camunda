package com.workflow.camunda.controller;

import com.workflow.camunda.dto.ActivityEventDto;
import com.workflow.camunda.service.ActivityStreamService;
import com.workflow.camunda.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * REST Controller for activity streaming and process visualization
 */
@RestController
@RequestMapping("/api/workflow/process")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ActivityStreamController {

    private final ActivityStreamService activityStreamService;
    private final WorkflowService workflowService;

    /**
     * SSE endpoint for realtime activity updates
     * 
     * GET /api/workflow/process/{processInstanceId}/activities/stream
     */
    @GetMapping(value = "/{processInstanceId}/activities/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamActivities(@PathVariable String processInstanceId) {
        log.info("📡 SSE subscription requested for process: {}", processInstanceId);

        // 10 minutes timeout for SSE connection
        SseEmitter emitter = activityStreamService.subscribe(processInstanceId, 10 * 60 * 1000L);

        return emitter;
    }

    /**
     * Get complete activity history for a process instance
     * 
     * GET /api/workflow/process/{processInstanceId}/activities
     */
    @GetMapping("/{processInstanceId}/activities")
    public ResponseEntity<List<ActivityEventDto>> getActivities(@PathVariable String processInstanceId) {
        log.info("Fetching activity history for process: {}", processInstanceId);
        List<ActivityEventDto> history = activityStreamService.getHistory(processInstanceId);
        return ResponseEntity.ok(history);
    }

    /**
     * Get BPMN XML for a running process instance
     * 
     * GET /api/workflow/process/{processInstanceId}/diagram
     */
    @GetMapping("/{processInstanceId}/diagram")
    public ResponseEntity<String> getProcessDiagram(@PathVariable String processInstanceId) {
        log.info("Fetching BPMN diagram for process: {}", processInstanceId);

        try {
            // Get process definition ID from process instance
            String processDefinitionId = workflowService.getProcessDefinitionId(processInstanceId);
            if (processDefinitionId == null) {
                return ResponseEntity.notFound().build();
            }

            String bpmnXml = workflowService.getBpmnXml(processDefinitionId);
            return ResponseEntity.ok(bpmnXml);
        } catch (Exception e) {
            log.error("Failed to get diagram: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
