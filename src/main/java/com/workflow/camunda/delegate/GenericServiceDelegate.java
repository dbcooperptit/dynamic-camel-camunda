package com.workflow.camunda.delegate;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generic service task delegate that can be used for various actions.
 * The action is determined by process variables.
 * 
 * Usage in workflow definition:
 * - delegateExpression: "${genericServiceDelegate}"
 * - variables: {"action": "sendEmail", "recipient": "user@example.com"}
 */
@Component("genericServiceDelegate")
@Slf4j
public class GenericServiceDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        String activityName = execution.getCurrentActivityName();

        // Get action from variables
        String action = (String) execution.getVariable("action");

        log.info("=== Service Task Execution ===");
        log.info("Process Instance: {}", processInstanceId);
        log.info("Activity: {} ({})", activityName, activityId);
        log.info("Action: {}", action);

        // Log all variables
        Map<String, Object> variables = execution.getVariables();
        log.info("Variables: {}", variables);

        // Execute action based on variable
        if (action != null) {
            switch (action.toLowerCase()) {
                case "sendemail":
                    handleSendEmail(execution);
                    break;
                case "log":
                    handleLog(execution);
                    break;
                case "validate":
                    handleValidate(execution);
                    break;
                case "notify":
                    handleNotify(execution);
                    break;
                default:
                    log.info("Executing generic action: {}", action);
                    execution.setVariable("actionResult", "completed");
            }
        } else {
            log.info("No action specified, completing task");
            execution.setVariable("serviceTaskCompleted", true);
        }

        log.info("=== Service Task Completed ===");
    }

    private void handleSendEmail(DelegateExecution execution) {
        String recipient = (String) execution.getVariable("recipient");
        String subject = (String) execution.getVariable("subject");
        String body = (String) execution.getVariable("body");

        log.info("Sending email to: {}", recipient);
        log.info("Subject: {}", subject);
        log.info("Body: {}", body);

        // Simulate email sending
        execution.setVariable("emailSent", true);
        execution.setVariable("emailSentAt", System.currentTimeMillis());
    }

    private void handleLog(DelegateExecution execution) {
        String message = (String) execution.getVariable("logMessage");
        String level = (String) execution.getVariable("logLevel");

        if (level != null && level.equalsIgnoreCase("error")) {
            log.error("Custom Log: {}", message);
        } else if (level != null && level.equalsIgnoreCase("warn")) {
            log.warn("Custom Log: {}", message);
        } else {
            log.info("Custom Log: {}", message);
        }
    }

    private void handleValidate(DelegateExecution execution) {
        // Example validation logic
        Object data = execution.getVariable("dataToValidate");
        boolean isValid = data != null;

        execution.setVariable("validationResult", isValid);
        log.info("Validation result: {}", isValid);
    }

    private void handleNotify(DelegateExecution execution) {
        String channel = (String) execution.getVariable("notificationChannel");
        String message = (String) execution.getVariable("notificationMessage");

        log.info("Sending notification via {}: {}", channel, message);
        execution.setVariable("notificationSent", true);
    }
}
