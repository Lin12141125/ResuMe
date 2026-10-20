import { execFileSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync, cpSync, readdirSync, lstatSync } from 'node:fs';
import { dirname, join, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const configuration = { ...(existsSync(join(root, '.env.real')) ? parseEnv(readFileSync(join(root, '.env.real'), 'utf8')) : {}), ...process.env };
const action = process.argv[2];
const target = process.argv[3];
const username = configuration.DATABASE_USER || 'iras_agent';
const compose = ['compose', '--env-file', join(root, '.env.real'), '-f', join(root, 'compose.yaml'), 'exec', '-T', 'database'];
const databaseName = 'iras_agent';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
function docker(argumentsList, input) {
    try { return execFileSync('docker', [...compose, ...argumentsList], { input, maxBuffer: 256 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] }); }
    catch { throw new Error('Database backup command failed. Verify the local Compose database and permissions.'); }
}
function fileHashes(directory) {
    const result = {};
    if (!existsSync(directory)) return result;
    for (const item of readdirSync(directory, { recursive: true, withFileTypes: true })) {
        const path = join(item.parentPath, item.name);
        if (lstatSync(path).isSymbolicLink()) throw new Error('Symlinks are not supported in backup storage');
        if (item.isFile()) result[relative(directory, path).replaceAll('\\', '/')] = hash(readFileSync(path));
    }
    return result;
}
try {
    if (action === 'create') {
        if (process.argv[3] !== '--quiesced') throw new Error('Stop all Java writers, then use: create --quiesced');
        let reachable = false;
        try { await fetch(`http://127.0.0.1:${configuration.SERVER_PORT || 8080}/api/health`, { signal: AbortSignal.timeout(3000) }); reachable = true; }
        catch (error) { if (error.name === 'TimeoutError') throw new Error('Backend state unknown; stop writers before backup'); }
        if (reachable) throw new Error('Backend is still reachable. Stop all writers before backup');
        const directory = join(root, 'backups', new Date().toISOString().replaceAll(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8));
        const storage = resolve(root, configuration.RESUME_STORAGE || './data/resumes');
        const before = fileHashes(storage);
        const dump = docker(['pg_dump', '-U', username, '-d', databaseName, '-Fc', '--no-owner', '--no-acl']);
        mkdirSync(join(directory, 'files'), { recursive: true });
        if (existsSync(storage)) cpSync(storage, join(directory, 'files'), { recursive: true, errorOnExist: true });
        if (JSON.stringify(before) !== JSON.stringify(fileHashes(storage)) || JSON.stringify(before) !== JSON.stringify(fileHashes(join(directory, 'files'))))
            throw new Error('Storage changed during backup; snapshot rejected');
        writeFileSync(join(directory, 'database.dump'), dump, { flag: 'wx' });
        writeFileSync(join(directory, 'manifest.json'), JSON.stringify({ format: 1, database: databaseName, sha256: hash(dump), files: before }, null, 2), { flag: 'wx' });
        console.log(`Backup created: ${relative(root, directory)}`);
        console.log('Contains private data. Protect the backup directory; do not commit or upload it.');
    } else if (action === 'verify') {
        if (!target) throw new Error('Usage: verify path/to/backup');
        const directory = resolve(target);
        const manifest = JSON.parse(readFileSync(join(directory, 'manifest.json'), 'utf8'));
        const dump = readFileSync(join(directory, 'database.dump'));
        if (manifest.format !== 1 || manifest.sha256 !== hash(dump) || JSON.stringify(manifest.files) !== JSON.stringify(fileHashes(join(directory, 'files'))))
            throw new Error('Backup integrity verification failed');
        const scratch = 'iras_restore_' + randomUUID().replaceAll('-', '');
        docker(['createdb', '-U', username, scratch]);
        try {
            docker(['pg_restore', '-U', username, '-d', scratch, '--no-owner', '--no-acl', '--exit-on-error'], dump);
            const rows = JSON.parse(docker(['psql', '-U', username, '-d', scratch, '-At', '-c',
                "SELECT coalesce(json_agg(file_path),'[]') FROM public.resumes"]).toString().trim());
            for (const file of rows) {
                if (!Object.hasOwn(manifest.files, file)) throw new Error('Restored resume references a missing file');
            }
            console.log(`Restore verified in isolated temporary database; ${rows.length} resume files checked.`);
        } finally { docker(['dropdb', '-U', username, '--if-exists', scratch]); }
    } else throw new Error('Usage: node java-backend/tools/backup.mjs create --quiesced | verify path/to/backup');
} catch (error) {
    console.error(error.message?.startsWith('ENOENT') ? 'Backup input is missing' : error.message);
    process.exitCode = 1;
}