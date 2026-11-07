'use strict';
const lib = require('./lib');
const { parseArgs, check, connectBot, sleep, say, setConfig, reloadConfig, runMain, errorLines } = lib;

const { reportFile } = parseArgs('oneblock.js');

const config = (oneblock = {}) => setConfig({ oneblock: { inviteSeconds: 3, levelRewardPerLevel: 100, ...oneblock } });
config();

const CENTER = 100;

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

async function waitAt(bot, x, y, z, tol = 1.5, timeoutMs = 6000) {
	const deadline = Date.now() + timeoutMs;
	while (Date.now() < deadline) {
		const p = bot.entity.position;
		if (Math.abs(p.x - x) <= tol && Math.abs(p.y - y) <= tol && Math.abs(p.z - z) <= tol) return true;
		await sleep(100);
	}
	console.log(`   (${bot.username} na ${lib.fmt(bot)})`);
	return false;
}

async function blockAtServer(srv, dimension, x, y, z, block) {
	const line = await srv.command(`execute in ${dimension} if block ${x} ${y} ${z} ${block}`, /Test passed|Test failed/);
	return /passed/.test(line);
}

runMain(async (srv) => {
	await srv.waitFor(/Done \(/, 180000);
	const ala = await connectBot('AlaWyspa');
	const bob = await connectBot('BobWyspa');

	await srv.command('give AlaWyspa minecraft:cobblestone 11', /Gave/);
	await srv.command('experience set AlaWyspa 7 levels', /Set .* experience/);
	await srv.command('serca ustaw AlaWyspa 12', /AlaWyspa: 12 /);
	const startPos = ala.entity.position.clone();

	await step('/is bez wyspy podpowiada', ala, '/is', /Nie masz wyspy\. Załóż ją: \/is stworz/);
	await step('/is info bez wyspy', ala, '/is info', /Nie masz wyspy/);
	await step('/is zapros bez wyspy', ala, '/is zapros BobWyspa', /Nie masz wyspy/);
	await step('/motyw bez wyspy', ala, '/motyw', /Nie masz wyspy/);

	await step('zalozenie wyspy', ala, '/is stworz', /Wyspa założona!/);
	check('swiat wysp', await waitDimension(srv, 'AlaWyspa', 'easytp:oneblock'));
	check('stoi na bloku', await waitAt(ala, 0.5, CENTER + 1, 0.5));
	check('osobny ekwipunek: bez bruku', await itemCount(srv, 'AlaWyspa', 'minecraft:cobblestone') === 0);
	check('blok startowy to skrzynia', await blockAtServer(srv, 'easytp:oneblock', 0, CENTER, 0, 'minecraft:chest'));
	check('pod spodem bedrock', await blockAtServer(srv, 'easytp:oneblock', 0, CENTER - 1, 0, 'minecraft:bedrock'));
	const hp = await srv.command('attribute AlaWyspa minecraft:max_health get', /AlaWyspa is ([0-9.]+)/);
	check('na wyspie zwykle 20 HP', /AlaWyspa is 20/.test(hp), hp);
	await step('druga wyspa', ala, '/is stworz', /Masz już wyspę/);
	await step('blokada komend serc', ala, '/wyplacserce 1', /Ta komenda nie działa w tym trybie gry/);
	await step('info wyspy', ala, '/is info', /Wyspa gracza AlaWyspa: poziom 0 \(0\/10 do następnego\), motyw Polana/);
	check('info: czlonkowie', !!(await lib.waitMsg(ala, /Członkowie \(1\/5\): AlaWyspa/)));
	check('info: rozmiar', !!(await lib.waitMsg(ala, /Rozmiar: 50x50, skarbiec: 0 \$, rozbite bloki: 0/)));

	await sleep(800);
	await srv.command('give AlaWyspa minecraft:netherite_pickaxe 1', /Gave/);
	ala.setQuickBarSlot(0);
	await sleep(500);

	const center = () => ala.blockAt(ala.vec3 ? ala.vec3(0, CENTER, 0) : new (ala.entity.position.constructor)(0, CENTER, 0));
	let broken = 0;
	const fromLevel = ala.log.length;
	for (let i = 0; i < 12; i++) {
		await srv.command(`execute in easytp:oneblock run tp AlaWyspa 0.5 ${CENTER + 1} 0.5`, /Teleported/);
		await sleep(150);
		const block = center();
		if (!block || block.name === 'air') {
			await sleep(300);
			continue;
		}
		try {
			await ala.dig(center(), 'ignore');
			broken++;
		} catch (e) {
			console.log(`   (kopanie: ${e.message})`);
		}
		await sleep(150);
	}
	check('kopanie bloku', broken >= 10, `rozbite: ${broken}`);
	check('blok odradza sie', !(await blockAtServer(srv, 'easytp:oneblock', 0, CENTER, 0, 'minecraft:air')));
	check('awans na poziom 1', !!(await lib.waitMsg(ala, /Poziom wyspy: 1! Skarbiec \+100 \$/, 4000, fromLevel)));
	check('skrzynia oddala przedmioty', await itemCount(srv, 'AlaWyspa', 'minecraft:bread') + await itemCount(srv, 'AlaWyspa', 'minecraft:oak_log') > 0);
	await step('skarbiec po awansie', ala, '/is skarbiec', /Skarbiec wyspy: 100 \$/);
	await step('info po awansie', ala, '/is info', /poziom 1 /);

	await srv.command('execute in easytp:oneblock run setblock 0 100 0 minecraft:air', /Changed the block/);
	await sleep(2500);
	check('blok wraca po usunieciu', !(await blockAtServer(srv, 'easytp:oneblock', 0, CENTER, 0, 'minecraft:air')));

	await srv.command('execute in easytp:oneblock run tp AlaWyspa 0.5 30 0.5', /Teleported/);
	check('ratunek z pustki', await waitAt(ala, 0.5, CENTER + 1, 0.5));
	check('ratunek: komunikat', !!(await lib.waitMsg(ala, /Wracasz na wyspę/, 3000, ala.log.length - 3)));

	await step('/motyw lista', ala, '/motyw', /Motywy generatora:/);
	check('/motyw aktywny', !!(await lib.waitMsg(ala, /polana: Polana \(poziom 0\) \[aktywny\]/)));
	check('/motyw zablokowany', !!(await lib.waitMsg(ala, /kres: Kres \(poziom 60\) \[zablokowany\]/)));
	await step('/motyw zablokowany odmowa', ala, '/motyw kres', /Nie ma motywu kres albo jest jeszcze zablokowany/);
	await step('/motyw wybor', ala, '/motyw polana', /Motyw generatora: Polana/);
	await step('/motyw auto', ala, '/motyw auto', /Generator sam wybiera/);

	await step('wplata bez pieniedzy', ala, '/is skarbiec wplac 50', /Za mało pieniędzy/);
	await srv.command('eco daj AlaWyspa 5000', /AlaWyspa: 5000 \$/);
	await step('wplata', ala, '/is skarbiec wplac 2500', /Wpłacono 2500 \$\. Skarbiec: 2600 \$/);
	await sleep(1000);
	let opened = new Promise((resolve) => ala.once('windowOpen', resolve));
	ala.chat('/is ulepszenia');
	let window = await opened;
	await sleep(300);
	check('ulepszenia: okno', window.slots[20] && window.slots[20].name === 'grass_block');
	opened = new Promise((resolve) => ala.once('windowOpen', resolve));
	ala.clickWindow(20, 0, 0).catch(() => {});
	window = await opened;
	await sleep(300);
	check('ulepszenie rozmiaru', ala.log.some((m) => /Ulepszenie kupione/.test(m)));
	ala.closeWindow(window);
	await step('nowy rozmiar', ala, '/is info', /poziom 1 /);
	check('info: rozmiar 100', !!(await lib.waitMsg(ala, /Rozmiar: 100x100, skarbiec: 600 \$/)));
	await sleep(1000);
	opened = new Promise((resolve) => ala.once('windowOpen', resolve));
	ala.chat('/is ulepszenia');
	window = await opened;
	await sleep(300);
	const from = ala.log.length;
	ala.clickWindow(20, 0, 0).catch(() => {});
	check('ulepszenie bez pieniedzy', !!(await lib.waitMsg(ala, /Za mało pieniędzy w skarbcu \(potrzeba: 8000 \$, jest: 600 \$\)/, 3000, from)));
	ala.closeWindow(window);
	await sleep(400);

	await step('zaproszenie', ala, '/is zapros BobWyspa', /Zaproszono gracza BobWyspa \(ważne 3 s\)/);
	check('zaproszony wie', !!(await lib.waitMsg(bob, /AlaWyspa zaprasza Cię na swoją wyspę/)));
	await sleep(3200);
	await step('zaproszenie wygasa', bob, '/is dolacz', /Brak zaproszenia na wyspę/);

	await srv.command('give BobWyspa minecraft:dirt 5', /Gave/);
	await step('odwiedziny', bob, '/is odwiedz AlaWyspa', /Odwiedzasz wyspę gracza AlaWyspa/);
	check('gosc w swiecie wysp', await waitDimension(srv, 'BobWyspa', 'easytp:oneblock'));
	await sleep(800);
	const countBefore = (await say(ala, '/is info', /rozbite bloki: (\d+)/)).hit.match(/rozbite bloki: (\d+)/)[1];
	const target = bob.blockAt(new (bob.entity.position.constructor)(0, CENTER, 0));
	await bob.dig(target, 'ignore').catch(() => {});
	await sleep(600);
	check('gosc nie rozbije bloku: blok zostaje', !(await blockAtServer(srv, 'easytp:oneblock', 0, CENTER, 0, 'minecraft:air')));
	await sleep(1000);
	const countAfter = (await say(ala, '/is info', /rozbite bloki: (\d+)/)).hit.match(/rozbite bloki: (\d+)/)[1];
	check('gosc nie nabija licznika', countBefore === countAfter, `${countBefore} -> ${countAfter}`);
	check('gosc: ekwipunek zapisany osobno', await itemCount(srv, 'BobWyspa', 'minecraft:dirt') === 0);
	const hurt = await srv.command('damage BobWyspa 5 minecraft:player_attack by AlaWyspa', /damage to|invulnerable/);
	check('na wyspach bez PvP', /invulnerable/.test(hurt), hurt);
	await sleep(1000);
	await say(bob, '/spawn', /./);
	check('/spawn wraca do glownego swiata', await waitDimension(srv, 'BobWyspa', 'minecraft:overworld'));
	await sleep(300);
	check('powrot: wlasne przedmioty', await itemCount(srv, 'BobWyspa', 'minecraft:dirt') === 5);

	await sleep(1000);
	ala.chat('/is zapros BobWyspa');
	await sleep(400);
	await step('dolaczenie', bob, '/is dolacz', /Witaj na wyspie/);
	check('czlonek w swiecie wysp', await waitDimension(srv, 'BobWyspa', 'easytp:oneblock'));
	await step('czlonek: wlasciciel nie odchodzi', ala, '/is opusc', /Właściciel nie może odejść/);
	await step('czlonek nie wyrzuca', bob, '/is wyrzuc AlaWyspa', /Tylko właściciel wyspy może to zrobić/);
	await step('topka wysp', bob, '/is topki', /1\. AlaWyspa: poziom \d+, bloki \d+/);

	const spawned = new Promise((resolve) => bob.once('spawn', resolve));
	await srv.command('kill BobWyspa', /Killed BobWyspa/);
	await Promise.race([spawned, sleep(5000)]);
	await sleep(800);
	check('po smierci na wyspie', await waitDimension(srv, 'BobWyspa', 'easytp:oneblock'));
	await sleep(300);
	const bobHearts = await srv.command('data get entity BobWyspa Pos[1]', /has the following entity data/);
	check('po smierci na bloku', /10[0-9]\./.test(bobHearts), bobHearts);
	await sleep(500);
	await say(bob, '/spawn', /./);
	await waitDimension(srv, 'BobWyspa', 'minecraft:overworld');
	await sleep(300);
	check('smierc na wyspie nie rusza glownego ekwipunku', await itemCount(srv, 'BobWyspa', 'minecraft:dirt') === 5);

	await srv.command('give BobWyspa minecraft:gold_ingot 3', /Gave/);
	const killed = new Promise((resolve) => bob.once('spawn', resolve));
	await srv.command('kill BobWyspa', /Killed BobWyspa/);
	await Promise.race([killed, sleep(5000)]);
	await sleep(800);
	check('smierc w glownym: zostaje tam', await waitDimension(srv, 'BobWyspa', 'minecraft:overworld'));

	await say(ala, '/spawn', /./);
	check('Ala w glownym', await waitDimension(srv, 'AlaWyspa', 'minecraft:overworld'));
	await sleep(400);
	check('powrot: bruk Ali', await itemCount(srv, 'AlaWyspa', 'minecraft:cobblestone') === 11);
	check('powrot: doswiadczenie', Number((await srv.command('data get entity AlaWyspa XpLevel', /entity data: \d+/)).match(/entity data: (\d+)/)[1]) === 7);
	const backHp = await srv.command('attribute AlaWyspa minecraft:max_health get', /AlaWyspa is ([0-9.]+)/);
	check('powrot: serca', /AlaWyspa is 24/.test(backHp), backHp);
	check('powrot: bez kilofa z wyspy', await itemCount(srv, 'AlaWyspa', 'minecraft:netherite_pickaxe') === 0);
	await sleep(lib.COOLDOWN_WAIT);
	await say(ala, '/is', /Witaj na wyspie/);
	check('powrot na wyspe', await waitDimension(srv, 'AlaWyspa', 'easytp:oneblock'));
	await sleep(400);
	check('wyspa: kilof czeka', await itemCount(srv, 'AlaWyspa', 'minecraft:netherite_pickaxe') === 1);
	check('wyspa: bez bruku z glownego', await itemCount(srv, 'AlaWyspa', 'minecraft:cobblestone') === 0);

	ala.quit();
	bob.quit();
	await sleep(1000);
	await srv.stop();
	srv.start();
	await srv.waitFor(/Done \(/, 180000);
	const ala2 = await connectBot('AlaWyspa');
	await sleep(500);
	check('restart: gracz zostaje na wyspie', await waitDimension(srv, 'AlaWyspa', 'easytp:oneblock'));
	await step('restart: dane wyspy', ala2, '/is info', /Wyspa gracza AlaWyspa: poziom 1 /);
	check('restart: czlonkowie', !!(await lib.waitMsg(ala2, /Członkowie \(2\/5\): AlaWyspa, BobWyspa/)));
	check('restart: blok istnieje', !(await blockAtServer(srv, 'easytp:oneblock', 0, CENTER, 0, 'minecraft:air')));
	const bob2 = await connectBot('BobWyspa');
	await step('wyrzucenie', ala2, '/is wyrzuc BobWyspa', /BobWyspa wyrzucony z wyspy/);
	check('wyrzucony wie', !!(await lib.waitMsg(bob2, /Wyrzucono Cię z wyspy/)));
	await step('usuwanie wymaga potwierdzenia', ala2, '/is usun', /Potwierdź: \/is usun potwierdz/);
	await step('usuwanie', ala2, '/is usun potwierdz', /Wyspa usunięta/);
	check('po usunieciu wraca do glownego', await waitDimension(srv, 'AlaWyspa', 'minecraft:overworld'));
	await sleep(400);
	check('po usunieciu: glowny ekwipunek', await itemCount(srv, 'AlaWyspa', 'minecraft:cobblestone') === 11);
	await step('po usunieciu: brak wyspy', ala2, '/is info', /Nie masz wyspy/);

	config({ enabled: false });
	await reloadConfig(srv);
	await step('wylaczony OneBlock', bob2, '/is', /Unknown or incomplete command/);
	ala2.quit();
	bob2.quit();
	await sleep(800);

	const errors = errorLines(srv);
	check('brak bledow w konsoli', errors.length === 0, errors.slice(0, 8).join('\n'));
}, reportFile);
