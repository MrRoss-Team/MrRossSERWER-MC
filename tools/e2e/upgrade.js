'use strict';
const fs = require('node:fs');
const path = require('node:path');
const lib = require('./lib');
const { parseArgs, check, connectBot, sleep, say, runMain, errorLines, serverDirectory } = lib;

const { reportFile } = parseArgs('upgrade.js');
const dimensions = ['easytp:duels', 'easytp:oneblock', 'easytp:boxpvp'];

async function step(name, bot, line, re) {
	await sleep(1000);
	const r = await say(bot, line, re);
	check(name, r.ok, r.ok ? '' : `${bot.username}: ${line} -> ${JSON.stringify(r.all)}`);
	return r;
}

runMain(async (srv) => {
	const dir = serverDirectory();
	const dataBefore = fs.readFileSync(path.join(dir, 'world', 'easytp', 'data.json'), 'utf8');
	await srv.waitFor(/Done \(/, 240000);
	const config = JSON.parse(fs.readFileSync(path.join(dir, 'config', 'easytp.json'), 'utf8'));
	check('stara konfiguracja dostaje nowe sekcje', !!config.lifesteal && !!config.economy && !!config.clans && !!config.duels && !!config.oneblock && !!config.boxpvp);
	for (const dimension of dimensions) {
		const line = await srv.command(`execute in ${dimension} run data get block 0 0 0`, /not a block entity|Unknown dimension|not loaded|is not|Invalid/);
		check(`wymiar ${dimension} istnieje w starym swiecie`, !/Unknown dimension|Invalid/i.test(line), line);
	}
	const ala = await connectBot('AlaStary');
	const bob = await connectBot('BobStary');
	await step('serca na starym swiecie', ala, '/serca', /Serca: 10 /);
	await step('spawn na starym swiecie', ala, '/spawn', /./);
	await sleep(800);
	const mark = ala.log.length;
	await sleep(1000);
	ala.chat('/duel BobStary');
	await lib.waitMsg(bob, /AlaStary wyzywa Cię/);
	await sleep(500);
	bob.chat('/duel akceptuj AlaStary');
	check('pojedynek na starym swiecie', !!(await lib.waitMsg(ala, /Walka!/, 8000, mark)));
	await sleep(500);
	const dim = await srv.command('data get entity AlaStary Dimension', /AlaStary has the following entity data/);
	check('gracz w swiecie aren', /easytp:duels/.test(dim), dim);
	await step('poddanie', ala, '/ff', /AlaStary poddaje się/);
	await sleep(800);
	const back = await srv.command('data get entity AlaStary Dimension', /AlaStary has the following entity data/);
	check('powrot ze swiata aren', /minecraft:overworld/.test(back), back);
	await sleep(lib.COOLDOWN_WAIT);
	await step('wyspa na starym swiecie', ala, '/is stworz', /Wyspa założona/);
	await sleep(600);
	const island = await srv.command('data get entity AlaStary Dimension', /AlaStary has the following entity data/);
	check('gracz w swiecie wysp', /easytp:oneblock/.test(island), island);
	await sleep(lib.COOLDOWN_WAIT);
	await step('BoxPvP na starym swiecie', ala, '/box', /Witaj w BoxPvP/);
	await sleep(600);
	const boxDim = await srv.command('data get entity AlaStary Dimension', /AlaStary has the following entity data/);
	check('gracz w swiecie BoxPvP', /easytp:boxpvp/.test(boxDim), boxDim);
	await step('zestaw na starym swiecie', ala, '/kit', /Zestaw startowy odebrany/);
	await sleep(lib.COOLDOWN_WAIT);
	await step('powrot na spawn', ala, '/spawn', /./);
	ala.quit();
	bob.quit();
	await sleep(1000);
	await srv.stop();
	const dataAfter = JSON.parse(fs.readFileSync(path.join(dir, 'world', 'easytp', 'data.json'), 'utf8'));
	const parsedBefore = JSON.parse(dataBefore);
	check('domy i spawn bez zmian', JSON.stringify(dataAfter.homes || {}) === JSON.stringify(parsedBefore.homes || {}) && JSON.stringify(dataAfter.spawn || null) === JSON.stringify(parsedBefore.spawn || null));
	check('dane graczy powstaly', fs.existsSync(path.join(dir, 'world', 'easytp', 'players.json')));
	for (const name of ['duels', 'oneblock', 'boxpvp']) {
		check(`mapa swiata ${name} na dysku`, fs.existsSync(path.join(dir, 'world', 'dimensions', 'easytp', name)));
	}
	const errors = errorLines(srv);
	check('brak bledow w konsoli', errors.length === 0, errors.slice(0, 8).join('\n'));
}, reportFile);
