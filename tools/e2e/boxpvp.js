'use strict';
const lib = require('./lib');
const { parseArgs, check, connectBot, sleep, say, setConfig, reloadConfig, runMain, errorLines } = lib;

const { reportFile } = parseArgs('boxpvp.js');

const BOX = {
	combatTagSeconds: 3, keepGearPercent: 0, killReward: 100, blockSameIp: false, minVictimPlayMinutes: 0,
	sameVictimCooldownMinutes: 0, recentDeathCooldownMinutes: 0, kitCooldownSeconds: 2, upgradeBaseCost: 100,
	upgradeBaseChance: 100, upgradeChancePenalty: 0, mineResetSeconds: 25, afkIntervalSeconds: 3, afkReward: 7,
};
const config = (boxpvp = {}) => setConfig({ boxpvp: { ...BOX, ...boxpvp } });
config();

const DIM = 'easytp:boxpvp';
const SLOT_SWORD = 23;
const WARP_MINE = 21;
const WARP_PVP = 23;
const WARP_AFK = 25;
const WARP_SPAWN = 19;

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

async function blockIs(srv, x, y, z, block) {
	const line = await srv.command(`execute in ${DIM} if block ${x} ${y} ${z} ${block}`, /Test passed|Test failed/);
	return /passed/.test(line);
}

async function openGui(bot, line) {
	await sleep(1000);
	const opened = new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`${bot.username}: ${line} nie otworzylo okna`)), 5000);
		bot.once('windowOpen', (window) => { clearTimeout(timer); resolve(window); });
	});
	bot.chat(line);
	const window = await opened;
	await sleep(300);
	return window;
}

async function warp(bot, slot) {
	const window = await openGui(bot, '/warpy');
	bot.clickWindow(slot, 0, 0).catch(() => {});
	await sleep(600);
	if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
	await sleep(400);
	return window;
}

async function clickUpgrade(bot, slot) {
	const opened = new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`${bot.username}: klik ${slot} nie otworzyl okna`)), 5000);
		bot.once('windowOpen', (window) => { clearTimeout(timer); resolve(window); });
	});
	bot.clickWindow(slot, 0, 0).catch(() => {});
	const window = await opened;
	await sleep(300);
	return window;
}

async function balance(bot) {
	await sleep(1000);
	const r = await say(bot, '/stan', /Stan konta: (\d+) \$/);
	return r.ok ? Number(r.hit.match(/Stan konta: (\d+)/)[1]) : -1;
}

runMain(async (srv) => {
	await srv.waitFor(/Done \(/, 180000);
	const ala = await connectBot('AlaBox');
	const bob = await connectBot('BobBox');
	const Vec3 = ala.entity.position.constructor;

	await srv.command('give AlaBox minecraft:cobblestone 9', /Gave/);
	await step('/kit poza trybem', ala, '/kit', /Ta komenda działa tylko w BoxPvP/);
	await step('/ulepsz poza trybem', ala, '/ulepsz', /Ta komenda działa tylko w BoxPvP/);
	await step('/warpy poza trybem', ala, '/warpy', /Ta komenda działa tylko w BoxPvP/);

	await step('wejscie do BoxPvP', ala, '/box', /Witaj w BoxPvP/);
	check('swiat BoxPvP', await waitDimension(srv, 'AlaBox', DIM));
	check('na spawnie', await waitAt(ala, 0.5, 65, 0.5));
	check('osobny ekwipunek', await itemCount(srv, 'AlaBox', 'minecraft:cobblestone') === 0);
	check('mapa: podloga spawnu', await blockIs(srv, 0, 64, 0, 'minecraft:smooth_quartz'));
	check('mapa: strefa PvP', await blockIs(srv, -45, 64, 0, 'minecraft:red_concrete'));
	check('mapa: dno kopalni', await blockIs(srv, 45, 64, 0, 'minecraft:bedrock'));
	check('mapa: kopalnia pelna', !(await blockIs(srv, 45, 69, 0, 'minecraft:air')));
	await step('blokada /sklep', ala, '/sklep', /Ta komenda nie działa w tym trybie gry/);
	await step('blokada /rynek', ala, '/rynek', /Ta komenda nie działa w tym trybie gry/);
	await step('blokada /sprzedaj', ala, '/sprzedaj', /Ta komenda nie działa w tym trybie gry/);
	await step('blokada /wyplacserce', ala, '/wyplacserce 1', /Ta komenda nie działa w tym trybie gry/);

	await step('zestaw', ala, '/kit', /Zestaw startowy odebrany/);
	check('zestaw: miecz', await itemCount(srv, 'AlaBox', 'minecraft:iron_sword') === 1);
	check('zestaw: kilof', await itemCount(srv, 'AlaBox', 'minecraft:iron_pickaxe') === 1);
	check('zestaw: zbroja zalozona', /iron_chestplate/.test(await srv.command('data get entity AlaBox equipment.chest', /has the following entity data|Found no elements/)));
	const again = await say(ala, '/kit', /Zestaw będzie dostępny za \d+ s/);
	check('zestaw: przerwa', again.ok, JSON.stringify(again.all));
	await sleep(2200);
	await step('zestaw: bez dubli', ala, '/kit', /Zestaw startowy odebrany/);
	check('zestaw: nadal jeden miecz', await itemCount(srv, 'AlaBox', 'minecraft:iron_sword') === 1);

	await sleep(800);
	ala.setQuickBarSlot(0);
	const floor = ala.blockAt(new Vec3(0, 64, 0));
	await ala.dig(floor, 'ignore').catch(() => {});
	await sleep(500);
	check('poza kopalnia nie da sie kopac', await blockIs(srv, 0, 64, 0, 'minecraft:smooth_quartz'));

	const window = await openGui(ala, '/warpy');
	check('warpy: okno', window.slots[WARP_MINE] && window.slots[WARP_MINE].name === 'diamond_pickaxe');
	ala.closeWindow(window);
	await warp(ala, WARP_MINE);
	check('warp do kopalni', await waitAt(ala, 45.5, 70, 0.5));

	const before = await balance(ala);
	ala.setQuickBarSlot(1);
	await sleep(300);
	const ore = ala.blockAt(new Vec3(45, 69, 0));
	await ala.dig(ore, 'ignore');
	await sleep(600);
	check('kopalnia: blok znika', await blockIs(srv, 45, 69, 0, 'minecraft:air'));
	const after = await balance(ala);
	check('kopalnia: automatyczna sprzedaz', after > before, `${before} -> ${after}`);
	const drops = await srv.command(`execute in ${DIM} if entity @e[type=minecraft:item]`, /Test passed|Test failed/);
	check('kopalnia: bez wypadajacych przedmiotow', /failed/.test(drops), drops);
	const reset = await lib.waitMsg(ala, /Kopalnia odnowiona!/, 30000, ala.log.length);
	check('kopalnia: odnowienie', !!reset);
	check('kopalnia: blok wraca', !(await blockIs(srv, 45, 69, 0, 'minecraft:air')));

	await srv.command('eco ustaw AlaBox 150', /AlaBox: 150 \$/);
	let gui = await openGui(ala, '/ulepsz');
	check('ulepszenia: miecz w oknie', gui.slots[SLOT_SWORD] && gui.slots[SLOT_SWORD].name === 'iron_sword');
	gui = await clickUpgrade(ala, SLOT_SWORD);
	check('ulepszenie: sukces', ala.log.some((m) => /Ulepszono: Miecz, poziom 1/.test(m)));
	let inv = await srv.command('data get entity AlaBox Inventory', /AlaBox has the following entity data/);
	check('ulepszenie: Ostrosc I', /sharpness": 1/.test(inv), inv.slice(0, 500));
	check('ulepszenie: gwiazdki', /easytp_stars: 1/.test(inv), inv.slice(0, 500));
	const noMoney = ala.log.length;
	ala.clickWindow(SLOT_SWORD, 0, 0).catch(() => {});
	check('ulepszenie: za malo pieniedzy', !!(await lib.waitMsg(ala, /Za mało pieniędzy \(potrzeba: 400 \$, masz: 50 \$\)/, 3000, noMoney)));
	ala.closeWindow(gui);
	await sleep(400);

	config({ upgradeBaseChance: 0 });
	await reloadConfig(srv);
	await srv.command('eco ustaw AlaBox 1000', /AlaBox: 1000 \$/);
	gui = await openGui(ala, '/ulepsz');
	gui = await clickUpgrade(ala, SLOT_SWORD);
	check('ulepszenie: porazka', ala.log.some((m) => /Ulepszenie nieudane, stracone pieniądze: 400 \$/.test(m)));
	ala.closeWindow(gui);
	await sleep(400);
	inv = await srv.command('data get entity AlaBox Inventory', /AlaBox has the following entity data/);
	check('ulepszenie: po porazce bez zmian', /easytp_stars: 1/.test(inv));
	check('ulepszenie: pieniadze stracone', await balance(ala) === 600);
	config();
	await reloadConfig(srv);

	await srv.command('give BobBox minecraft:dirt 4', /Gave/);
	await step('Bob wchodzi', bob, '/box', /Witaj w BoxPvP/);
	await waitDimension(srv, 'BobBox', DIM);
	await step('Bob: zestaw', bob, '/kit', /Zestaw startowy odebrany/);
	await sleep(500);
	await warp(ala, WARP_SPAWN);
	check('warp na spawn', await waitAt(ala, 0.5, 65, 0.5));
	const safe = await srv.command('damage BobBox 5 minecraft:player_attack by AlaBox', /damage to|invulnerable/);
	check('spawn: bez PvP', /invulnerable/.test(safe), safe);

	await warp(ala, WARP_PVP);
	await warp(bob, WARP_PVP);
	check('warp do strefy PvP', await waitAt(bob, -44.5, 65, 0.5));
	const hit = await srv.command('damage BobBox 2 minecraft:player_attack by AlaBox', /damage to|invulnerable/);
	check('strefa PvP: obrazenia', /damage to/.test(hit), hit);
	const blocked = await say(ala, '/spawn', /Trwa walka\. Poczekaj jeszcze \d+ s/);
	check('walka: komendy zablokowane', blocked.ok, JSON.stringify(blocked.all));
	const blockedVictim = await say(bob, '/box wyjdz', /Trwa walka\. Poczekaj jeszcze \d+ s/);
	check('walka: ofiara tez', blockedVictim.ok, JSON.stringify(blockedVictim.all));
	check('walka: koniec znacznika', !!(await lib.waitMsg(ala, /Koniec znacznika walki/, 6000, ala.log.length - 2)));

	let mark = ala.log.length;
	let spawned = new Promise((resolve) => bob.once('spawn', resolve));
	await srv.command('damage BobBox 500 minecraft:player_attack by AlaBox', /damage to/);
	await Promise.race([spawned, sleep(5000)]);
	await sleep(800);
	check('zabojstwo: nagroda', !!(await lib.waitMsg(ala, /Zabójstwo BobBox: \+100 \$, seria: 1/, 3000, mark)));
	check('smierc: odrodzenie w BoxPvP', await waitDimension(srv, 'BobBox', DIM));
	check('smierc: na spawnie', await waitAt(bob, 0.5, 65, 0.5));
	check('smierc: sprzet przepadl', await itemCount(srv, 'BobBox', 'minecraft:iron_sword') === 0);
	const loot = await srv.command(`execute in ${DIM} if entity @e[type=minecraft:item,distance=0..,x=-45,y=65,z=0,dx=8,dy=4,dz=8]`, /Test passed|Test failed/);
	const lootWide = await srv.command(`execute in ${DIM} if entity @e[type=minecraft:item]`, /Test passed|Test failed/);
	check('smierc: lupy na ziemi', /passed/.test(loot) || /passed/.test(lootWide), `${loot} / ${lootWide}`);
	await sleep(800);
	const mainHealth = await srv.command('attribute BobBox minecraft:max_health get', /BobBox is ([0-9.]+)/);
	check('BoxPvP: 20 HP', /BobBox is 20/.test(mainHealth), mainHealth);
	check('zabojstwo: bez utraty serca w trybie', (await say(bob, '/serca', /Serca: 10 /)).ok);

	await srv.command('kill @e[type=minecraft:item]', /Killed|No entity/).catch(() => null);
	config({ keepGearPercent: 100 });
	await reloadConfig(srv);
	await sleep(2200);
	await step('Bob: nowy zestaw', bob, '/kit', /Zestaw startowy odebrany/);
	await warp(bob, WARP_PVP);
	await sleep(4000);
	mark = bob.log.length;
	spawned = new Promise((resolve) => bob.once('spawn', resolve));
	await srv.command('damage BobBox 500 minecraft:player_attack by AlaBox', /damage to/);
	await Promise.race([spawned, sleep(5000)]);
	check('zachowanie sprzetu: komunikat', !!(await lib.waitMsg(bob, /Zachowane części sprzętu: 6/, 4000, mark)));
	await sleep(500);
	check('zachowanie sprzetu: miecz', await itemCount(srv, 'BobBox', 'minecraft:iron_sword') === 1);
	check('zachowanie sprzetu: kilof', await itemCount(srv, 'BobBox', 'minecraft:iron_pickaxe') === 1);
	config();
	await reloadConfig(srv);

	await warp(bob, WARP_PVP);
	await sleep(4000);
	await srv.command('damage BobBox 1 minecraft:player_attack by AlaBox', /damage to/);
	mark = ala.log.length;
	await sleep(300);
	bob.quit();
	check('wyjscie w walce: zabojstwo', !!(await lib.waitMsg(ala, /Zabójstwo BobBox: \+100 \$/, 5000, mark)));
	await sleep(1000);
	const bob2 = await connectBot('BobBox');
	await sleep(1200);
	check('wyjscie w walce: powrot do BoxPvP', await waitDimension(srv, 'BobBox', DIM));
	check('wyjscie w walce: sprzet przepadl', await itemCount(srv, 'BobBox', 'minecraft:iron_sword') === 0);
	const hp = await srv.command('data get entity BobBox Health', /has the following entity data: ([0-9.]+)/);
	check('wyjscie w walce: zywy', Number(hp.match(/entity data: ([0-9.]+)/)[1]) > 0, hp);

	await sleep(3500);
	await warp(ala, WARP_AFK);
	check('warp do AFK', await waitAt(ala, 0.5, 65, 38.5));
	const afk = ala.log.length;
	check('strefa AFK: nagroda', !!(await lib.waitMsg(ala, /Nagroda za AFK: \+7 \$/, 6000, afk)));

	await srv.command(`execute in ${DIM} run tp AlaBox 0.5 30 0.5`, /Teleported/);
	check('ratunek z pustki', await waitAt(ala, 0.5, 65, 0.5));

	await step('staty', ala, '/box staty', /BoxPvP: zabójstwa \d+, śmierci 0, seria \d+, najlepsza seria \d+/);
	await step('topka box', ala, '/topki box', /1\. AlaBox: \d+/);
	await step('topka seria', ala, '/topki seria', /1\. AlaBox: \d+/);

	await sleep(1000);
	await say(ala, '/box wyjdz', /./, 1500);
	check('wyjscie do glownego', await waitDimension(srv, 'AlaBox', 'minecraft:overworld'));
	await sleep(400);
	check('wyjscie: wlasny ekwipunek', await itemCount(srv, 'AlaBox', 'minecraft:cobblestone') === 9);
	check('wyjscie: bez sprzetu BoxPvP', await itemCount(srv, 'AlaBox', 'minecraft:iron_pickaxe') === 0);
	await step('powrot do BoxPvP', ala, '/box', /Witaj w BoxPvP/);
	await waitDimension(srv, 'AlaBox', DIM);
	await sleep(400);
	check('powrot: sprzet czeka', await itemCount(srv, 'AlaBox', 'minecraft:iron_pickaxe') >= 1);

	ala.quit();
	bob2.quit();
	await sleep(1000);
	await srv.stop();
	srv.start();
	await srv.waitFor(/Done \(/, 180000);
	const ala2 = await connectBot('AlaBox');
	await sleep(500);
	check('restart: gracz zostaje w BoxPvP', await waitDimension(srv, 'AlaBox', DIM));
	check('restart: mapa bez zmian', await blockIs(srv, 0, 64, 0, 'minecraft:smooth_quartz'));
	await step('restart: staty', ala2, '/box staty', /BoxPvP: zabójstwa [1-9]/);
	const inv2 = await srv.command('data get entity AlaBox Inventory', /AlaBox has the following entity data/);
	check('restart: gwiazdki', /easytp_stars: 1/.test(inv2));

	config({ enabled: false });
	await reloadConfig(srv);
	await step('wylaczony BoxPvP', ala2, '/kit', /Unknown or incomplete command/);
	ala2.quit();
	await sleep(800);

	const errors = errorLines(srv);
	check('brak bledow w konsoli', errors.length === 0, errors.slice(0, 8).join('\n'));
}, reportFile);
