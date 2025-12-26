-- Seed baseline reference data for demo scenarios.
-- Uses MERGE for idempotency across dev runs when the database persists.

MERGE INTO accounts (account_number, account_name, balance, status, created_at, updated_at, version)
KEY(account_number)
VALUES
  ('1001234567', 'NGUYEN VAN A', 100000000.00, 'ACTIVE', CURRENT_TIMESTAMP, NULL, 0),
  ('1009876543', 'TRAN THI B', 50000000.00, 'ACTIVE', CURRENT_TIMESTAMP, NULL, 0),
  ('1005555555', 'LE VAN C', 200000000.00, 'ACTIVE', CURRENT_TIMESTAMP, NULL, 0),
  ('1003333333', 'PHAM THI D', 10000000.00, 'ACTIVE', CURRENT_TIMESTAMP, NULL, 0);

MERGE INTO transactions (
  transaction_id,
  source_account,
  dest_account,
  amount,
  description,
  status,
  saga_state,
  error_message,
  created_at,
  completed_at,
  compensated_at
)
KEY(transaction_id)
VALUES
  ('TXN-DEMO-001', '1001234567', '1009876543', 1500000.00, 'Demo transfer A -> B', 'COMPLETED', 'COMPLETED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
  ('TXN-DEMO-002', '1005555555', '1003333333', 5000000.00, 'Demo transfer C -> D', 'PENDING', 'IN_PROGRESS', NULL, CURRENT_TIMESTAMP, NULL, NULL);
