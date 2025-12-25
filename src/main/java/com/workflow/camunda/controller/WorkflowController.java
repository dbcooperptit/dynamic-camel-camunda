package com.workflow.camunda.controller;

import com.workflow.camunda.dto.ProcessStartRequest;
import com.workflow.camunda.dto.ProcessDefinitionDto;
import com.workflow.camunda.dto.TaskCompleteRequest;
import com.workflow.camunda.dto.TaskDto;
import com.workflow.camunda.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.task.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Camunda workflow operations
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;

    /**
     * Start a new process instance
     * 
     * POST /api/workflow/start/{processKey}
     * Body: {"customerName": "John Doe", "amount": 5000}
     */
    @PostMapping("/start/{processKey}")
    public ResponseEntity<Map<String, String>> startProcess(
            @PathVariable String processKey,
            @RequestBody(required = false) ProcessStartRequest request) {

        Map<String, Object> variables = request != null ? request.getVariables() : new HashMap<>();
        String processInstanceId = workflowService.startProcess(processKey, variables);

        Map<String, String> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);
        response.put("processKey", processKey);
        response.put("status", "started");

        return ResponseEntity.ok(response);
    }

    /**
     * List deployed process definitions (latest versions).
     * Useful for UIs to show the correct process *key* to start.
     *
     * GET /api/workflow/definitions
     */
    @GetMapping("/definitions")
    public ResponseEntity<List<ProcessDefinitionDto>> listProcessDefinitions() {
        return ResponseEntity.ok(workflowService.listLatestProcessDefinitions());
    }

    /**
     * Get all tasks for a specific assignee
     * 
     * GET /api/workflow/tasks?assignee=admin
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<TaskDto>> getTasks(@RequestParam(required = false) String assignee) {
        List<Task> tasks;

        if (assignee != null && !assignee.isEmpty()) {
            tasks = workflowService.getTasksByAssignee(assignee);
        } else {
            tasks = workflowService.getUnassignedTasks();
        }

        List<TaskDto> taskDtos = tasks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(taskDtos);
    }

    /**
     * Get a specific task by ID
     * 
     * GET /api/workflow/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskDto> getTask(@PathVariable String taskId) {
        Task task = workflowService.getTaskById(taskId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(convertToDto(task));
    }

    /**
     * Complete a task
     * 
     * POST /api/workflow/tasks/{taskId}/complete
     * Body: {"approved": true, "comment": "Looks good"}
     */
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Map<String, String>> completeTask(
            @PathVariable String taskId,
            @RequestBody(required = false) TaskCompleteRequest request) {

        Task task = workflowService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> variables = request != null ? request.getVariables() : new HashMap<>();
        workflowService.completeTask(taskId, variables);

        Map<String, String> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Claim a task for an assignee
     * 
     * POST /api/workflow/tasks/{taskId}/claim
     * Body: {"assignee": "admin"}
     */
    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<Map<String, String>> claimTask(
            @PathVariable String taskId,
            @RequestBody Map<String, String> request) {

        String assignee = request.get("assignee");
        if (assignee == null || assignee.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        workflowService.claimTask(taskId, assignee);

        Map<String, String> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("assignee", assignee);
        response.put("status", "claimed");

        return ResponseEntity.ok(response);
    }

    /**
     * Check if a process instance is still active
     * 
     * GET /api/workflow/process/{processInstanceId}/status
     */
    @GetMapping("/process/{processInstanceId}/status")
    public ResponseEntity<Map<String, Object>> getProcessStatus(@PathVariable String processInstanceId) {
        boolean isActive = workflowService.isProcessActive(processInstanceId);

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);
        response.put("isActive", isActive);
        response.put("status", isActive ? "running" : "completed");

        return ResponseEntity.ok(response);
    }

    /**
     * Convert Camunda Task to DTO
     */
    private TaskDto convertToDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .name(task.getName())
                .assignee(task.getAssignee())
                .processInstanceId(task.getProcessInstanceId())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .createTime(task.getCreateTime())
                .build();
    }

    /**
     * Get process instance variables
     * 
     * GET /api/workflow/process/{processInstanceId}/variables
     */
    @GetMapping("/process/{processInstanceId}/variables")
    public ResponseEntity<Map<String, Object>> getProcessVariables(@PathVariable String processInstanceId) {
        Map<String, Object> variables = workflowService.getProcessVariables(processInstanceId);
        return ResponseEntity.ok(variables);
    }

    /**
     * Unclaim a task (remove assignee)
     * 
     * POST /api/workflow/tasks/{taskId}/unclaim
     */
    @PostMapping("/tasks/{taskId}/unclaim")
    public ResponseEntity<Map<String, String>> unclaimTask(@PathVariable String taskId) {
        Task task = workflowService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        workflowService.unclaimTask(taskId);

        Map<String, String> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", "unclaimed");

        return ResponseEntity.ok(response);
    }

    /**
     * Get BPMN XML for a process definition
     * 
     * GET /api/workflow/definitions/{processDefinitionId}/xml
     */
    @GetMapping("/definitions/{processDefinitionId}/xml")
    public ResponseEntity<String> getBpmnXml(@PathVariable String processDefinitionId) {
        String xml = workflowService.getBpmnXml(processDefinitionId);
        return ResponseEntity.ok(xml);
    }

    /**
     * Deploy BPMN from file upload
     * 
     * POST /api/workflow/deploy
     */
    @PostMapping("/deploy")
    public ResponseEntity<com.workflow.camunda.dto.DeploymentDto> deployBpmn(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            org.camunda.bpm.engine.repository.Deployment deployment = workflowService
                    .deployBpmn(file.getOriginalFilename(), file.getInputStream());

            var processDefinitions = workflowService.getProcessDefinitionsByDeploymentId(deployment.getId());

            return ResponseEntity.ok(com.workflow.camunda.dto.DeploymentDto.builder()
                    .id(deployment.getId())
                    .name(deployment.getName())
                    .source(deployment.getSource())
                    .deploymentTime(deployment.getDeploymentTime())
                    .processDefinitionKeys(processDefinitions)
                    .status("DEPLOYED")
                    .build());
        } catch (Exception e) {
            log.error("Failed to deploy BPMN: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deploy BPMN from XML string (for UI builders)
     * 
     * POST /api/workflow/deploy/xml
     */
    @PostMapping("/deploy/xml")
    public ResponseEntity<com.workflow.camunda.dto.DeploymentDto> deployBpmnXml(
            @RequestBody com.workflow.camunda.dto.BpmnDeployRequest request) {
        try {
            String filename = request.getName() + ".bpmn";
            java.io.InputStream xmlStream = new java.io.ByteArrayInputStream(
                    request.getXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            org.camunda.bpm.engine.repository.Deployment deployment = workflowService.deployBpmn(filename, xmlStream);

            var processDefinitions = workflowService.getProcessDefinitionsByDeploymentId(deployment.getId());

            log.info("Deployed BPMN from XML: {} ({})", request.getName(), deployment.getId());

            return ResponseEntity.ok(com.workflow.camunda.dto.DeploymentDto.builder()
                    .id(deployment.getId())
                    .name(deployment.getName())
                    .source(deployment.getSource())
                    .deploymentTime(deployment.getDeploymentTime())
                    .processDefinitionKeys(processDefinitions)
                    .status("DEPLOYED")
                    .build());
        } catch (Exception e) {
            log.error("Failed to deploy BPMN from XML: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Undeploy (delete) a deployment
     * 
     * DELETE /api/workflow/deploy/{deploymentId}
     */
    @DeleteMapping("/deploy/{deploymentId}")
    public ResponseEntity<Map<String, String>> undeploy(
            @PathVariable String deploymentId,
            @RequestParam(defaultValue = "false") boolean cascade) {
        try {
            workflowService.undeploy(deploymentId, cascade);

            Map<String, String> response = new HashMap<>();
            response.put("deploymentId", deploymentId);
            response.put("status", "UNDEPLOYED");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to undeploy: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
