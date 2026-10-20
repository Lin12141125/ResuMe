import { DatabaseSync } from 'node:sqlite';
import { existsSync, realpathSync, statSync } from 'node:fs';
import { dirname, resolve, relative, isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';

export function audit(databasePath) {
    const source = realpathSync(databasePath);
    const root = dirname(source);
    const database = new DatabaseSync(source, { readOnly: true });
    try {
        database.exec('PRAGMA query_only=ON; BEGIN');
        const expected = ['iras_t_recruiter', 'iras_t_administrator', 'iras_t_resume', 'ries_resume_extracted'];
        const tables = new Set(database.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(row => row.name));
        if (expected.some(table => !tables.has(table))) throw new Error('Unsupported legacy schema; expected IRAS tables');
        const count = sql => Number(database.prepare(sql).get().total);
        const counts = Object.fromEntries(expected.map(table => [table, count(`SELECT count(*) AS total FROM ${table}`)]));
        const duplicateAccounts = count(`SELECT count(*) AS total FROM (
            SELECT account FROM (SELECT recruiterAccount AS account FROM iras_t_recruiter
            UNION ALL SELECT adminAccount AS account FROM iras_t_administrator)
            GROUP BY account HAVING count(*)>1)`);
        const invalidAccounts = count(`SELECT count(*) AS total FROM (
            SELECT recruiterAccount AS account FROM iras_t_recruiter UNION ALL SELECT adminAccount AS account FROM iras_t_administrator)
            WHERE account IS NULL OR length(trim(account))=0 OR length(account)>60`);
        const orphanResumes = count(`SELECT count(*) AS total FROM iras_t_resume r LEFT JOIN iras_t_recruiter u
            ON u.recruiterId=r.recruiterId WHERE u.recruiterId IS NULL`);
        const orphanExtractions = count(`SELECT count(*) AS total FROM ries_resume_extracted e LEFT JOIN iras_t_resume r
            ON r.resumecode=e.resume_id WHERE r.resumecode IS NULL`);
        let missingFiles = 0;
        let unsafePaths = 0;
        let unsupportedFiles = 0;
        for (const row of database.prepare('SELECT resumeurl FROM iras_t_resume').iterate()) {
            if (typeof row.resumeurl !== 'string' || !row.resumeurl) { missingFiles++; continue; }
            const candidate = resolve(root, row.resumeurl);
            const path = existsSync(candidate) ? realpathSync(candidate) : candidate;
            const within = relative(root, path);
            if (within.startsWith('..') || isAbsolute(within)) { unsafePaths++; continue; }
            if (!existsSync(path) || !statSync(path).isFile()) missingFiles++;
            if (!/\.(txt|pdf|docx)$/i.test(path)) unsupportedFiles++;
        }
        database.exec('ROLLBACK');
        return {
            mode: 'READ_ONLY_DRY_RUN', counts,
            issues: { duplicateAccounts, invalidAccounts, orphanResumes, orphanExtractions, missingFiles, unsafePaths, unsupportedFiles },
            passwordPolicy: 'RESET_REQUIRED_NO_PASSWORDS_READ_OR_COPIED',
            externalCalls: 0, writes: 0,
            importReady: false,
            blockers: ['Explicit account identity mapping and password-reset delivery required',
                'Personal-data consent and local-only extraction review required before import or embedding']
        };
    } finally { database.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    try {
        if (!process.argv[2]) throw new Error('Usage: node java-backend/tools/legacy-audit.mjs path/to/db.sqlite3');
        console.log(JSON.stringify(audit(process.argv[2]), null, 2));
    } catch {
        console.error('Legacy audit failed: verify the read-only database path and IRAS schema locally. No import performed.');
        process.exitCode = 1;
    }
}