CREATE TABLE resumes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name VARCHAR(60) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    extraction JSONB NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    work_years INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner_id, sha256)
);
CREATE TABLE resume_versions (
    resume_id BIGINT NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    extraction JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(resume_id, version)
);
CREATE TABLE evidence (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    page INTEGER NOT NULL,
    paragraph INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding public.vector(1024) NOT NULL
);
CREATE INDEX evidence_resume_idx ON evidence(resume_id);
CREATE INDEX resumes_owner_idx ON resumes(owner_id);