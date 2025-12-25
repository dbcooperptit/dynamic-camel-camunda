package com.workflow.camunda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Entity - Tracks money transfer transactions with saga state.
 * Used for saga pattern compensation and audit trail.
 */
@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false, length = 50)
    private String transactionId;

    @Column(name = "source_account", nullable = false, length = 20)
    private String sourceAccount;

    @Column(name = "dest_account", nullable = false, length = 20)
    private String destAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Transaction status:
     * - PENDING: Initial state
     * - COMPLETED: Successfully completed
     * - FAILED: Failed (no debit occurred)
     * - COMPENSATED: Rolled back after partial completion
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Saga state for tracking compensation:
     * - STARTED: Transaction initiated
     * - DEBITED: Source account debited
     * - CREDITED: Destination credited (final success state)
     * - COMPENSATION_STARTED: Rollback in progress
     * - COMPENSATION_COMPLETED: Rollback finished
     */
    @Column(name = "saga_state", length = 30)
    @Builder.Default
    private String sagaState = "STARTED";

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensated_at")
    private LocalDateTime compensatedAt;

    // Helper methods for state transitions
    public void markDebited() {
        this.sagaState = "DEBITED";
    }

    public void markCredited() {
        this.sagaState = "CREDITED";
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void markCompensated() {
        this.sagaState = "COMPENSATION_COMPLETED";
        this.status = "COMPENSATED";
        this.compensatedAt = LocalDateTime.now();
    }
}
