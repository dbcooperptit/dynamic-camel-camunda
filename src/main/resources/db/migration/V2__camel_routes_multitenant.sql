-- Flyway migration for dynamic Camel route persistence.
--
-- Notes:
-- - This project may already have the table created by Hibernate (ddl-auto=update).
-- - We use IF NOT EXISTS where supported to keep this migration idempotent across dev DBs.

-- Ensure table exists (Hibernate typically creates it). If it doesn't, create a minimal schema.
CREATE TABLE IF NOT EXISTS camel_routes (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255),
  tenant_id VARCHAR(128),
  description VARCHAR(2000),
  definition_json CLOB NOT NULL,
  status VARCHAR(32),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add columns that may be missing on older databases.
ALTER TABLE camel_routes ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128);
ALTER TABLE camel_routes ADD COLUMN IF NOT EXISTS description VARCHAR(2000);
ALTER TABLE camel_routes ADD COLUMN IF NOT EXISTS status VARCHAR(32);
ALTER TABLE camel_routes ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE camel_routes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE camel_routes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Helpful indexes for tenant-scoped listing and recency queries.
CREATE INDEX IF NOT EXISTS idx_camel_routes_tenant_id ON camel_routes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_camel_routes_updated_at ON camel_routes(updated_at);
