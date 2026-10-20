import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { homedir } from 'node:os';
import { spawnSync, spawn } from 'node:child_process';
import { parseEnv } from 'node:util';

const root = dirname(fileURLToPath(import.meta.url));
const local = join(root, '.env.real');
const environment = { ...(existsSync(local) ? parseEnv(readFileSync(local, 'utf8')) : {}), ...process.env };
const action = process.argv[2] || 'run';
if (!['run', 'test', 'test-real', 'admin', 'check'].includes(action)) throw new Error('Action: run, test, test-real, admin, check');
const required = ['DATABASE_URL', 'DATABASE_USER', 'DATABASE_PASSWORD', 'OPENAI_BASE_URL', 'OPENAI_MODEL', 'OPENAI_API_KEY', 'EMBEDDING_BASE_URL', 'EMBEDDING_MODEL', 'EMBEDDING_API_KEY'];
if (action === 'check') {
    for (const key of required) console.log(`${key}: ${environment[key]?.trim() ? 'configured' : 'missing'}`);
    process.exit(required.some(key => !environment[key]?.trim()) ? 2 : 0);
}
const javaHome = [environment.JAVA21_HOME, environment.JAVA_HOME, 'D:/DownLoad/Java/jdk21'].find(path =>
    path && existsSync(join(path, 'release')) && /^JAVA_VERSION="21[."]/m.test(readFileSync(join(path, 'release'), 'utf8')));
if (!javaHome) throw new Error('Configure JAVA21_HOME with JDK 21');
environment.JAVA_HOME = javaHome;
const windows = process.platform === 'win32';
const mavenHome = environment.MAVEN_HOME || join(homedir(), '.maven', 'maven-3.9.16');
const maven = existsSync(join(mavenHome, 'bin', windows ? 'mvn.cmd' : 'mvn')) ? join(mavenHome, 'bin', windows ? 'mvn.cmd' : 'mvn') : 'mvn';
const goals = action.startsWith('test') ? ['test', ...(action === 'test-real' ? ['-Diras.test.ai=true'] : [])] : ['package', '-DskipTests'];
const build = spawnSync(maven, ['-B', '-ntp', ...goals], { cwd: root, env: environment, stdio: 'inherit', shell: windows });
if (build.status !== 0) process.exit(build.status ?? 1);
if (action.startsWith('test')) process.exit(0);
const jars = (await import('node:fs')).readdirSync(join(root, 'target')).filter(name => name.endsWith('.jar'));
if (jars.length !== 1) throw new Error('Expected one executable jar in target');
const argumentsList = ['-jar', join(root, 'target', jars[0])];
if (action === 'admin') argumentsList.push('--iras.admin.create=true', '--server.port=0');
const child = spawn(join(javaHome, 'bin', windows ? 'java.exe' : 'java'), argumentsList, { cwd: root, env: environment, stdio: 'inherit' });
child.on('exit', code => process.exit(code ?? 1));
child.on('error', () => { console.error('Java process could not start'); process.exit(1); });
process.on('SIGINT', () => child.kill('SIGINT'));
process.on('SIGTERM', () => child.kill('SIGTERM'));