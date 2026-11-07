'use strict';
const lib = require('./lib');
const { parseArgs, check, connectBot, sleep, say, setConfig, reloadConfig, runMain, errorLines } = lib;

const { reportFile } = parseArgs('duels.js');

const DUELS = { maxArenas: 2, timeLimitSeconds: 120, countdownSeconds: 4, challengeSeconds: 3, queueRange: 100, queueRangePerSecond: 10 };
const config = (duels = {}) => setConfig({ duels: { ...DUELS, ...duels } });
config();

const FLOOR = 64;

async function step(name, bot, line, re, timeoutMs) {
	await sleep(1000);
	const r = await say(bot, line, re, timeoutMs);
	check(name, r.ok, r.ok ? '' : `${bot.username}: ${line} -> ${JSON.stringify(r.all)}`);
	return r;
}

async function itemCount(srv, name, item) {
	const line = await srv.command(`clear ${name} ${item} 0`, /Found \d+ matching|No items were found/);
	const m = line.match(/Found (\d+) matching/);
	return m ? Number(m[1]) : 0;
}

async function number(srv, name, path) {
	const line = await srv.command(`data get entity ${name} ${path}`, new RegExp(`${name} has the following entity data: ([\\-0-9.]+)`));
	return Number(line.match(/entity data: ([\-0-9.]+)/)[1]);
}

async function dimensionOf(srv, name) {
	const line = await srv.command(`data get entity ${name} Dimension`, new RegExp(`${name} has the following entity data`));
	return line.split('entity data: ')[1].replace(/"/g, '').trim();
}

async function waitDimension(srv, name, dimension, timeoutMs = 6000) {
	const deadline = Date.now() + timeoutMs;
	let current = null;
	while (Date.now() < deadline) {
		current = await dimensionOf(srv, name);
		if (current === dimension) return true;
		await sleep(250);
	}
	console.log(`   (${name} jest w ${current}, a nie w ${dimension})`);
	return false;
}

async function prepareGear(srv, bot) {
	await srv.command(`clear ${bot.username}`, /Removed|No items/);
	await srv.command(`give ${bot.username} minecraft:cobblestone 7`, /Gave/);
	await srv.command(`give ${bot.username} minecraft:diamond_sword[minecraft:custom_name="Skarb"] 1`, /Gave/);
	await srv.command(`experience set ${bot.username} 5 levels`, /Set .* experience/);
	await srv.command(`effect give ${bot.username} minecraft:speed 300 1 true`, /Applied effect/);
	await srv.command(`tp ${bot.username} 3.5 -60 3.5`, /Teleported/);
	await sleep(400);
}

async function startDuel(srv, from, to, args = '') {
	await sleep(1000);
	from.chat(`/duel ${to.username}${args}`);
	await lib.waitMsg(to, new RegExp(`${from.username} wyzywa Cię`), 4000, to.log.length - 1);
	await sleep(500);
	const mark = from.log.length;
	to.chat(`/duel akceptuj ${from.username}`);
	return { mark };
}

async function fight(srv, bot, from) {
	return lib.waitMsg(bot, /Walka!/, 10000, from);
}

async function kill(srv, victim, killer) {
	return srv.command(`damage ${victim} 100 minecraft:player_attack by ${killer}`, /damage to|invulnerable|Unable/);
}

runMain(async (srv) => {
	await srv.waitFor(/Done \(/, 180000);
	const ala = await connectBot('AlaDuel');
	const bob = await connectBot('BobDuel');
	const cez = await connectBot('CezDuel');

	await step('lista zestawow', ala, '/kity', /Zestawy: standard, siekiera, sumo/);
	await step('podglad dywizji', ala, '/dywizje', /Dywizje ELO/);
	await step('elo startowe', ala, '/elo', /Pojedynki gracza AlaDuel: ELO 1000 \(Srebro III\), wygrane 0, przegrane 0/);
	await step('wyzwanie samego siebie', ala, '/duel AlaDuel', /Nie można wyzwać samego siebie/);
	await step('nieznany zestaw', ala, '/duel BobDuel xyz', /Nie ma zestawu xyz/);
	await step('poddanie poza walka', ala, '/ff', /Nie jesteś w pojedynku/);
	await step('akceptacja bez wyzwania', ala, '/duel akceptuj BobDuel', /Brak wyzwania od gracza BobDuel/);

	await sleep(1000);
	let opened = new Promise((resolve) => ala.once('windowOpen', resolve));
	ala.chat('/previewkit standard');
	let window = await opened;
	await sleep(300);
	check('podglad zestawu', window.slots[0] && window.slots[0].name === 'netherite_helmet' && window.slots[9] && window.slots[9].name === 'netherite_sword');
	ala.closeWindow(window);

	await step('wylaczenie wyzwan', bob, '/duel toggle', /Nie przyjmujesz teraz wyzwań/);
	await step('wyzwanie odrzucone przez toggle', ala, '/duel BobDuel', /BobDuel nie przyjmuje teraz wyzwań/);
	await step('wlaczenie wyzwan', bob, '/duel toggle', /Znowu przyjmujesz wyzwania/);
	await step('wyzwanie', ala, '/duel BobDuel', /Wyzwanie wysłane do BobDuel \(zestaw: standard, do 1 wygranych, ważne 3 s\)/);
	check('wyzwany dostaje przyciski', bob.raw.some((m) => m.includes('duel akceptuj AlaDuel')) || !!(await lib.waitMsg(bob, /AlaDuel wyzywa Cię/)));
	await step('odrzucenie', bob, '/duel odrzuc AlaDuel', /Odrzucono wyzwanie od AlaDuel/);
	check('wyzywajacy wie o odrzuceniu', !!(await lib.waitMsg(ala, /BobDuel odrzuca wyzwanie/)));
	await step('wyzwanie 2', ala, '/duel BobDuel', /Wyzwanie wysłane/);
	check('wyzwanie wygasa', !!(await lib.waitMsg(ala, /Wyzwanie do BobDuel wygasło/, 6000, ala.log.length - 1)));

	await prepareGear(srv, ala);
	await prepareGear(srv, bob);
	await srv.command('serca ustaw AlaDuel 12', /AlaDuel: 12 /);
	const posBefore = await number(srv, 'AlaDuel', 'Pos[0]');
	const mark = ala.log.length;
	const bobMark = bob.log.length;
	await startDuel(srv, ala, bob);
	check('start: komunikat', !!(await lib.waitMsg(ala, /Pojedynek z BobDuel, zestaw standard/, 4000, mark)));
	check('start: swiat aren', await waitDimension(srv, 'AlaDuel', 'easytp:duels'));
	check('start: wrog w swiecie aren', await waitDimension(srv, 'BobDuel', 'easytp:duels'));
	check('zestaw: miecz', await itemCount(srv, 'AlaDuel', 'minecraft:netherite_sword') === 1);
	check('zestaw: zbroja', await itemCount(srv, 'AlaDuel', 'minecraft:netherite_chestplate') === 1);
	check('zestaw: bez wlasnych rzeczy', await itemCount(srv, 'AlaDuel', 'minecraft:cobblestone') === 0);
	const maxHealth = await srv.command('attribute AlaDuel minecraft:max_health get', /AlaDuel is ([0-9.]+)/);
	check('pojedynek: rowne 20 HP mimo serc', /AlaDuel is 20/.test(maxHealth), maxHealth);
	const blocked = await kill(srv, 'BobDuel', 'AlaDuel');
	check('odliczanie: bez obrazen', /invulnerable|Unable/.test(blocked) || await number(srv, 'BobDuel', 'Health') === 20, blocked);
	check('walka startuje', !!(await fight(srv, ala, mark)));

	await step('w walce /spawn zablokowany', ala, '/spawn', /Podczas pojedynku ta komenda nie działa/);
	await step('w walce /sprzedaj zablokowany', ala, '/sprzedaj', /Podczas pojedynku ta komenda nie działa/);
	await step('w walce /rynek zablokowany', ala, '/rynek wystaw 5', /Podczas pojedynku ta komenda nie działa/);
	await step('w walce /wyplacserce zablokowany', ala, '/wyplacserce 1', /Podczas pojedynku ta komenda nie działa/);
	await step('wyzwanie gracza w walce', cez, '/duel AlaDuel', /AlaDuel jest teraz w pojedynku/);

	await kill(srv, 'BobDuel', 'AlaDuel');
	check('smierc konczy walke', !!(await lib.waitMsg(ala, /Pojedynek wygrywa AlaDuel/, 4000, mark)));
	check('przegrany wie', !!(await lib.waitMsg(bob, /Pojedynek wygrywa AlaDuel/, 4000, bobMark)));
	check('powrot do swiata', await waitDimension(srv, 'AlaDuel', 'minecraft:overworld'));
	check('powrot przegranego', await waitDimension(srv, 'BobDuel', 'minecraft:overworld'));
	await sleep(300);
	check('przegrany nie zginal naprawde', !bob.log.slice(bobMark).some((m) => /was slain/.test(m)));
	check('zwrot: bruk', await itemCount(srv, 'BobDuel', 'minecraft:cobblestone') === 7);
	check('zwrot: wlasny miecz', await itemCount(srv, 'BobDuel', 'minecraft:diamond_sword') === 1);
	check('zwrot: bez zestawu', await itemCount(srv, 'BobDuel', 'minecraft:netherite_sword') === 0);
	check('zwrot: doswiadczenie', await number(srv, 'BobDuel', 'XpLevel') === 5);
	check('zwrot: efekt', await srv.command('data get entity BobDuel active_effects[0].id', /speed|Found no elements/).then((l) => /speed/.test(l)));
	check('zwrot: pozycja', Math.abs(await number(srv, 'AlaDuel', 'Pos[0]') - posBefore) < 0.5);
	const healthAfter = await srv.command('attribute AlaDuel minecraft:max_health get', /AlaDuel is ([0-9.]+)/);
	check('zwrot: serca', /AlaDuel is 24/.test(healthAfter), healthAfter);
	await step('wyzwanie bez ELO', ala, '/elo', /ELO 1000 \(Srebro III\), wygrane 1, przegrane 0/);
	await step('komendy po walce dzialaja', ala, '/serca', /Serca: 12 /);
	const sword = await srv.command('data get entity BobDuel Inventory', /BobDuel has the following entity data/);
	check('zwrot: nazwa przedmiotu', /Skarb/.test(sword), sword);

	let m1 = ala.log.length;
	await startDuel(srv, ala, bob);
	await fight(srv, ala, m1);
	await step('poddanie', bob, '/ff', /BobDuel poddaje się/);
	check('poddanie: wygrana', !!(await lib.waitMsg(ala, /Pojedynek wygrywa AlaDuel/, 3000, m1)));
	check('poddanie: powrot', await waitDimension(srv, 'BobDuel', 'minecraft:overworld'));
	await step('zapis wygranych', bob, '/elo', /wygrane 0, przegrane 2/);

	m1 = ala.log.length;
	await startDuel(srv, ala, bob, ' standard 3');
	await fight(srv, ala, m1);
	await sleep(300);
	await kill(srv, 'BobDuel', 'AlaDuel');
	check('do 2 wygranych: runda', !!(await lib.waitMsg(ala, /Rundę wygrywa AlaDuel \(1:0\)/, 4000, m1)));
	const round = ala.log.length;
	check('do 2 wygranych: nowa runda', !!(await lib.waitMsg(ala, /Walka!/, 12000, round)));
	check('nowa runda: dalej w arenie', await waitDimension(srv, 'BobDuel', 'easytp:duels'));
	await sleep(300);
	await kill(srv, 'AlaDuel', 'BobDuel');
	check('do 2 wygranych: wyrownanie', !!(await lib.waitMsg(ala, /Rundę wygrywa BobDuel \(1:1\)/, 4000, round)));
	const round2 = ala.log.length;
	await lib.waitMsg(ala, /Walka!/, 12000, round2);
	await sleep(300);
	await kill(srv, 'BobDuel', 'AlaDuel');
	check('do 2 wygranych: koniec', !!(await lib.waitMsg(ala, /Pojedynek wygrywa AlaDuel/, 12000, round2)));
	check('do 2 wygranych: powrot', await waitDimension(srv, 'AlaDuel', 'minecraft:overworld'));

	m1 = ala.log.length;
	await startDuel(srv, ala, bob, ' sumo');
	await waitDimension(srv, 'AlaDuel', 'easytp:duels');
	await fight(srv, ala, m1);
	await sleep(400);
	const resist = await kill(srv, 'BobDuel', 'AlaDuel');
	await sleep(400);
	check('sumo: ciosy nie rania', await number(srv, 'BobDuel', 'Health') === 20, resist);
	check('sumo: pusty ekwipunek', await itemCount(srv, 'AlaDuel', 'minecraft:netherite_sword') === 0);
	await srv.command(`execute in easytp:duels run tp BobDuel 512.5 ${FLOOR - 5} 512.5`, /Teleported/);
	check('sumo: spadniecie to przegrana', !!(await lib.waitMsg(ala, /Pojedynek wygrywa AlaDuel/, 5000, m1)));
	check('sumo: powrot', await waitDimension(srv, 'BobDuel', 'minecraft:overworld'));

	m1 = ala.log.length;
	await startDuel(srv, ala, bob, ' totemy');
	await fight(srv, ala, m1);
	await sleep(400);
	check('totem: w zestawie', await itemCount(srv, 'BobDuel', 'minecraft:totem_of_undying') === 3);
	await kill(srv, 'BobDuel', 'AlaDuel');
	await sleep(500);
	check('totem ratuje: walka trwa', !ala.log.slice(m1).some((m) => /Pojedynek wygrywa/.test(m)));
	check('totem: zuzyty', await itemCount(srv, 'BobDuel', 'minecraft:totem_of_undying') === 2);
	await step('totem: poddanie', ala, '/poddaj', /AlaDuel poddaje się/);
	check('totem: powrot', await waitDimension(srv, 'BobDuel', 'minecraft:overworld'));

	m1 = ala.log.length;
	await startDuel(srv, ala, bob, ' uhc');
	await fight(srv, ala, m1);
	await sleep(500);
	const center = await number(srv, 'AlaDuel', 'Pos[0]');
	check('uhc: arena', Math.abs(center - 498) < 2, String(center));
	await srv.command('clear AlaDuel minecraft:cobblestone', /Removed/);
	await srv.command('give AlaDuel minecraft:cobblestone 8', /Gave/);
	await sleep(400);
	ala.setQuickBarSlot(0);
	const floorBlock = ala.blockAt(ala.entity.position.floored().offset(0, -1, 0));
	check('uhc: podloga z bedrocka', floorBlock && floorBlock.name === 'bedrock', floorBlock && floorBlock.name);
	await step('uhc: poddanie', ala, '/ff', /AlaDuel poddaje się/);
	await waitDimension(srv, 'AlaDuel', 'minecraft:overworld');

	await step('kolejka: zly zestaw', ala, '/ranked xyz', /Nie ma zestawu xyz/);
	await step('kolejka: wejscie', ala, '/ranked sumo', /Kolejka rankingowa, zestaw sumo/);
	await step('kolejka: podwojne wejscie', ala, '/queue sumo', /Już czekasz w kolejce/);
	await step('kolejka: wyjscie', ala, '/queue leave', /Wyjście z kolejki/);
	await step('kolejka: wyjscie bez kolejki', ala, '/queue leave', /Nie czekasz w kolejce/);
	await step('kolejka: Ala', ala, '/ranked', /Kolejka rankingowa, zestaw standard/);
	m1 = ala.log.length;
	const mb = bob.log.length;
	await step('kolejka: Bob', bob, '/ranked', /Kolejka rankingowa, zestaw standard/);
	check('kolejka: dobrany przeciwnik', !!(await lib.waitMsg(ala, /Pojedynek z BobDuel, zestaw standard/, 5000, m1)));
	await fight(srv, ala, m1);
	await sleep(300);
	await kill(srv, 'BobDuel', 'AlaDuel');
	check('ranking: zmiana ELO zwyciezcy', !!(await lib.waitMsg(ala, /ELO: \d+ -> \d+/, 5000, m1)));
	const eloLine = await lib.waitMsg(bob, /ELO: \d+ -> \d+/, 5000, mb);
	const [, oldB, newB] = eloLine.match(/ELO: (\d+) -> (\d+)/);
	check('ranking: przegrany traci', Number(newB) < Number(oldB), eloLine);
	await waitDimension(srv, 'AlaDuel', 'minecraft:overworld');
	await step('topka elo', ala, '/topki elo', /1\. AlaDuel: \d+/);

	await prepareGear(srv, bob);
	m1 = ala.log.length;
	await startDuel(srv, ala, bob);
	await fight(srv, ala, m1);
	check('wyjscie: w arenie', await waitDimension(srv, 'BobDuel', 'easytp:duels'));
	bob.quit();
	check('wyjscie z gry = przegrana', !!(await lib.waitMsg(ala, /BobDuel wychodzi z serwera i przegrywa/, 5000, m1)));
	check('wyjscie: wygrany wraca', await waitDimension(srv, 'AlaDuel', 'minecraft:overworld'));
	await sleep(1000);
	const bob2 = await connectBot('BobDuel');
	check('powrot do gry: ekwipunek', !!(await lib.waitMsg(bob2, /Przywrócono ekwipunek sprzed pojedynku/, 4000, 0)));
	check('powrot do gry: swiat', await waitDimension(srv, 'BobDuel', 'minecraft:overworld'));
	check('powrot do gry: bruk', await itemCount(srv, 'BobDuel', 'minecraft:cobblestone') === 7);
	check('powrot do gry: bez zestawu', await itemCount(srv, 'BobDuel', 'minecraft:netherite_sword') === 0);

	await prepareGear(srv, ala);
	m1 = ala.log.length;
	await startDuel(srv, ala, bob2);
	await fight(srv, ala, m1);
	check('awaria: w arenie', await waitDimension(srv, 'AlaDuel', 'easytp:duels'));
	srv.proc.kill('SIGKILL');
	await sleep(1500);
	ala.quit();
	bob2.quit();
	cez.quit();
	srv.start();
	await srv.waitFor(/Done \(/, 180000);
	const ala2 = await connectBot('AlaDuel');
	check('po awarii: ekwipunek wraca', !!(await lib.waitMsg(ala2, /Przywrócono ekwipunek sprzed pojedynku/, 4000, 0)));
	check('po awarii: swiat', await waitDimension(srv, 'AlaDuel', 'minecraft:overworld'));
	check('po awarii: bruk', await itemCount(srv, 'AlaDuel', 'minecraft:cobblestone') === 7);
	check('po awarii: bez zestawu', await itemCount(srv, 'AlaDuel', 'minecraft:netherite_sword') === 0);
	await step('po awarii: wyniki zostaja', ala2, '/elo', /Pojedynki gracza AlaDuel: ELO \d+/);
	const bob3 = await connectBot('BobDuel');

	config({ maxArenas: 1 });
	await reloadConfig(srv);
	const cez2 = await connectBot('CezDuel');
	const dan = await connectBot('DanDuel');
	m1 = ala2.log.length;
	await startDuel(srv, ala2, bob3);
	await fight(srv, ala2, m1);
	await sleep(1000);
	cez2.chat('/duel DanDuel');
	await lib.waitMsg(dan, /CezDuel wyzywa Cię/);
	await sleep(1000);
	const noArena = await say(dan, '/duel akceptuj CezDuel', /Nie ma wolnych aren/);
	check('brak wolnych aren', noArena.ok, JSON.stringify(noArena.all));
	await step('koniec walki', ala2, '/ff', /AlaDuel poddaje się/);

	config({ enabled: false });
	await reloadConfig(srv);
	await step('wylaczone duele', dan, '/duel CezDuel', /Unknown or incomplete command/);
	for (const bot of [ala2, bob3, cez2, dan]) bot.quit();
	await sleep(800);

	const errors = errorLines(srv);
	check('brak bledow w konsoli', errors.length === 0, errors.slice(0, 8).join('\n'));
}, reportFile);
