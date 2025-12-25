package com.workflow.camunda.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.exception.NullValueException;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import com.workflow.camunda.dto.ProcessDefinitionDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service class for Camunda workflow operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    /**
     * Start a process instance by process definition key
     * 
     * @param processKey Process definition key (e.g., "simple-process")
     * @param variables  Process variables
     * @return Process instance ID
     */
    public String startProcess(String processKey, Map<String, Object> variables) {
        log.info("Starting process: {} with variables: {}", processKey, variables);

        ProcessInstance processInstance;
        try {
            // Fast path: caller provides a real Camunda process definition key (= BPMN
            // <process id="...">)
            processInstance = runtimeService.startProcessInstanceByKey(processKey, variables);
        } catch (NullValueException e) {
            // Common mistake: caller sends BPMN process *name* (e.g. "Money Transfer
            // Process") instead of *id/key*
            ProcessDefinition definition = resolveLatestProcessDefinition(processKey);
            if (definition == null) {
                throw new IllegalArgumentException(buildNotDeployedMessage(processKey), e);
            }
            log.warn("No process deployed with key '{}'. Resolved by name to key '{}' (definitionId={})",
                    processKey, definition.getKey(), definition.getId());
            processInstance = runtimeService.startProcessInstanceById(definition.getId(), variables);
        }

        String processInstanceId = processInstance.getProcessInstanceId();
        log.info("Process started with ID: {}", processInstanceId);

        return processInstanceId;
    }

    /**
     * List latest-version process definitions that are currently deployed.
     */
    public List<ProcessDefinitionDto> listLatestProcessDefinitions() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey()
                .asc()
                .list()
                .stream()
                .map(pd -> ProcessDefinitionDto.builder()
                        .id(pd.getId())
                        .key(pd.getKey())
                        .name(pd.getName())
                        .version(pd.getVersion())
                        .build())
                .collect(Collectors.toList());
    }

    private ProcessDefinition resolveLatestProcessDefinition(String keyOrName) {
        // Try by key first
        ProcessDefinition byKey = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(keyOrName)
                .latestVersion()
                .singleResult();
        if (byKey != null) {
            return byKey;
        }

        // Then try by name ("process name" in BPMN)
        List<ProcessDefinition> byName = repositoryService.createProcessDefinitionQuery()
                .processDefinitionName(keyOrName)
                .orderByProcessDefinitionVersion()
                .desc()
                .listPage(0, 1);
        return byName.isEmpty() ? null : byName.getFirst();
    }

    private String buildNotDeployedMessage(String keyOrName) {
        List<ProcessDefinition> available = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey()
                .asc()
                .list();

        String availableKeys = available.stream()
                .map(ProcessDefinition::getKey)
                .distinct()
                .collect(Collectors.joining(", "));

        return "No deployed process definition with key or name '" + keyOrName + "'. "
                + (availableKeys.isBlank() ? "No process definitions are deployed."
                        : "Available keys: " + availableKeys);
    }

    /**
     * Get all tasks for a specific assignee
     * 
     * @param assignee Task assignee
     * @return List of tasks
     */
    public List<Task> getTasksByAssignee(String assignee) {
        log.info("Fetching tasks for assignee: {}", assignee);
        return taskService.createTaskQuery()
                .taskAssignee(assignee)
                .list();
    }

    /**
     * Get all unassigned tasks (candidate tasks)
     * 
     * @return List of unassigned tasks
     */
    public List<Task> getUnassignedTasks() {
        log.info("Fetching unassigned tasks");
        return taskService.createTaskQuery()
                .taskUnassigned()
                .list();
    }

    /**
     * Get task by ID
     * 
     * @param taskId Task ID
     * @return Task or null if not found
     */
    public Task getTaskById(String taskId) {
        return taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
    }

    /**
     * Complete a task with variables
     * 
     * @param taskId    Task ID
     * @param variables Task variables
     */
    public void completeTask(String taskId, Map<String, Object> variables) {
        log.info("Completing task: {} with variables: {}", taskId, variables);
        taskService.complete(taskId, variables);
        log.info("Task completed: {}", taskId);
    }

    /**
     * Claim a task for an assignee
     * 
     * @param taskId   Task ID
     * @param assignee User to assign the task to
     */
    public void claimTask(String taskId, String assignee) {
        log.info("Claiming task: {} for assignee: {}", taskId, assignee);
        taskService.claim(taskId, assignee);
    }

    /**
     * Get process instance status
     * 
     * @param processInstanceId Process instance ID
     * @return true if active, false if completed/terminated
     */
    public boolean isProcessActive(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return processInstance != null;
    }

    /**
     * Get all variables for a process instance
     * 
     * @param processInstanceId Process instance ID
     * @return Map of variable name to VariableDto
     */
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        log.info("Fetching variables for process: {}", processInstanceId);
        try {
            return runtimeService.getVariables(processInstanceId);
        } catch (Exception e) {
            log.warn("Could not get runtime variables, trying history: {}", e.getMessage());
            // If process is completed, get from history
            return new HashMap<>();
        }
    }

    /**
     * Unclaim a task (remove assignee)
     * 
     * @param taskId Task ID to unclaim
     */
    public void unclaimTask(String taskId) {
        log.info("Unclaiming task: {}", taskId);
        taskService.setAssignee(taskId, null);
    }

    /**
     * Get BPMN XML for a process definition
     * 
     * @param processDefinitionId Process definition ID
     * @return BPMN XML string
     */
    public String getBpmnXml(String processDefinitionId) {
        log.info("Fetching BPMN XML for definition: {}", processDefinitionId);
        try (var inputStream = repositoryService.getProcessModel(processDefinitionId)) {
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to get BPMN XML: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve BPMN model", e);
        }
    }

    /**
     * Get process definition ID for a running process instance
     * 
     * @param processInstanceId Process instance ID
     * @return Process definition ID or null if not found
     */
    public String getProcessDefinitionId(String processInstanceId) {
        log.info("Fetching process definition ID for instance: {}", processInstanceId);

        // Try runtime first (for active processes)
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance != null) {
            return processInstance.getProcessDefinitionId();
        }

        // Process might be completed - could check history if needed
        log.warn("Process instance not found or completed: {}", processInstanceId);
        return null;
    }

    // ============ DYNAMIC DEPLOYMENT METHODS ============

    /**
     * Deploy BPMN from InputStream
     * 
     * @param filename    Name of the BPMN file
     * @param inputStream BPMN XML content
     * @return Camunda Deployment object
     */
    public org.camunda.bpm.engine.repository.Deployment deployBpmn(String filename, java.io.InputStream inputStream) {
        log.info("Deploying BPMN: {}", filename);

        org.camunda.bpm.engine.repository.Deployment deployment = repositoryService.createDeployment()
                .addInputStream(filename, inputStream)
                .name(filename.replace(".bpmn", ""))
                .source("dynamic-api")
                .deploy();

        log.info("BPMN deployed successfully: {} (deploymentId={})", filename, deployment.getId());
        return deployment;
    }

    /**
     * Get process definition keys for a deployment
     * 
     * @param deploymentId Deployment ID
     * @return List of process definition keys
     */
    public List<String> getProcessDefinitionsByDeploymentId(String deploymentId) {
        return repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .list()
                .stream()
                .map(ProcessDefinition::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Undeploy (delete) a deployment
     * 
     * @param deploymentId Deployment ID
     * @param cascade      If true, also delete all process instances
     */
    public void undeploy(String deploymentId, boolean cascade) {
        log.info("Undeploying: {} (cascade={})", deploymentId, cascade);
        repositoryService.deleteDeployment(deploymentId, cascade);
        log.info("Deployment deleted: {}", deploymentId);
    }
}
