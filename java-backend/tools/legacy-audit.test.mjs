import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { audit } from './legacy-audit.mjs';

test('legacy audit is read-only, reports aggregates and detects migration hazards', () => {
    const root = mkdtempSync(join(tmpdir(), 'iras-audit-'));
    const path = join(root, 'legacy.sqlite3');
    try {
        const database = new DatabaseSync(path);
        database.exec(`CREATE TABLE iras_t_recruiter(recruiterId INTEGER, recruiterAccount TEXT, password TEXT);
            CREATE TABLE iras_t_administrator(adminAccount TEXT, password TEXT);
            CREATE TABLE iras_t_resume(resumecode INTEGER, recruiterId INTEGER, resumeurl TEXT);
            CREATE TABLE ries_resume_extracted(resume_id INTEGER);
            INSERT INTO iras_t_recruiter VALUES(1,'private-account','never-read-secret');
            INSERT INTO iras_t_administrator VALUES('private-account','never-read-secret');
            INSERT INTO iras_t_resume VALUES(1,1,'synthetic.txt'),(2,99,'missing.pdf'),(3,1,'../outside.txt');
            INSERT INTO ries_resume_extracted VALUES(999);`);
        database.close();
        writeFileSync(join(root, 'synthetic.txt'), 'Synthetic data only');
        const before = readFileSync(path);
        const report = audit(path);
        assert.equal(report.issues.duplicateAccounts, 1);
        assert.equal(report.issues.orphanResumes, 1);
        assert.equal(report.issues.orphanExtractions, 1);
        assert.equal(report.issues.missingFiles, 1);
        assert.equal(report.issues.unsafePaths, 1);
        assert.equal(report.writes, 0);
        assert.equal(report.externalCalls, 0);
        assert.doesNotMatch(JSON.stringify(report), /private-account|never-read-secret|synthetic.txt/);
        assert.deepEqual(readFileSync(path), before);
    } finally { rmSync(root, { recursive: true, force: true }); }
});