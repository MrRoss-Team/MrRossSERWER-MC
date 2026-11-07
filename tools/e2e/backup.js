'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const lib = require('./lib');

const { reportFile } = lib.parseArgs('backup.js');
const serverDir = lib.serverDirectory();
const { sleep, check, connectBot, say, expectSay, waitMsg, setConfig, reloadConfig, opPlayer, errorLines } = lib;

const backupsDir = path.join(serverDir, 'backups');
const worldDir = path.join(serverDir, 'world');
const ZIP_NAME = /^world-\d{8}-\d{6}(_\d+)?\.zip$/;

const zips = (dir = backupsDir) => (fs.existsSync(dir) ? fs.readdirSync(dir).filter((n) => ZIP_NAME.test(n)).sort().reverse() : []);
const tmpFiles = () => (fs.existsSync(backupsDir) ? fs.readdirSync(backupsDir).filter((n) => n.endsWith('.tmp')) : []);
const zipNames = (zip) => execFileSync('tar', ['-tf', zip], { encoding: 'utf8' }).split(/\r?\n/).filter(Boolean);
const zipBytes = (zip, name) => execFileSync('tar', ['-xOf', zip, name], { maxBuffer: 256 * 1024 * 1024 });
const regionFiles = () => {
	const out = [];
	const walk = (dir) => {
		for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
			const p = path.join(dir, e.name);
			if (e.isDirectory()) walk(p);
			else if (e.name.endsWith('.mca')) out.push(p);
		}
	};
	walk(worldDir);
	return out;
};

async function waitZips(count, timeoutMs) {
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		if (zips().length >= count) return true;
		await sleep(1000);
	}
	return zips().length >= count;
}

async function collect(bot, command, headerRe, settleMs = 600) {
	const from = bot.log.length;
	bot.chat(command);
	const header = await waitMsg(bot, headerRe, 4000, from);
	await sleep(settleMs);
	return { ok: !!header, all: bot.log.slice(from) };
}

async function consoleBackup(srv) {
	try {
		const line = await srv.command('easytp backup', /Kopia zapasowa gotowa: (world-\S+\.zip)/, 30000);
		return line.match(/(world-\S+\.zip)/)[1];
	} catch (e) {
		console.log('   (konsolowa kopia nie powiodla sie: ' + e.message + ')');
		return null;
	}
}

async function scenario(srv) {
	await srv.waitFor(/Done \(.*\)! For help, type "help"/, 300000);
	check('serwer wystartowal, mod zaladowany z kopiami zapasowymi', srv.lines.some((l) => /EasyTP gotowy.*kopie zapasowe/.test(l)));

	const admin = await connectBot('Admin');
	const alice = await connectBot('Alice');
	await opPlayer(srv, 'Admin');

	{
		const r = await say(alice, '/easytp backup', /Unknown or incomplete command|Incorrect argument for command|command\.unknown/i);
		check('zwykly gracz nie ma dostepu do /easytp backup', r.ok, JSON.stringify(r.all));
	}
	await sleep(700);
	{
		const r = await say(alice, '/easytp', /EasyTP \d+\.\d+\.\d+/);
		check('zwykly gracz nie widzi w /easytp linii o kopiach', r.ok && !r.all.some((m) => /Kopie zapasowe \(OP\)/.test(m)), JSON.stringify(r.all));
	}
	await expectSay('/easytp pokazuje operatorowi komendy kopii zapasowych', admin, '/easytp', /Kopie zapasowe \(OP\)/);
	await sleep(700);

	await expectSay('Alice ustawia dom (dane moda trafiaja do swiata)', alice, '/sethome baza', /Ustawiono dom 'baza'/);
	check('na starcie nie ma jeszcze zadnej kopii', zips().length === 0, JSON.stringify(zips()));
	admin.chat('/easytp backup');
	const startMsg = await waitMsg(admin, /Trwa tworzenie kopii zapasowej świata/, 5000);
	const doneMsg = await waitMsg(admin, /Kopia zapasowa gotowa: (world-\d{8}-\d{6}(?:_\d+)?\.zip) \(/, 30000);
	check('/easytp backup: komunikat o rozpoczeciu', !!startMsg);
	check('/easytp backup: komunikat o zakonczeniu z nazwa pliku', !!doneMsg, JSON.stringify(admin.log.slice(-6)));
	await sleep(500);
	const first = zips()[0];
	check('powstal dokladnie jeden plik kopii', zips().length === 1 && !!first, JSON.stringify(zips()));
	const zipPath = path.join(backupsDir, first || 'brak.zip');
	let names = [];
	try {
		names = zipNames(zipPath);
	} catch (e) {
		check('ZIP da sie odczytac', false, e.message);
	}
	check('ZIP ma level.dat swiata w folderze world/', names.includes('world/level.dat'), names.slice(0, 8).join(', '));
	check('ZIP nie zawiera session.lock', names.length > 0 && !names.some((n) => n.endsWith('session.lock')));
	check('ZIP zawiera dane moda (domy graczy)', names.includes('world/easytp/data.json') && /baza/.test(zipBytes(zipPath, 'world/easytp/data.json').toString('utf8')));
	check('brak plikow tymczasowych .tmp', tmpFiles().length === 0, JSON.stringify(tmpFiles()));
	{
		const bad = [];
		for (const file of regionFiles()) {
			const rel = 'world/' + path.relative(worldDir, file).split(path.sep).join('/');
			if (!names.includes(rel)) { bad.push(rel + ' (brak w ZIP)'); continue; }
			const inZip = zipBytes(zipPath, rel).length;
			const onDisk = fs.statSync(file).size;
			if (inZip !== onDisk) bad.push(`${rel}: ZIP ${inZip} B, dysk ${onDisk} B`);
		}
		check('wszystkie pliki regionow sa w ZIP i maja rozmiar z dysku', regionFiles().length > 0 && bad.length === 0, bad.join('; ') || 'brak plikow regionow');
	}

	{
		const line = await srv.command('save-on', /already turned on|Automatic saving is now enabled/i, 8000);
		check('po kopii automatyczny zapis swiata jest nadal wlaczony', /already turned on/i.test(line), line);
	}
	await srv.command('save-off', /Automatic saving is now disabled/i, 8000);
	{
		const name = await consoleBackup(srv);
		check('kopia z konsoli serwera dziala', !!name);
		const line = await srv.command('save-on', /already turned on|Automatic saving is now enabled/i, 8000);
		check('kopia nie wlacza zapisu, ktory administrator wylaczyl recznie', /Automatic saving is now enabled/i.test(line), line);
	}

	{
		const r = await collect(admin, '/easytp backup list', /Kopie zapasowe w folderze .*backups \(2\):/);
		const text = r.all.join(' | ');
		check('/easytp backup list pokazuje naglowek z liczba kopii', r.ok, text);
		check('/easytp backup list pokazuje pliki ze rozmiarem i wiekiem', zips().every((z) => text.includes(z)) && /\(\d+(\.\d+)? (B|KB|MB), .* temu\)/.test(text), text);
	}

	setConfig({ backupKeep: 2, backupIntervalMinutes: 1440 });
	await reloadConfig(srv);
	const created = [];
	for (let i = 0; i < 3; i++) {
		await sleep(1100);
		created.push(await consoleBackup(srv));
	}
	{
		const left = zips();
		check('rotacja: zostaly 2 kopie', left.length === 2, JSON.stringify(left));
		check('rotacja: zostaly dwie najnowsze', JSON.stringify([...left].sort()) === JSON.stringify(created.slice(-2).sort()), `zostalo ${JSON.stringify(left)}, powstaly ${JSON.stringify(created)}`);
	}

	setConfig({ backupKeep: 10, backupIntervalMinutes: 1440, backupDir: 'world/kopie' });
	await reloadConfig(srv);
	await expectSay('folder kopii wewnatrz swiata jest odrzucony', admin, '/easytp backup', /Folder kopii zapasowych \(backupDir\) nie może leżeć wewnątrz świata/, 6000);
	check('zly folder: nic nie powstalo w swiecie', !fs.existsSync(path.join(worldDir, 'kopie')));

	setConfig({ backupEnabled: false, backupIntervalMinutes: 1, backupKeep: 10 });
	await reloadConfig(srv);
	const countBefore = zips().length;
	await srv.command('setblock 3 80 3 minecraft:gold_block', /Changed the block/i, 8000);
	await srv.command('save-all flush', /Saved the game/i, 20000);
	console.log('   (czekam 130 s: kopie automatyczne sa wylaczone)');
	await sleep(130000);
	check('backupEnabled=false: zegar nie robi kopii mimo zmiany swiata', zips().length === countBefore, `bylo ${countBefore}, jest ${zips().length}`);

	setConfig({ backupEnabled: true, backupIntervalMinutes: 1, backupKeep: 10 });
	await reloadConfig(srv);
	admin.quit();
	alice.quit();
	await srv.waitFor(/Server empty for \d+ seconds, pausing/, 120000);
	check('serwer bez graczy wstrzymal tyki (pausing)', true);
	console.log('   (czekam do 150 s na kopie automatyczna na wstrzymanym serwerze)');
	const gotAuto = await waitZips(countBefore + 1, 150000);
	check('kopia automatyczna powstaje na pustym, wstrzymanym serwerze', gotAuto, `bylo ${countBefore}, jest ${zips().length}`);
	{
		const newest = zips()[0];
		const n = newest ? zipNames(path.join(backupsDir, newest)) : [];
		check('kopia automatyczna jest kompletnym ZIP-em ze swiatem', n.includes('world/level.dat') && !n.some((x) => x.endsWith('session.lock')), n.slice(0, 6).join(', '));
	}

	const countAfterAuto = zips().length;
	console.log('   (czekam 130 s: swiat bez zmian, kopii nie powinno byc)');
	await sleep(130000);
	check('swiat bez zmian: zegar pomija kopie', zips().length === countAfterAuto, `bylo ${countAfterAuto}, jest ${zips().length}`);
	check('brak plikow tymczasowych .tmp na koncu', tmpFiles().length === 0, JSON.stringify(tmpFiles()));

	const errors = errorLines(srv).filter((l) => !/EasyTP: folder kopii zapasowych /.test(l));
	check('w logu serwera nie ma bledow', errors.length === 0, errors.slice(0, 5).join(' | '));
}

lib.runMain(scenario, reportFile);
