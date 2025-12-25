package com.workflow.camunda.service;

import com.workflow.camunda.entity.Account;
import com.workflow.camunda.entity.Transaction;
import com.workflow.camunda.repository.AccountRepository;
import com.workflow.camunda.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Service - Handles saga-based money transfer operations.
 * 
 * Saga Pattern Implementation:
 * 1. debit() - Debit source account, mark saga state as DEBITED
 * 2. credit() - Credit destination account, mark saga state as CREDITED
 * 3. compensateDebit() - Rollback debit if credit fails
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Validate if account exists and is active
     */
    public boolean validateAccount(String accountNumber) {
        return accountRepository.existsByAccountNumberAndStatus(accountNumber, "ACTIVE");
    }

    /**
     * Get account by number (read-only)
     */
    public Optional<Account> getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    /**
     * Check if account has sufficient balance
     */
    public boolean hasSufficientBalance(String accountNumber, BigDecimal amount) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(account -> account.hasSufficientBalance(amount))
                .orElse(false);
    }

    /**
     * Get current balance
     */
    public BigDecimal getBalance(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(Account::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Create a new transaction record
     */
    @Transactional
    public Transaction createTransaction(String sourceAccount, String destAccount,
            BigDecimal amount, String description) {
        String transactionId = "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .sourceAccount(sourceAccount)
                .destAccount(destAccount)
                .amount(amount)
                .description(description)
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * SAGA STEP 1: Debit source account
     * Marks saga state as DEBITED for potential compensation
     */
    @Transactional
    public void debit(String accountNumber, BigDecimal amount, String transactionId) {
        log.info("💸 SAGA DEBIT: Account={}, Amount={}, TxnId={}",
                accountNumber, amount, transactionId);

        // Get account with pessimistic lock
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        // Validate account is active
        if (!account.isActive()) {
            throw new IllegalStateException("Account is not active: " + accountNumber);
        }

        // Check balance
        if (!account.hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient balance. Available: " + account.getBalance());
        }

        // Perform debit
        account.debit(amount);
        accountRepository.save(account);

        // Update transaction saga state
        transactionRepository.findByTransactionId(transactionId)
                .ifPresent(txn -> {
                    txn.markDebited();
                    transactionRepository.save(txn);
                });

        log.info("✅ DEBIT SUCCESS: New balance={}", account.getBalance());
    }

    /**
     * SAGA STEP 2: Credit destination account
     * Marks saga state as CREDITED (final success state)
     */
    @Transactional
    public void credit(String accountNumber, BigDecimal amount, String transactionId) {
        log.info("💰 SAGA CREDIT: Account={}, Amount={}, TxnId={}",
                accountNumber, amount, transactionId);

        // Get account with pessimistic lock
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        // Validate account is active
        if (!account.isActive()) {
            throw new IllegalStateException("Destination account is not active: " + accountNumber);
        }

        // Perform credit
        account.credit(amount);
        accountRepository.save(account);

        // Update transaction to completed
        transactionRepository.findByTransactionId(transactionId)
                .ifPresent(txn -> {
                    txn.markCredited();
                    transactionRepository.save(txn);
                });

        log.info("✅ CREDIT SUCCESS: New balance={}", account.getBalance());
    }

    /**
     * SAGA COMPENSATION: Rollback debit if credit fails
     * Only called when saga state is DEBITED
     */
    @Transactional
    public void compensateDebit(String accountNumber, BigDecimal amount, String transactionId) {
        log.warn("🔄 SAGA COMPENSATION: Reversing debit for TxnId={}", transactionId);

        // Get transaction to verify it needs compensation
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (!"DEBITED".equals(transaction.getSagaState())) {
            log.warn("⚠️ Transaction not in DEBITED state, skipping compensation");
            return;
        }

        // Get account and reverse the debit (credit back)
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        account.credit(amount); // Reverse the debit
        accountRepository.save(account);

        // Mark transaction as compensated
        transaction.markCompensated();
        transactionRepository.save(transaction);

        log.info("✅ COMPENSATION COMPLETE: Amount {} returned to {}", amount, accountNumber);
    }

    /**
     * Execute full transfer with saga pattern
     * Returns transaction ID on success, throws exception on failure
     */
    @Transactional
    public String executeTransfer(String sourceAccount, String destAccount,
            BigDecimal amount, String description) {
        log.info("🚀 EXECUTING SAGA TRANSFER: {} → {}, Amount={}",
                sourceAccount, destAccount, amount);

        // Create transaction record
        Transaction transaction = createTransaction(sourceAccount, destAccount, amount, description);
        String transactionId = transaction.getTransactionId();

        try {
            // Step 1: Debit source
            debit(sourceAccount, amount, transactionId);

            // Step 2: Credit destination (may fail)
            credit(destAccount, amount, transactionId);

            log.info("✅ TRANSFER COMPLETED: TxnId={}", transactionId);
            return transactionId;

        } catch (Exception e) {
            log.error("❌ TRANSFER FAILED: TxnId={}, Error={}", transactionId, e.getMessage());

            // Check if we need to compensate (debit happened but credit failed)
            Transaction txn = transactionRepository.findByTransactionId(transactionId).orElse(null);
            if (txn != null && "DEBITED".equals(txn.getSagaState())) {
                try {
                    compensateDebit(sourceAccount, amount, transactionId);
                } catch (Exception compEx) {
                    log.error("❌ COMPENSATION FAILED: {}", compEx.getMessage());
                    txn.setErrorMessage("Transfer and compensation failed: " + compEx.getMessage());
                    transactionRepository.save(txn);
                }
            } else if (txn != null) {
                txn.markFailed(e.getMessage());
                transactionRepository.save(txn);
            }

            throw new RuntimeException("Transfer failed: " + e.getMessage(), e);
        }
    }
}
