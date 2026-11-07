'use strict';
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const mineflayer = require('mineflayer');

const PORT = 25599;
const COOLDOWN_WAIT = 2400;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

let serverDir;
let javaExe;
let consoleLog;
let configFile;
const results = [];

function parseArgs(scriptName) {
	const [dir, java, report] = process.argv.slice(2);
	if (!dir || !java) {
		console.error(`uzycie: node ${scriptName} <folder-serwera> <java.exe> [plik-raportu]`);
		process.exit(2);
	}
	serverDir = dir;
	javaExe = java;
	consoleLog = path.join(dir, 'e2e-console.log');
	configFile = path.join(dir, 'config', 'easytp.json');
	console.log('serverDir =', JSON.stringify(dir), ' java =', JSON.stringify(java));
	return { serverDir: dir, javaExe: java, reportFile: report };
}

function check(name, ok, detail = '') {
	results.push({ name, ok: !!ok, detail });
	console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : '\n        -> ' + detail}`);
}

class Server {
	constructor() {
		this.lines = [];
		this.watchers = [];
		this.proc = null;
		this.exited = null;
	}

	start() {
		this.lines = [];
		this.proc = spawn(javaExe, [
			'-Xmx1G', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
			'-jar', path.join(serverDir, 'fabric-server-launch.jar'), 'nogui',
		], { cwd: serverDir, stdio: ['pipe', 'pipe', 'pipe'] });
		for (const stream of [this.proc.stdout, this.proc.stderr]) {
			let rest = '';
			stream.on('data', (chunk) => {
				rest += chunk.toString('utf8');
				const parts = rest.split(/\r?\n/);
				rest = parts.pop();
				for (const line of parts) this._line(line);
			});
		}
		this.exited = new Promise((resolve) => this.proc.on('exit', (code) => resolve(code)));
	}

	_line(line) {
		this.lines.push(line);
		fs.appendFileSync(consoleLog, line + '\n');
		for (const w of [...this.watchers]) {
			if (w.re.test(line)) {
				this.watchers.splice(this.watchers.indexOf(w), 1);
				w.resolve(line);
			}
		}
	}

	waitFor(re, timeoutMs, fromIndex = this.lines.length) {
		for (let i = fromIndex; i < this.lines.length; i++) {
			if (re.test(this.lines[i])) return Promise.resolve(this.lines[i]);
		}
		return new Promise((resolve, reject) => {
			const cleanup = () => {
				clearTimeout(timer);
				const i = this.watchers.indexOf(w);
				if (i >= 0) this.watchers.splice(i, 1);
				this.proc.off('exit', onExit);
			};
			const w = { re, resolve: (l) => { cleanup(); resolve(l); } };
			const timer = setTimeout(() => { cleanup(); reject(new Error(`timeout czekajac na ${re}`)); }, timeoutMs);
			const onExit = (code) => { cleanup(); reject(new Error(`serwer zakonczyl prace (kod ${code}) czekajac na ${re}`)); };
			this.watchers.push(w);
			this.proc.once('exit', onExit);
		});
	}

	async command(cmd, doneRe, timeoutMs = 10000) {
		const from = this.lines.length;
		this.proc.stdin.write(cmd + '\n');
		if (doneRe) return this.waitFor(doneRe, timeoutMs, from);
		await sleep(400);
		return null;
	}

	async stop() {
		if (!this.proc || this.proc.exitCode !== null) return;
		this.proc.stdin.write('stop\n');
		const code = await Promise.race([this.exited, sleep(60000).then(() => 'timeout')]);
		if (code === 'timeout') this.proc.kill();
	}
}

function createBot(name) {
	const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, auth: 'offline', version: '1.21.11' });
	bot.log = [];
	bot.raw = [];
	bot.kickReason = null;
	bot.ended = false;
	bot.on('messagestr', (message) => { bot.log.push(message); });
	bot.on('message', (jsonMsg) => { bot.raw.push(JSON.stringify(jsonMsg)); });
	bot.on('error', (e) => console.log(`[${name}] blad klienta: ${e.message}`));
	bot.on('kicked', (reason) => {
		bot.kickReason = typeof reason === 'string' ? reason : JSON.stringify(reason);
		console.log(`[${name}] wyrzucony: ${bot.kickReason.slice(0, 200)}`);
	});
	bot.once('end', () => { bot.ended = true; });
	return bot;
}

async function connectBot(name) {
	const bot = createBot(name);
	await new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`${name}: nie wszedl na serwer`)), 60000);
		bot.once('spawn', () => { clearTimeout(timer); resolve(); });
		bot.once('end', (reason) => { clearTimeout(timer); reject(new Error(`${name}: rozlaczony (${reason})`)); });
	});
	await sleep(700);
	return bot;
}

function tryJoin(name, timeoutMs = 30000) {
	return new Promise((resolve) => {
		const bot = createBot(name);
		let done = false;
		const finish = (result) => {
			if (done) return;
			done = true;
			clearTimeout(timer);
			resolve({ ...result, bot });
		};
		const timer = setTimeout(() => finish({ joined: false, reason: 'timeout' }), timeoutMs);
		bot.once('spawn', () => setTimeout(() => finish({ joined: true, reason: null }), 700));
		bot.once('end', (why) => setTimeout(() => finish({ joined: false, reason: bot.kickReason || String(why) }), 100));
	});
}

async function waitEnd(bot, timeoutMs = 8000) {
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		if (bot.ended) return true;
		await sleep(50);
	}
	return bot.ended;
}

async function say(bot, line, re, timeoutMs = 4000) {
	const from = bot.log.length;
	bot.chat(line);
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		const hit = bot.log.slice(from).find((m) => re.test(m));
		if (hit) return { ok: true, hit, all: bot.log.slice(from) };
		await sleep(50);
	}
	return { ok: false, hit: null, all: bot.log.slice(from) };
}

async function expectSay(name, bot, line, re, timeoutMs) {
	const r = await say(bot, line, re, timeoutMs);
	check(name, r.ok, r.ok ? '' : `${bot.username}: ${line} -> ${JSON.stringify(r.all)}`);
	return r;
}

async function expectSilence(name, bot, re, ms = 1500) {
	const from = bot.log.length;
	await sleep(ms);
	const hits = bot.log.slice(from).filter((m) => re.test(m));
	check(name, hits.length === 0, `${bot.username} dostal: ${JSON.stringify(hits)}`);
}

async function waitMsg(bot, re, timeoutMs = 4000, from = 0) {
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		const hit = bot.log.slice(from).find((m) => re.test(m));
		if (hit) return hit;
		await sleep(50);
	}
	console.log(`   (brak ${re} u ${bot.username}; wiadomosci: ${JSON.stringify(bot.log.slice(from))})`);
	return null;
}

const near = (bot, x, y, z, tol = 0.8) => {
	const p = bot.entity.position;
	return Math.abs(p.x - x) <= tol && Math.abs(p.y - y) <= tol && Math.abs(p.z - z) <= tol;
};
const fmt = (bot) => {
	const p = bot.entity.position;
	return `(${p.x.toFixed(2)}, ${p.y.toFixed(2)}, ${p.z.toFixed(2)})`;
};
async function waitNear(bot, x, y, z, tol = 0.8, timeoutMs = 5000) {
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		if (bot.entity && near(bot, x, y, z, tol)) return true;
		await sleep(50);
	}
	return false;
}

async function consoleTp(srv, bot, x, y, z, dimension) {
	const prefix = dimension ? `execute in ${dimension} run ` : '';
	await srv.command(`${prefix}tp ${bot.username} ${x} ${y} ${z}`, new RegExp(`Teleported ${bot.username} to`));
	const ok = await waitNear(bot, x, y, z, 0.5);
	if (!ok) console.log(`   (uwaga: ${bot.username} nie dotarl na ${x},${y},${z}; jest ${fmt(bot)})`);
	return ok;
}

function setConfig(overrides) {
	const base = { language: 'pl', maxHomes: 3, tpaTimeoutSeconds: 5, cooldownSeconds: 2, warmupSeconds: 0, opsBypassLimits: true };
	fs.writeFileSync(configFile, JSON.stringify({ ...base, ...overrides }, null, 2));
}
async function reloadConfig(srv) {
	await srv.command('easytp reload', /konfigurac|configuration reloaded/i);
	await sleep(300);
}

async function opPlayer(srv, name) {
	await srv.command(`op ${name}`, new RegExp(`Made ${name} a server operator`));
	await sleep(800);
}

const angleDiff = (a, b) => Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));

async function runMain(scenario, reportFile) {
	const srv = new Server();
	fs.writeFileSync(consoleLog, '');
	if (!fs.existsSync(configFile)) {
		fs.mkdirSync(path.dirname(configFile), { recursive: true });
		setConfig({});
	}
	srv.start();
	let failed = false;
	try {
		await scenario(srv);
	} catch (e) {
		failed = true;
		console.log('BLAD SCENARIUSZA:', e.stack || e.message);
		check('scenariusz dobiegl konca bez wyjatku', false, e.message);
	} finally {
		await srv.stop().catch(() => {});
	}
	const bad = results.filter((r) => !r.ok);
	const summary = `\nPodsumowanie: ${results.length - bad.length}/${results.length} testow OK` + (bad.length ? `, niepowodzenia: ${bad.length}` : '');
	console.log(summary);
	if (reportFile) {
		fs.writeFileSync(reportFile, results.map((r) => `${r.ok ? 'PASS' : 'FAIL'}  ${r.name}${r.ok || !r.detail ? '' : '\n        -> ' + r.detail}`).join('\n') + summary + '\n');
	}
	process.exit(failed || bad.length ? 1 : 0);
}

function errorLines(srv) {
	const noise = /No key layers in MapLike|Yggdrasil Key Fetcher|Failed to request yggdrasil public key|SSLHandshakeException|SSL peer shut down|EOFException|SocketTimeoutException|UnknownHostException|ConnectException/;
	return srv.lines.filter((l) => /\[(?:[^\]]*)\/(ERROR|FATAL)\]|Exception in thread|Caused by|NullPointerException/.test(l) && !noise.test(l));
}

module.exports = {
	PORT, COOLDOWN_WAIT, sleep, parseArgs, check, Server, createBot, connectBot, tryJoin, waitEnd, say, expectSay,
	expectSilence, waitMsg, near, fmt, waitNear, consoleTp, setConfig, reloadConfig, opPlayer, angleDiff, runMain,
	errorLines, serverDirectory: () => serverDir,
};
