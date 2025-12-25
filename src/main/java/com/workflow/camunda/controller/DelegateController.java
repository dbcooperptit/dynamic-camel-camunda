package com.workflow.camunda.controller;

import com.workflow.camunda.dto.DelegateInfo;
import com.workflow.camunda.dto.DelegateInfo.ActionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for delegate discovery.
 * Provides endpoints to list available delegates and their actions.
 */
@RestController
@RequestMapping("/api/delegates")
@CrossOrigin(origins = "*")
@Slf4j
public class DelegateController {

    /**
     * Registry of all available delegates and their actions.
     * In a production system, this could be auto-discovered via Spring beans.
     */
    private static final Map<String, DelegateInfo> DELEGATE_REGISTRY = new LinkedHashMap<>();

    static {
        // MoneyTransferDelegate
        DELEGATE_REGISTRY.put("moneyTransferDelegate", DelegateInfo.builder()
                .name("moneyTransferDelegate")
                .displayName("💰 Money Transfer")
                .description(
                        "Handles money transfer operations: validate accounts, check balance, execute transfer, send notifications")
                .actions(List.of(
                        ActionInfo.builder()
                                .name("validateSourceAccount")
                                .displayName("Validate Source Account")
                                .description("Validate that source account exists and is active")
                                .requiredVariables(Map.of("sourceAccount", "string - Source account number"))
                                .build(),
                        ActionInfo.builder()
                                .name("validateDestAccount")
                                .displayName("Validate Destination Account")
                                .description("Validate that destination account exists")
                                .requiredVariables(Map.of("destAccount", "string - Destination account number"))
                                .build(),
                        ActionInfo.builder()
                                .name("checkBalance")
                                .displayName("Check Balance")
                                .description("Check if source account has sufficient balance")
                                .requiredVariables(Map.of(
                                        "sourceAccount", "string - Source account number",
                                        "amount", "number - Transfer amount"))
                                .build(),
                        ActionInfo.builder()
                                .name("executeTransfer")
                                .displayName("Execute Transfer")
                                .description("Execute the actual money transfer with saga compensation")
                                .requiredVariables(Map.of(
                                        "sourceAccount", "string - Source account number",
                                        "destAccount", "string - Destination account number",
                                        "amount", "number - Transfer amount",
                                        "description", "string - Transfer description"))
                                .build(),
                        ActionInfo.builder()
                                .name("sendNotification")
                                .displayName("Send Notification")
                                .description("Send SMS, email, and push notifications")
                                .requiredVariables(Map.of("transactionId", "string - Transaction ID"))
                                .build()))
                .build());

        // GenericServiceDelegate
        DELEGATE_REGISTRY.put("genericServiceDelegate", DelegateInfo.builder()
                .name("genericServiceDelegate")
                .displayName("⚙️ Generic Service")
                .description("Generic service delegate for common actions: email, logging, validation, notification")
                .actions(List.of(
                        ActionInfo.builder()
                                .name("sendEmail")
                                .displayName("Send Email")
                                .description("Send email to recipient")
                                .requiredVariables(Map.of(
                                        "recipient", "string - Email recipient",
                                        "subject", "string - Email subject",
                                        "body", "string - Email body"))
                                .build(),
                        ActionInfo.builder()
                                .name("log")
                                .displayName("Log Message")
                                .description("Log a custom message")
                                .requiredVariables(Map.of(
                                        "logMessage", "string - Message to log",
                                        "logLevel", "string - Log level (info/warn/error)"))
                                .build(),
                        ActionInfo.builder()
                                .name("validate")
                                .displayName("Validate Data")
                                .description("Validate data and set validationResult variable")
                                .requiredVariables(Map.of("dataToValidate", "any - Data to validate"))
                                .build(),
                        ActionInfo.builder()
                                .name("notify")
                                .displayName("Send Notification")
                                .description("Send notification via channel")
                                .requiredVariables(Map.of(
                                        "notificationChannel", "string - Channel (email/sms/push)",
                                        "notificationMessage", "string - Notification message"))
                                .build()))
                .build());

        // CamelDelegate
        DELEGATE_REGISTRY.put("camelDelegate", DelegateInfo.builder()
                .name("camelDelegate")
                .displayName("🐪 Apache Camel")
                .description("Execute Apache Camel routes for integration")
                .actions(List.of(
                        ActionInfo.builder()
                                .name("callExternalApi")
                                .displayName("Call External API")
                                .description("Call external REST API via Camel route")
                                .requiredVariables(Map.of("camelRoute", "string - Camel route name"))
                                .build(),
                        ActionInfo.builder()
                                .name("routeMessage")
                                .displayName("Route Message")
                                .description("Route message based on content")
                                .requiredVariables(Map.of("camelRoute", "string - Camel route name"))
                                .build(),
                        ActionInfo.builder()
                                .name("transformJson")
                                .displayName("Transform JSON")
                                .description("Transform JSON data")
                                .requiredVariables(Map.of("camelRoute", "string - Camel route name"))
                                .build()))
                .build());
    }

    /**
     * Get all available delegates
     */
    @GetMapping
    public ResponseEntity<List<DelegateInfo>> getAllDelegates() {
        log.info("Fetching all available delegates");
        return ResponseEntity.ok(new ArrayList<>(DELEGATE_REGISTRY.values()));
    }

    /**
     * Get a specific delegate by name
     */
    @GetMapping("/{name}")
    public ResponseEntity<DelegateInfo> getDelegate(@PathVariable String name) {
        log.info("Fetching delegate: {}", name);
        DelegateInfo delegate = DELEGATE_REGISTRY.get(name);
        if (delegate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(delegate);
    }

    /**
     * Get actions for a specific delegate
     */
    @GetMapping("/{name}/actions")
    public ResponseEntity<List<ActionInfo>> getDelegateActions(@PathVariable String name) {
        log.info("Fetching actions for delegate: {}", name);
        DelegateInfo delegate = DELEGATE_REGISTRY.get(name);
        if (delegate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(delegate.getActions());
    }
}
