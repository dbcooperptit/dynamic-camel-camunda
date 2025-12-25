package com.workflow.camunda.service;

import com.workflow.camunda.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ProcessBuilder;
import org.camunda.bpm.model.bpmn.instance.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building and deploying BPMN processes programmatically
 * using Camunda BPMN Model API Fluent Builder
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderService {

    private final RepositoryService repositoryService;

    /**
     * Build and deploy a workflow from the definition request
     */
    public DeploymentResponse buildAndDeploy(WorkflowDefinitionRequest request) {
        log.info("Building workflow: {} ({})", request.getProcessName(), request.getProcessKey());

        // Build BPMN model
        BpmnModelInstance modelInstance = buildBpmnModel(request);

        // Convert to XML for logging/preview
        String bpmnXml = convertToXml(modelInstance);
        log.debug("Generated BPMN XML:\n{}", bpmnXml);

        // Deploy to Camunda engine
        Deployment deployment = repositoryService.createDeployment()
                .addModelInstance(request.getProcessKey() + ".bpmn", modelInstance)
                .name(request.getProcessName())
                .deploy();

        // Get process definition
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        log.info("Deployed workflow: {} with deployment ID: {}",
                request.getProcessKey(), deployment.getId());

        return DeploymentResponse.builder()
                .deploymentId(deployment.getId())
                .processDefinitionId(processDefinition != null ? processDefinition.getId() : null)
                .processKey(request.getProcessKey())
                .processName(request.getProcessName())
                .deployedAt(deployment.getDeploymentTime())
                .bpmnXml(bpmnXml)
                .message("Workflow deployed successfully")
                .build();
    }

    /**
     * Preview BPMN XML without deploying
     */
    public DeploymentResponse preview(WorkflowDefinitionRequest request) {
        log.info("Previewing workflow: {} ({})", request.getProcessName(), request.getProcessKey());

        BpmnModelInstance modelInstance = buildBpmnModel(request);
        String bpmnXml = convertToXml(modelInstance);

        return DeploymentResponse.builder()
                .processKey(request.getProcessKey())
                .processName(request.getProcessName())
                .bpmnXml(bpmnXml)
                .message("Preview generated (not deployed)")
                .build();
    }

    /**
     * List all deployments
     */
    public List<DeploymentDto> listDeployments() {
        return repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list()
                .stream()
                .map(d -> DeploymentDto.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .source(d.getSource())
                        .deploymentTime(d.getDeploymentTime())
                        .tenantId(d.getTenantId())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Delete a deployment
     */
    public void deleteDeployment(String deploymentId, boolean cascade) {
        log.info("Deleting deployment: {} (cascade: {})", deploymentId, cascade);
        repositoryService.deleteDeployment(deploymentId, cascade);
    }

    // Track gateway ID to default flow target ID mappings
    private final Map<String, String> gatewayDefaultFlows = new HashMap<>();

    /**
     * Build BPMN model from workflow definition
     */
    private BpmnModelInstance buildBpmnModel(WorkflowDefinitionRequest request) {
        // Clear tracking map for each new model
        gatewayDefaultFlows.clear();

        List<StepDefinition> steps = request.getSteps();

        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step");
        }

        // Create maps for quick lookup
        Map<String, StepDefinition> stepMap = steps.stream()
                .collect(Collectors.toMap(StepDefinition::getId, s -> s));

        // Find start and end steps
        StepDefinition startStep = findStartStep(steps);
        Set<String> endStepIds = findEndStepIds(steps);

        // Start building the process
        ProcessBuilder processBuilder = Bpmn.createExecutableProcess(request.getProcessKey())
                .name(request.getProcessName())
                .camundaHistoryTimeToLive(request.getHistoryTimeToLive());

        // Build the flow using fluent API
        AbstractFlowNodeBuilder<?, ?> builder = processBuilder.startEvent("start")
                .name("Start");

        // Track visited nodes to handle complex flows
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        // Start from the first step
        if (startStep != null) {
            builder = buildStep(builder, startStep, stepMap, visited, endStepIds);
        }

        // Finish the model
        BpmnModelInstance model = builder.done();

        // Post-process: Set default flows on exclusive gateways
        setDefaultFlowsOnGateways(model);

        return model;
    }

    /**
     * Post-process the model to set default flows on exclusive gateways
     */
    private void setDefaultFlowsOnGateways(BpmnModelInstance model) {
        for (Map.Entry<String, String> entry : gatewayDefaultFlows.entrySet()) {
            String gatewayId = entry.getKey();
            String targetStepId = entry.getValue();

            ExclusiveGateway gateway = model.getModelElementById(gatewayId);
            if (gateway != null) {
                // Find the sequence flow from this gateway to the target
                for (SequenceFlow outgoing : gateway.getOutgoing()) {
                    FlowNode target = outgoing.getTarget();
                    if (target != null && target.getId().equals(targetStepId)) {
                        gateway.setDefault(outgoing);
                        log.debug("Set default flow {} on gateway {}", outgoing.getId(), gatewayId);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Build a single step and recursively build next steps
     */
    @SuppressWarnings("unchecked")
    private AbstractFlowNodeBuilder<?, ?> buildStep(
            AbstractFlowNodeBuilder<?, ?> builder,
            StepDefinition step,
            Map<String, StepDefinition> stepMap,
            Set<String> visited,
            Set<String> endStepIds) {

        if (visited.contains(step.getId())) {
            // Already built this step, connect to it
            return builder.connectTo(step.getId());
        }
        visited.add(step.getId());

        switch (step.getType()) {
            case USER_TASK:
                builder = builder.userTask(step.getId())
                        .name(step.getName());

                if (step.getAssignee() != null && !step.getAssignee().isEmpty()) {
                    builder = ((org.camunda.bpm.model.bpmn.builder.UserTaskBuilder) builder)
                            .camundaAssignee(step.getAssignee());
                }
                if (step.getCandidateGroups() != null && !step.getCandidateGroups().isEmpty()) {
                    builder = ((org.camunda.bpm.model.bpmn.builder.UserTaskBuilder) builder)
                            .camundaCandidateGroups(String.join(",", step.getCandidateGroups()));
                }
                if (step.getFormKey() != null && !step.getFormKey().isEmpty()) {
                    builder = ((org.camunda.bpm.model.bpmn.builder.UserTaskBuilder) builder)
                            .camundaFormKey(step.getFormKey());
                }
                break;

            case SERVICE_TASK:
                builder = builder.serviceTask(step.getId())
                        .name(step.getName());

                org.camunda.bpm.model.bpmn.builder.ServiceTaskBuilder serviceTaskBuilder = (org.camunda.bpm.model.bpmn.builder.ServiceTaskBuilder) builder;

                if (step.getDelegateExpression() != null && !step.getDelegateExpression().isEmpty()) {
                    serviceTaskBuilder = serviceTaskBuilder.camundaDelegateExpression(step.getDelegateExpression());
                } else if (step.getDelegateClass() != null && !step.getDelegateClass().isEmpty()) {
                    serviceTaskBuilder = serviceTaskBuilder.camundaClass(step.getDelegateClass());
                }

                // Add input parameters from variables map
                if (step.getVariables() != null && !step.getVariables().isEmpty()) {
                    for (Map.Entry<String, Object> entry : step.getVariables().entrySet()) {
                        String paramName = entry.getKey();
                        Object paramValue = entry.getValue();
                        if (paramValue != null) {
                            serviceTaskBuilder = serviceTaskBuilder.camundaInputParameter(paramName,
                                    paramValue.toString());
                        }
                    }
                }
                builder = serviceTaskBuilder;
                break;

            case EXCLUSIVE_GATEWAY:
                builder = builder.exclusiveGateway(step.getId())
                        .name(step.getName());
                break;

            case PARALLEL_GATEWAY:
                builder = builder.parallelGateway(step.getId())
                        .name(step.getName());
                break;

            default:
                log.warn("Unknown step type: {} for step: {}", step.getType(), step.getId());
        }

        // Handle next steps
        List<String> nextSteps = step.getNextSteps();
        if (nextSteps == null || nextSteps.isEmpty() || endStepIds.contains(step.getId())) {
            // This is an end step
            builder = builder.endEvent("end_" + step.getId())
                    .name("End");
        } else if (nextSteps.size() == 1) {
            // Single next step
            String nextId = nextSteps.get(0);
            StepDefinition nextStep = stepMap.get(nextId);
            if (nextStep != null) {
                builder = buildStep(builder, nextStep, stepMap, visited, endStepIds);
            }
        } else {
            // Multiple next steps (gateway branches)
            // Build all branches using moveToNode to return to gateway
            boolean isFirst = true;
            String gatewayId = step.getId();

            // For exclusive gateways, find which branch should be the default (no
            // condition)
            String defaultBranchId = null;
            if (step.getType() == StepType.EXCLUSIVE_GATEWAY) {
                for (String nextId : nextSteps) {
                    String condition = null;
                    if (step.getNextStepConditions() != null) {
                        condition = step.getNextStepConditions().get(nextId);
                    }
                    StepDefinition nextStep = stepMap.get(nextId);
                    if (condition == null && nextStep != null && nextStep.getConditionExpression() == null) {
                        // First branch without condition becomes default
                        defaultBranchId = nextId;
                        break;
                    }
                }
            }

            for (int i = 0; i < nextSteps.size(); i++) {
                String nextId = nextSteps.get(i);
                StepDefinition nextStep = stepMap.get(nextId);

                if (nextStep != null) {
                    if (!isFirst) {
                        // Move back to gateway to create another branch
                        builder = builder.moveToNode(gatewayId);
                    }

                    // Determine condition for this branch
                    String condition = null;
                    if (step.getNextStepConditions() != null) {
                        condition = step.getNextStepConditions().get(nextId);
                    }
                    // Fallback to target step's condition (if not defined in source)
                    if (condition == null && nextStep.getConditionExpression() != null) {
                        condition = nextStep.getConditionExpression();
                    }

                    // Add condition to sequence flow for exclusive gateway
                    // Exclusive gateway requires condition on all flows except the default flow
                    if (step.getType() == StepType.EXCLUSIVE_GATEWAY) {
                        if (condition != null) {
                            // Apply condition expression to this flow
                            builder = builder.condition("to_" + nextId, condition);
                        } else if (nextId.equals(defaultBranchId)) {
                            // This is the default branch - register it for post-processing
                            // The default flow doesn't need a condition
                            gatewayDefaultFlows.put(gatewayId, nextId);
                            log.debug("Registered default flow to {} for gateway {}", nextId, gatewayId);
                        } else {
                            // No condition and not default - this would cause an error
                            // Apply a fallback "else" condition
                            log.warn("No condition for flow to {} in exclusive gateway {} - applying fallback", nextId,
                                    gatewayId);
                            builder = builder.condition("to_" + nextId, "${true}");
                        }
                    }

                    builder = buildStep(builder, nextStep, stepMap, visited, endStepIds);
                    isFirst = false;
                }
            }
        }

        return builder;
    }

    /**
     * Find the first step (entrance point)
     */
    private StepDefinition findStartStep(List<StepDefinition> steps) {
        // First step in the list is considered the start
        if (!steps.isEmpty()) {
            StepDefinition first = steps.get(0);
            if (first.getType() == StepType.START_EVENT) {
                return steps.size() > 1 ? steps.get(1) : null;
            }
            return first;
        }
        return null;
    }

    /**
     * Find steps that have no outgoing flows (end steps)
     */
    private Set<String> findEndStepIds(List<StepDefinition> steps) {
        Set<String> endSteps = new HashSet<>();
        for (StepDefinition step : steps) {
            if (step.getNextSteps() == null || step.getNextSteps().isEmpty()) {
                endSteps.add(step.getId());
            }
            // Also check for END_EVENT type
            if (step.getType() == StepType.END_EVENT) {
                endSteps.add(step.getId());
            }
        }
        return endSteps;
    }

    /**
     * Convert BPMN model to XML string
     */
    private String convertToXml(BpmnModelInstance modelInstance) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Bpmn.writeModelToStream(outputStream, modelInstance);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to convert BPMN model to XML", e);
            return null;
        }
    }
}
