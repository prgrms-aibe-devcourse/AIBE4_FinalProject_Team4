-- =====================================================================
-- V13: Alter TIMESTAMP columns to TIMESTAMPTZ
-- =====================================================================

-- ---------------------------------------------------------------------
-- V3: company, member
-- ---------------------------------------------------------------------
ALTER TABLE company
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE member
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- ---------------------------------------------------------------------
-- V4: project, project_member, project_api_key
-- ---------------------------------------------------------------------
ALTER TABLE project
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE project_member
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE project_api_key
    ALTER COLUMN revoked_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- ---------------------------------------------------------------------
-- V5: domain_source, document_group, document_metadata
-- ---------------------------------------------------------------------
ALTER TABLE domain_source
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE document_group
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE document_metadata
    ALTER COLUMN uploaded_at TYPE TIMESTAMPTZ,
    ALTER COLUMN reuploaded_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- ---------------------------------------------------------------------
-- V7: invitation
-- ---------------------------------------------------------------------
ALTER TABLE invitation
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ,
    ALTER COLUMN used_at TYPE TIMESTAMPTZ,
    ALTER COLUMN revoked_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
