package com.workflow.camunda.repository;

import com.workflow.camunda.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Transaction Repository for saga state tracking and audit.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Find transaction by unique transaction ID
     */
    Optional<Transaction> findByTransactionId(String transactionId);

    /**
     * Find transactions by saga state (for compensation retry)
     */
    List<Transaction> findBySagaState(String sagaState);

    /**
     * Find transactions by source account
     */
    List<Transaction> findBySourceAccountOrderByCreatedAtDesc(String sourceAccount);

    /**
     * Find transactions by destination account
     */
    List<Transaction> findByDestAccountOrderByCreatedAtDesc(String destAccount);

    /**
     * Find pending transactions that need compensation
     */
    List<Transaction> findBySagaStateAndStatus(String sagaState, String status);
}
