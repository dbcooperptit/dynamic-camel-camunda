package com.workflow.camunda.repository;

import com.workflow.camunda.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Account Repository with pessimistic locking for saga operations.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Find account with pessimistic write lock.
     * Used during debit/credit operations to prevent concurrent modifications.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(String accountNumber);

    /**
     * Find account without lock (for read-only operations)
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Check if account exists and is active
     */
    boolean existsByAccountNumberAndStatus(String accountNumber, String status);
}
