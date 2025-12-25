package com.workflow.camunda.controller;

import com.workflow.camunda.dto.DeploymentDto;
import com.workflow.camunda.dto.DeploymentResponse;
import com.workflow.camunda.dto.WorkflowDefinitionRequest;
import com.workflow.camunda.service.ProcessBuilderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for building and deploying BPMN processes dynamically
 */
@RestController
@RequestMapping("/api/process-builder")
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderController {

    private final ProcessBuilderService processBuilderService;

    /**
     * Deploy a workflow from JSON definition
     * 
     * POST /api/process-builder/deploy
     */
    @PostMapping("/deploy")
    public ResponseEntity<DeploymentResponse> deployWorkflow(
            @Valid @RequestBody WorkflowDefinitionRequest request) {

        log.info("Deploying workflow: {}", request.getProcessKey());
        DeploymentResponse response = processBuilderService.buildAndDeploy(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Preview BPMN XML without deploying
     * 
     * POST /api/process-builder/preview
     */
    @PostMapping("/preview")
    public ResponseEntity<DeploymentResponse> previewWorkflow(
            @Valid @RequestBody WorkflowDefinitionRequest request) {

        log.info("Previewing workflow: {}", request.getProcessKey());
        DeploymentResponse response = processBuilderService.preview(request);
        return ResponseEntity.ok(response);
    }

    /**
     * List all deployments
     * 
     * GET /api/process-builder/deployments
     */
    @GetMapping("/deployments")
    public ResponseEntity<List<DeploymentDto>> listDeployments() {
        List<DeploymentDto> deployments = processBuilderService.listDeployments();
        return ResponseEntity.ok(deployments);
    }

    /**
     * Delete a deployment
     * 
     * DELETE /api/process-builder/deployments/{deploymentId}
     */
    @DeleteMapping("/deployments/{deploymentId}")
    public ResponseEntity<Map<String, String>> deleteDeployment(
            @PathVariable String deploymentId,
            @RequestParam(defaultValue = "true") boolean cascade) {

        log.info("Deleting deployment: {} (cascade: {})", deploymentId, cascade);
        processBuilderService.deleteDeployment(deploymentId, cascade);

        Map<String, String> response = new HashMap<>();
        response.put("deploymentId", deploymentId);
        response.put("status", "deleted");
        response.put("cascade", String.valueOf(cascade));

        return ResponseEntity.ok(response);
    }
}
