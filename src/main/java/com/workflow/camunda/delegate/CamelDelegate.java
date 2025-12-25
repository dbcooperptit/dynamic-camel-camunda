package com.workflow.camunda.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Camunda Delegate to invoke Apache Camel routes from BPMN Service Tasks.
 * 
 * Usage in BPMN:
 * - Set delegate expression: ${camelDelegate}
 * - Set process variable 'camelRoute' to the route name (e.g.,
 * 'callExternalApi', 'routeMessage')
 * - Optionally set 'camelRouteData' as Map for input data
 * 
 * Result will be stored in 'camelResult' process variable.
 */
@Slf4j
@Component("camelDelegate")
@RequiredArgsConstructor
public class CamelDelegate implements JavaDelegate {

    private final ProducerTemplate producerTemplate;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String routeName = (String) execution.getVariable("camelRoute");

        if (routeName == null || routeName.isBlank()) {
            throw new IllegalArgumentException("Process variable 'camelRoute' is required");
        }

        log.info("CamelDelegate executing route: {} for process instance: {}",
                routeName, execution.getProcessInstanceId());

        // Prepare input data - either from specific variable or all process variables
        Object inputData = execution.getVariable("camelRouteData");
        if (inputData == null) {
            // Use all process variables as input
            Map<String, Object> variables = new HashMap<>(execution.getVariables());
            variables.remove("camelRoute"); // Don't include the route name itself
            variables.remove("camelRouteData");
            inputData = variables;
        }

        // Set headers from process variables if needed
        Map<String, Object> headers = new HashMap<>();

        // Check for common header variables
        if (execution.hasVariable("priority")) {
            headers.put("priority", execution.getVariable("priority"));
        }
        if (execution.hasVariable("userId")) {
            headers.put("userId", execution.getVariable("userId"));
        }

        try {
            String endpointUri = "direct:" + routeName;
            log.info("Sending to Camel endpoint: {} with data: {}", endpointUri, inputData);

            Object result;
            if (headers.isEmpty()) {
                result = producerTemplate.requestBody(endpointUri, inputData);
            } else {
                result = producerTemplate.requestBodyAndHeaders(endpointUri, inputData, headers);
            }

            // Store result in process variable
            execution.setVariable("camelResult", result);
            execution.setVariable("camelRouteExecuted", routeName);
            execution.setVariable("camelExecutionTime", System.currentTimeMillis());

            log.info("Camel route {} completed successfully. Result: {}", routeName, result);

        } catch (Exception e) {
            log.error("Error executing Camel route {}: {}", routeName, e.getMessage(), e);
            execution.setVariable("camelError", e.getMessage());
            throw e;
        }
    }
}
