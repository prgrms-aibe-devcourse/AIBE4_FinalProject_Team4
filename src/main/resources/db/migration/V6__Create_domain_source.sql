-- ─────────────────────────────────────────────
-- domain_source 테이블 (BaseEntity: BIGSERIAL PK)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS domain_source (
    id          BIGSERIAL    PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);
