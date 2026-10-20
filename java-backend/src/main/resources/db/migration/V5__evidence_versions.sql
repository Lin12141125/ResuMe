ALTER TABLE evidence ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE evidence ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT'
    CHECK (source IN ('DOCUMENT', 'CORRECTION'));
CREATE INDEX evidence_version_idx ON evidence(resume_id, version);