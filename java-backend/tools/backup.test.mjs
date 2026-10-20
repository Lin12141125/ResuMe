import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, writeFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, delimiter } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const environment = { ...(existsSync(join(root, '.env.real')) ? parseEnv(readFileSync(join(root, '.env.real'), 'utf8')) : {}), ...process.env };
const username = environment.DATABASE_USER || 'iras_agent';
if (process.platform === 'win32') {
    const pathKey = Object.keys(environment).find(key => key.toLowerCase() === 'path') || 'Path';
    environment[pathKey] = ['C:\\Program Files\\Docker\\Docker\\resources\\bin', environment[pathKey] || ''].join(delimiter);
}
const compose = ['compose', '--env-file', join(root, '.env.real'), '-f', join(root, 'compose.yaml'), 'exec', '-T', 'database'];
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

function docker(argumentsList, input) {
    try {
        return execFileSync('docker', [...compose, ...argumentsList], { env: environment, input, maxBuffer: 32 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] });
    } catch {
        throw new Error('Isolated backup fixture command failed; local Compose database must be healthy');
    }
}

test('real restore validates nonempty files, tampering and missing references', { timeout: 120_000 }, () => {
    const directory = mkdtempSync(join(tmpdir(), 'iras-backup-test-'));
    const database = 'iras_backup_test_' + randomUUID().replaceAll('-', '');
    let created = false;
    try {
        docker(['createdb', '-U', username, database]);
        created = true;
        const migrations = join(root, 'src/main/resources/db/migration');
        const sql = readdirSync(migrations).filter(name => /^V\d+__.*\.sql$/.test(name))
            .sort((left, right) => Number(left.match(/^V(\d+)/)[1]) - Number(right.match(/^V(\d+)/)[1]))
            .map(name => readFileSync(join(migrations, name), 'utf8')).join('\n');
        docker(['psql', '-U', username, '-d', database, '-v', 'ON_ERROR_STOP=1'], sql);
        const content = Buffer.from('Synthetic Candidate\nJava and PostgreSQL testing only.\n');
        docker(['psql', '-U', username, '-d', database, '-v', 'ON_ERROR_STOP=1'],
            `INSERT INTO accounts(account,password_hash,role) VALUES ('synthetic-restore','disabled-test-only','USER');
             INSERT INTO resumes(owner_id,name,sha256,file_path,extraction)
             SELECT id,'synthetic.txt','${hash(content)}','synthetic.txt','{}'::jsonb FROM accounts;`);
        const dump = docker(['pg_dump', '-U', username, '-d', database, '-Fc', '--no-owner', '--no-acl']);
        mkdirSync(join(directory, 'files'));
        writeFileSync(join(directory, 'files/synthetic.txt'), content);
        writeFileSync(join(directory, 'database.dump'), dump);
        const manifest = { format: 1, database: 'iras_agent', sha256: hash(dump), files: { 'synthetic.txt': hash(content) } };
        writeFileSync(join(directory, 'manifest.json'), JSON.stringify(manifest));
        const verify = () => spawnSync(process.execPath, [join(root, 'tools/backup.mjs'), 'verify', directory], { env: environment, encoding: 'utf8', timeout: 30_000 });
        const valid = verify();
        assert.equal(valid.status, 0, valid.stderr);
        assert.match(valid.stdout, /1 resume files checked/);
        writeFileSync(join(directory, 'files/synthetic.txt'), 'corrupted');
        const corrupted = verify();
        assert.equal(corrupted.status, 1);
        assert.match(corrupted.stderr, /integrity verification failed/);
        rmSync(join(directory, 'files/synthetic.txt'));
        manifest.files = {};
        writeFileSync(join(directory, 'manifest.json'), JSON.stringify(manifest));
        const missing = verify();
        assert.equal(missing.status, 1);
        assert.match(missing.stderr, /references a missing file/);
    } finally {
        try { if (created) docker(['dropdb', '-U', username, '--if-exists', database]); }
        finally { rmSync(directory, { recursive: true, force: true }); }
    }
});