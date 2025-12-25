package com.workflow.camunda.delegate;

import com.workflow.camunda.entity.Account;
import com.workflow.camunda.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Money Transfer Delegate - Handles all service task actions for the money
 * transfer workflow.
 * 
 * Now uses AccountService with real database operations and Saga pattern.
 * 
 * Actions:
 * - validateSourceAccount: Validate source account exists and is active
 * - validateDestAccount: Validate destination account exists
 * - checkBalance: Check if source account has sufficient balance
 * - executeTransfer: Execute the actual money transfer with saga compensation
 * - sendNotification: Send notification to customer
 */
@Component("moneyTransferDelegate")
@Slf4j
@RequiredArgsConstructor
public class MoneyTransferDelegate implements JavaDelegate {

    private final AccountService accountService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        String activityName = execution.getCurrentActivityName();
        String action = (String) execution.getVariable("action");

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║ 💰 MONEY TRANSFER SERVICE                                    ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║ Process ID: {}", processInstanceId);
        log.info("║ Activity: {} ({})", activityName, activityId);
        log.info("║ Action: {}", action);
        log.info("╚══════════════════════════════════════════════════════════════╝");

        if (action == null) {
            log.warn("No action specified, skipping execution");
            return;
        }

        switch (action.toLowerCase()) {
            case "validatesourceaccount":
                handleValidateSourceAccount(execution);
                break;
            case "validatedestaccount":
                handleValidateDestAccount(execution);
                break;
            case "checkbalance":
                handleCheckBalance(execution);
                break;
            case "executetransfer":
                handleExecuteTransfer(execution);
                break;
            case "sendnotification":
                handleSendNotification(execution);
                break;
            default:
                log.warn("Unknown action: {}", action);
        }
    }

    /**
     * Validate source account exists and is active
     */
    private void handleValidateSourceAccount(DelegateExecution execution) {
        String sourceAccount = (String) execution.getVariable("sourceAccount");

        log.info("🔍 Validating source account: {}", sourceAccount);

        if (sourceAccount == null) {
            log.error("❌ Source account is NULL - missing required variable");
            execution.setVariable("sourceAccountValid", false);
            return;
        }

        // Use AccountService for real validation
        boolean isValid = accountService.validateAccount(sourceAccount);

        if (isValid) {
            Optional<Account> account = accountService.getAccount(sourceAccount);
            log.info("✅ Source account {} is VALID and ACTIVE", sourceAccount);
            execution.setVariable("sourceAccountValid", true);
            execution.setVariable("sourceAccountName", account.map(Account::getAccountName).orElse("Unknown"));
        } else {
            log.error("❌ Source account {} NOT FOUND or INACTIVE", sourceAccount);
            execution.setVariable("sourceAccountValid", false);
        }
    }

    /**
     * Validate destination account exists
     */
    private void handleValidateDestAccount(DelegateExecution execution) {
        String destAccount = (String) execution.getVariable("destAccount");

        log.info("🔍 Validating destination account: {}", destAccount);

        if (destAccount == null) {
            log.error("❌ Destination account is NULL - missing required variable");
            execution.setVariable("destAccountValid", false);
            return;
        }

        // Use AccountService for real validation
        boolean isValid = accountService.validateAccount(destAccount);

        if (isValid) {
            Optional<Account> account = accountService.getAccount(destAccount);
            log.info("✅ Destination account {} is VALID", destAccount);
            execution.setVariable("destAccountValid", true);
            execution.setVariable("destAccountName", account.map(Account::getAccountName).orElse("Unknown"));
        } else {
            log.error("❌ Destination account {} NOT FOUND or INACTIVE", destAccount);
            execution.setVariable("destAccountValid", false);
        }
    }

    /**
     * Check if source account has sufficient balance
     */
    private void handleCheckBalance(DelegateExecution execution) {
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        Object amountObj = execution.getVariable("amount");

        BigDecimal amount;
        if (amountObj instanceof Number) {
            amount = new BigDecimal(amountObj.toString());
        } else {
            amount = new BigDecimal(String.valueOf(amountObj));
        }

        log.info("💰 Checking balance for account: {}", sourceAccount);
        log.info("   Requested amount: {} VND", formatCurrency(amount));

        // Use AccountService for real balance check
        BigDecimal currentBalance = accountService.getBalance(sourceAccount);
        boolean sufficient = accountService.hasSufficientBalance(sourceAccount, amount);

        log.info("   Current balance: {} VND", formatCurrency(currentBalance));
        log.info("   Balance sufficient: {}", sufficient ? "✅ YES" : "❌ NO");

        execution.setVariable("currentBalance", currentBalance.longValue());
        execution.setVariable("balanceSufficient", sufficient);

        if (!sufficient) {
            execution.setVariable("balanceShortfall", amount.subtract(currentBalance).longValue());
        }
    }

    /**
     * Execute the actual money transfer
     */
    private void handleExecuteTransfer(DelegateExecution execution) {
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String destAccount = (String) execution.getVariable("destAccount");
        Object amountObj = execution.getVariable("amount");
        String description = (String) execution.getVariable("description");

        // Validate required variables
        if (sourceAccount == null || sourceAccount.isBlank()) {
            log.error("❌ Missing sourceAccount! Available variables: {}", execution.getVariables().keySet());
            throw new BpmnError("VALIDATION_ERROR", "sourceAccount is required. Provide it when starting the process.");
        }
        if (destAccount == null || destAccount.isBlank()) {
            log.error("❌ Missing destAccount! Available variables: {}", execution.getVariables().keySet());
            throw new BpmnError("VALIDATION_ERROR", "destAccount is required. Provide it when starting the process.");
        }
        if (amountObj == null) {
            log.error("❌ Missing amount! Available variables: {}", execution.getVariables().keySet());
            throw new BpmnError("VALIDATION_ERROR", "amount is required. Provide it when starting the process.");
        }

        BigDecimal amount;
        if (amountObj instanceof Number) {
            amount = new BigDecimal(amountObj.toString());
        } else {
            amount = new BigDecimal(String.valueOf(amountObj));
        }

        log.info("⚡ EXECUTING SAGA TRANSFER");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║ From Account: {}", sourceAccount);
        log.info("║ To Account: {}", destAccount);
        log.info("║ Amount: {} VND", formatCurrency(amount));
        log.info("║ Description: {}", description);
        log.info("╚══════════════════════════════════════════════════════════════╝");

        try {
            // Execute transfer using AccountService saga pattern
            // This handles: createTransaction -> debit -> credit -> compensation if needed
            String transactionId = accountService.executeTransfer(
                    sourceAccount, destAccount, amount, description);

            log.info("✅ SAGA TRANSFER SUCCESSFUL!");
            log.info("   Transaction ID: {}", transactionId);

            execution.setVariable("transferSuccess", true);
            execution.setVariable("transactionId", transactionId);
            execution.setVariable("transferTimestamp", LocalDateTime.now().format(DATE_FORMAT));
            execution.setVariable("transferStatus", "COMPLETED");

        } catch (Exception e) {
            log.error("❌ SAGA TRANSFER FAILED: {}", e.getMessage());
            execution.setVariable("transferSuccess", false);
            execution.setVariable("transferStatus", "FAILED");
            execution.setVariable("transferError", e.getMessage());

            // Throw BPMN error for workflow to handle
            throw new BpmnError("TRANSFER_FAILED", e.getMessage());
        }
    }

    /**
     * Send notification to customer (email/SMS)
     */
    private void handleSendNotification(DelegateExecution execution) {
        String transactionId = (String) execution.getVariable("transactionId");
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String destAccount = (String) execution.getVariable("destAccount");
        Object amountObj = execution.getVariable("amount");

        BigDecimal amount;
        if (amountObj instanceof Number) {
            amount = new BigDecimal(amountObj.toString());
        } else {
            amount = new BigDecimal(String.valueOf(amountObj));
        }

        log.info("📧 SENDING NOTIFICATIONS");
        log.info("   Transaction ID: {}", transactionId);

        // Simulate sending SMS
        simulateProcessing(200);
        log.info("   📱 SMS sent to source account holder");

        // Simulate sending Email
        simulateProcessing(200);
        log.info("   📧 Email sent to source account holder");

        // Simulate push notification
        simulateProcessing(100);
        log.info("   🔔 Push notification sent");

        String notificationMessage = String.format(
                "Chuyển khoản thành công %s VND từ TK %s đến TK %s. Mã GD: %s",
                formatCurrency(amount), maskAccount(sourceAccount), maskAccount(destAccount), transactionId);

        execution.setVariable("notificationSent", true);
        execution.setVariable("notificationMessage", notificationMessage);
        execution.setVariable("notificationTimestamp", LocalDateTime.now().format(DATE_FORMAT));

        log.info("✅ All notifications sent successfully");
    }

    /**
     * Simulate processing delay
     */
    private void simulateProcessing(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Format currency with thousand separators
     */
    private String formatCurrency(BigDecimal amount) {
        return String.format("%,d", amount.longValue());
    }

    /**
     * Mask account number for security (show first 3 and last 3 digits)
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 6) {
            return account;
        }
        return account.substring(0, 3) + "****" + account.substring(account.length() - 3);
    }
}
