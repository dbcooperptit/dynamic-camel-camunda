-- Seed data for accounts table
-- This file is auto-executed by Spring Boot on startup
-- Using MERGE to avoid duplicate errors when database persists between restarts

MERGE INTO accounts (account_number, account_name, balance, status, created_at, version)
KEY(account_number)
VALUES 
('1001234567', 'NGUYEN VAN A', 100000000.00, 'ACTIVE', CURRENT_TIMESTAMP, 0),
('1009876543', 'TRAN THI B', 50000000.00, 'ACTIVE', CURRENT_TIMESTAMP, 0),
('1005555555', 'LE VAN C', 200000000.00, 'ACTIVE', CURRENT_TIMESTAMP, 0),
('1003333333', 'PHAM THI D', 10000000.00, 'ACTIVE', CURRENT_TIMESTAMP, 0);

-- Note: Balance in VND
-- 1001234567: 100 triệu VND
-- 1009876543: 50 triệu VND
-- 1005555555: 200 triệu VND
-- 1003333333: 10 triệu VND (for testing insufficient balance)

