'use strict';
const lib = require('./lib');
const { parseArgs, check, connectBot, sleep, say, setConfig, reloadConfig, runMain, errorLines } = lib;

const { reportFile } = parseArgs('lifesteal.js');

const OPEN = {
	enabled: true, startHearts: 10, minHearts: 1, maxHearts: 30, legendaryHeartBonus: 10, blockSameIp: false,
	minVictimPlayMinutes: 0, sameVictimCooldownMinutes: 0, recentDeathCooldownMinutes: 0, killWindowMinutes: 60,
	maxKillsPerWindow: 1000, stoneHeartChance: 0, legendaryHeartPrice: 3, legendarySetPrice: 2,
};
const ECO = {
	enabled: true, currency: '$', killReward: 50, killTheftPercent: 10, marketMaxListings: 2, marketMaxPrice: 100000,
	sellPrices: { 'minecraft:diamond': 100, 'minecraft:coal': 3 },
	shop: [{ item: 'minecraft:golden_apple', count: 2, price: 150 }, { item: 'minecraft:nie_ma_takiego', count: 1, price: 5 }],
};
const config = (lifesteal = {}, economy = {}) => setConfig({ lifesteal: { ...OPEN, ...lifesteal }, economy: { ...ECO, ...economy } });

config();

async function step(name, bot, line, re, timeoutMs) {
	await sleep(1000);
	const r = await say(bot, line, re, timeoutMs);
	check(name, r.ok, r.ok ? '' : `${bot.username}: ${line} -> ${JSON.stringify(r.all)}`);
	return r;
}

async function kill(srv, victim, killer) {
	const spawned = new Promise((resolve) => victim.once('spawn', resolve));
	await srv.command(`damage ${victim.username} 100 minecraft:player_attack by ${killer.username}`, /damage to/);
	await Promise.race([spawned, sleep(5000)]);
	await sleep(700);
}

async function maxHealth(srv, name) {
	const line = await srv.command(`attribute ${name} minecraft:max_health get`, new RegExp(`${name} is ([0-9.]+)`));
	return Number(line.match(new RegExp(`${name} is ([0-9.]+)`))[1]);
}

async function itemCount(srv, name, item) {
	const line = await srv.command(`clear ${name} ${item} 0`, /Found \d+ matching|No items were found/);
	const m = line.match(/Found (\d+) matching/);
	return m ? Number(m[1]) : 0;
}

async function inventoryText(srv, name) {
	return srv.command(`data get entity ${name} Inventory`, new RegExp(`${name} has the following entity data|No elements|Found no elements`));
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

async function clickForWindow(bot, slot) {
	const opened = new Promise((resolve, reject) => {
		const timer = setTimeout(() => reject(new Error(`${bot.username}: klik ${slot} nie otworzyl okna`)), 5000);
		bot.once('windowOpen', (window) => { clearTimeout(timer); resolve(window); });
	});
	bot.clickWindow(slot, 0, 0).catch(() => {});
	const window = await opened;
	await sleep(300);
	return window;
}

async function clickAndExpect(name, bot, slot, re) {
	const from = bot.log.length;
	bot.clickWindow(slot, 0, 0).catch(() => {});
	const hit = await lib.waitMsg(bot, re, 4000, from);
	check(name, !!hit, `${bot.username}: ${JSON.stringify(bot.log.slice(from))}`);
	await sleep(400);
}

const close = async (bot) => {
	if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
	await sleep(300);
};

async function use(bot, itemName = 'heart_of_the_sea') {
	const slot = bot.inventory.slots.findIndex((s, i) => i >= 36 && i <= 44 && s && s.name === itemName);
	bot.setQuickBarSlot(slot >= 36 ? slot - 36 : 0);
	await sleep(200);
	bot.activateItem();
	await sleep(300);
	bot.deactivateItem();
}

runMain(async (srv) => {
	await srv.waitFor(/Done \(/, 180000);
	const ala = await connectBot('AlaTest');
	const bob = await connectBot('BobTest');

	await step('startowe serca', ala, '/serca', /Serca: 10 \(limit: 30\)/);
	check('start: 20 HP', await maxHealth(srv, 'AlaTest') === 20);

	await srv.command('eco daj BobTest 1000', /BobTest: 1000 \$/);
	await kill(srv, bob, ala);
	await step('zabojca ma +1 serce', ala, '/serca', /Serca: 11 /);
	await step('ofiara ma -1 serce', bob, '/serca', /Serca: 9 /);
	check('zabojca: 22 HP', await maxHealth(srv, 'AlaTest') === 22);
	check('ofiara po odrodzeniu: 18 HP', await maxHealth(srv, 'BobTest') === 18);
	check('ofiara dostala komunikat', bob.log.some((m) => /AlaTest odbiera Ci serce \(zostało: 9\)/.test(m)));
	check('ofiara wie o kradziezy', bob.log.some((m) => /Skradzione pieniądze: 100 \$/.test(m)));
	check('zabojca dostal nagrode', ala.log.some((m) => /Nagroda za zabójstwo: 150 \$ \(w tym skradzione: 100 \$\)/.test(m)));
	await step('stan zabojcy', ala, '/stan', /Stan konta: 150 \$/);
	await step('stan ofiary', bob, '/stan', /Stan konta: 900 \$/);

	await srv.command('serca ustaw BobTest 1', /BobTest: 1 /);
	check('ustaw serca: 2 HP', await maxHealth(srv, 'BobTest') === 2);
	await kill(srv, bob, ala);
	check('ofiara na minimum nie traci serca', ala.log.some((m) => /ma minimum serc/.test(m)));
	await step('minimum zostaje', bob, '/serca', /Serca: 1 /);
	await step('zabojca bez serca', ala, '/serca', /Serca: 11 /);
	await srv.command('serca ustaw BobTest 10', /BobTest: 10 /);

	await srv.command('serca ustaw AlaTest 30', /AlaTest: 30 /);
	await srv.command('clear AlaTest', /Removed|No items/);
	await kill(srv, bob, ala);
	check('limit: serce jako przedmiot', ala.log.some((m) => /serce trafia do ekwipunku/.test(m)));
	check('limit: przedmiot w ekwipunku', await itemCount(srv, 'AlaTest', 'minecraft:heart_of_the_sea') === 1);
	await step('limit: dalej 30', ala, '/serca', /Serca: 30 /);
	await sleep(1000);
	let from = ala.log.length;
	await use(ala);
	check('przedmiot przy limicie nie znika', !!(await lib.waitMsg(ala, /Masz już limit serc \(30\)/, 3000, from)));
	check('przedmiot nadal jest', await itemCount(srv, 'AlaTest', 'minecraft:heart_of_the_sea') === 1);

	await srv.command('serca ustaw AlaTest 10', /AlaTest: 10 /);
	from = ala.log.length;
	await use(ala);
	check('uzycie serca', !!(await lib.waitMsg(ala, /Serce dodane\. Serca: 11/, 3000, from)));
	check('serce zuzyte', await itemCount(srv, 'AlaTest', 'minecraft:heart_of_the_sea') === 0);
	check('uzycie serca: 22 HP', await maxHealth(srv, 'AlaTest') === 22);

	await step('wyplata', ala, '/wyplacserce 4', /Wypłacone serca: 4\. Zostało: 7/);
	check('wyplata: 4 przedmioty', await itemCount(srv, 'AlaTest', 'minecraft:heart_of_the_sea') === 4);
	check('wyplata: 14 HP', await maxHealth(srv, 'AlaTest') === 14);
	await step('wyplata ponad minimum', ala, '/wyplacserce 7', /Nie można wypłacić 7: musi zostać co najmniej 1/);

	let gui = await openGui(ala, '/sklepzaserca');
	check('sklep za serca: legendarne serce', gui.slots[20] && gui.slots[20].name === 'nether_star');
	check('sklep za serca: set', gui.slots[24] && gui.slots[24].name === 'netherite_chestplate');
	await clickForWindow(ala, 20);
	check('kupno legendarnego serca', ala.log.some((m) => /Kupiono: Legendarne serce/.test(m)));
	await close(ala);
	check('legendarne serce w ekwipunku', await itemCount(srv, 'AlaTest', 'minecraft:nether_star') === 1);
	check('zaplata 3 serca', await itemCount(srv, 'AlaTest', 'minecraft:heart_of_the_sea') === 1);

	gui = await openGui(ala, '/sklepzaserca');
	await clickAndExpect('za malo serc na set', ala, 24, /Za mało serc w ekwipunku \(potrzeba: 2, masz: 1\)/);
	await close(ala);
	await step('wyplata na set', ala, '/wyplacserce 1', /Wypłacone serca: 1/);
	gui = await openGui(ala, '/sklepzaserca');
	await clickForWindow(ala, 24);
	await close(ala);
	check('kupno setu', ala.log.some((m) => /Kupiono: Legendarny set/.test(m)));
	check('set: napiersnik', await itemCount(srv, 'AlaTest', 'minecraft:netherite_chestplate') === 1);
	const inv = await inventoryText(srv, 'AlaTest');
	check('set: Ochrona V', /protection": 5/.test(inv), inv);
	check('set: Niezniszczalnosc III', /unbreaking": 3/.test(inv), inv);

	await srv.command('clear AlaTest minecraft:netherite_helmet', /Removed|No items/);
	await srv.command('clear AlaTest minecraft:netherite_chestplate', /Removed|No items/);
	await srv.command('clear AlaTest minecraft:netherite_leggings', /Removed|No items/);
	await srv.command('clear AlaTest minecraft:netherite_boots', /Removed|No items/);
	await sleep(1000);
	from = ala.log.length;
	await use(ala, 'nether_star');
	check('legendarne serce podnosi limit', !!(await lib.waitMsg(ala, /Nowy limit serc: 40/, 3000, from)));
	check('legendarne serce zuzyte', await itemCount(srv, 'AlaTest', 'minecraft:nether_star') === 0);
	await srv.command('clear AlaTest', /Removed|No items/);
	await srv.command('give AlaTest minecraft:nether_star 1', /Gave/);
	await sleep(500);
	from = ala.log.length;
	await use(ala, 'nether_star');
	await sleep(500);
	check('zwykla gwiazda nie dziala', await itemCount(srv, 'AlaTest', 'minecraft:nether_star') === 1 && ala.log.length === from);
	await srv.command('clear AlaTest', /Removed|No items/);
	await srv.command('serca ustaw AlaTest 40', /AlaTest: 40 /);
	await step('nowy limit dziala', ala, '/serca', /Serca: 40 \(limit: 40\)/);
	await srv.command('serca ustaw AlaTest 10', /AlaTest: 10 /);

	config({ blockSameIp: true, ignoreLocalIp: false });
	await reloadConfig(srv);
	await kill(srv, bob, ala);
	check('to samo IP: bez nagrody', ala.log.some((m) => /Bez nagrody za BobTest: to samo IP/.test(m)));
	await step('to samo IP: serca bez zmian', ala, '/serca', /Serca: 10 /);
	config({ blockSameIp: true, ignoreLocalIp: true });
	await reloadConfig(srv);
	await kill(srv, bob, ala);
	check('tunel: adres lokalny nie blokuje nagrody', ala.log.some((m) => /Zabójstwo BobTest: \+1 serce \(masz 11\)/.test(m)));
	await srv.command('serca ustaw AlaTest 10', /AlaTest: 10 /);

	config({ minVictimPlayMinutes: 100000 });
	await reloadConfig(srv);
	await kill(srv, bob, ala);
	check('nowy gracz: bez nagrody', ala.log.some((m) => /Bez nagrody za BobTest: to nowy gracz/.test(m)));

	config({ recentDeathCooldownMinutes: 5 });
	await reloadConfig(srv);
	await kill(srv, bob, ala);
	check('niedawna smierc: bez nagrody', ala.log.some((m) => /Bez nagrody za BobTest: ten gracz niedawno zginął/.test(m)));

	config({ sameVictimCooldownMinutes: 5 });
	await reloadConfig(srv);
	from = ala.log.length;
	await kill(srv, bob, ala);
	check('ta sama ofiara: bez nagrody', ala.log.slice(from).some((m) => /Bez nagrody za BobTest: ten gracz był zabity/.test(m)));
	await step('ta sama ofiara: serca bez zmian', ala, '/serca', /Serca: 10 /);

	config({ maxKillsPerWindow: 1 });
	await reloadConfig(srv);
	from = ala.log.length;
	await kill(srv, bob, ala);
	check('limit zabojstw: bez nagrody', ala.log.slice(from).some((m) => /zbyt wiele zabójstw/.test(m)));

	config();
	await reloadConfig(srv);
	from = ala.log.length;
	await kill(srv, bob, ala);
	check('po luzowaniu zasad znow nagroda', ala.log.slice(from).some((m) => /Zabójstwo BobTest: \+1 serce \(masz 11\)/.test(m)));

	config({ stoneHeartChance: 1 });
	await reloadConfig(srv);
	await srv.command('clear AlaTest', /Removed|No items/);
	await srv.command('give AlaTest minecraft:diamond_pickaxe 1', /Gave/);
	const pos = ala.entity.position.floored();
	await srv.command(`setblock ${pos.x + 1} ${pos.y} ${pos.z} minecraft:stone`, /Changed the block/);
	ala.setQuickBarSlot(0);
	await sleep(500);
	const block = ala.blockAt(pos.offset(1, 0, 0));
	check('kamien postawiony', block && block.name === 'stone', block && block.name);
	await ala.dig(block);
	await sleep(500);
	check('serce z kamienia', ala.log.some((m) => /Serce znalezione w kamieniu/.test(m)));
	check('serce z kamienia w ekwipunku', await itemCount(srv, 'AlaTest', 'minecraft:heart_of_the_sea') === 1);

	config({ stoneHeartChance: 0 });
	await reloadConfig(srv);
	await srv.command('clear AlaTest', /Removed|No items/);
	await srv.command('clear BobTest', /Removed|No items/);
	await srv.command('eco ustaw AlaTest 1000', /AlaTest: 1000 \$/);
	await srv.command('eco ustaw BobTest 100', /BobTest: 100 \$/);

	await step('przelew', ala, '/pay BobTest 250', /Przelano 250 \$ dla BobTest/);
	check('przelew: odbiorca wie', !!(await lib.waitMsg(bob, /AlaTest przelewa Ci 250 \$/)));
	await step('przelew za duzy', ala, '/pay BobTest 5000', /Za mało pieniędzy \(potrzeba: 5000 \$, masz: 750 \$\)/);
	await step('przelew do siebie', ala, '/przelej AlaTest 5', /samemu sobie/);
	await step('stan innego', ala, '/stan BobTest', /Stan konta gracza BobTest: 350 \$/);
	await srv.command('eco zabierz BobTest 50', /BobTest: 300 \$/);
	await step('baltop kolejnosc', ala, '/baltop', /1\. AlaTest: 750 \$/);
	await step('eco dla gracza zablokowane', ala, '/eco daj AlaTest 5', /Unknown or incomplete command|Nieznana/);
	await step('serca ustaw dla gracza zablokowane', ala, '/serca ustaw AlaTest 30', /Unknown or incomplete|Incorrect argument|Expected|Nieznana|No player/);

	await srv.command('give BobTest minecraft:diamond 5', /Gave/);
	await step('sprzedaz z reki', bob, '/sprzedaj', /Sprzedano przedmioty \(5\) za 500 \$/);
	check('sprzedaz: diamenty znikly', await itemCount(srv, 'BobTest', 'minecraft:diamond') === 0);
	await srv.command('give BobTest minecraft:diamond[minecraft:custom_name="Pamiatka"] 1', /Gave/);
	await step('nazwanego nie mozna sprzedac', bob, '/sprzedaj', /Tego przedmiotu nie można sprzedać/);
	await step('sprzedaz wszystkiego bez towaru', bob, '/sprzedaj wszystko', /W ekwipunku nie ma nic do sprzedania/);
	check('pamiatka zostala', await itemCount(srv, 'BobTest', 'minecraft:diamond') === 1);
	await srv.command('give BobTest minecraft:coal 10', /Gave/);
	await srv.command('give BobTest minecraft:diamond 2', /Gave/);
	await step('sprzedaz wszystkiego', bob, '/sprzedaj wszystko', /Sprzedano przedmioty \(12\) za 230 \$/);
	await srv.command('clear BobTest', /Removed|No items/);

	await srv.command('eco ustaw BobTest 100', /BobTest: 100 \$/);
	gui = await openGui(bob, '/sklep');
	check('sklep: oferta z konfiguracji', gui.slots[0] && gui.slots[0].name === 'golden_apple' && gui.slots[0].count === 2);
	check('sklep: bledna oferta pominieta', !gui.slots[1]);
	await clickAndExpect('sklep: za malo pieniedzy', bob, 0, /Za mało pieniędzy \(potrzeba: 150 \$, masz: 100 \$\)/);
	check('sklep: nic nie dostal', await itemCount(srv, 'BobTest', 'minecraft:golden_apple') === 0);
	await srv.command('eco ustaw BobTest 200', /BobTest: 200 \$/);
	await clickAndExpect('sklep: kupno', bob, 0, /Kupiono za 150 \$/);
	check('sklep: dostal towar', await itemCount(srv, 'BobTest', 'minecraft:golden_apple') === 2);
	bob.clickWindow(49, 0, 0).catch(() => {});
	await sleep(500);
	bob.clickWindow(49, 0, 1).catch(() => {});
	await sleep(500);
	bob.clickWindow(0, 0, 1).catch(() => {});
	await sleep(500);
	await close(bob);
	check('okno: ikon nie da sie zabrac', await itemCount(srv, 'BobTest', 'minecraft:paper') === 0);
	check('okno: towaru nie da sie zabrac za darmo', await itemCount(srv, 'BobTest', 'minecraft:golden_apple') === 2);
	await step('sklep: pieniadze po kupnie', bob, '/stan', /Stan konta: 50 \$/);
	await srv.command('clear BobTest', /Removed|No items/);

	await step('rynek: pusta reka', ala, '/rynek wystaw 100', /Weź do ręki przedmiot/);
	await srv.command('give AlaTest minecraft:emerald 3', /Gave/);
	await step('rynek: cena za duza', ala, '/rynek wystaw 100001', /Cena musi być od 1 do 100000/);
	await step('rynek: wystawienie', ala, '/rynek wystaw 100', /Wystawiono na rynek za 100 \$/);
	check('rynek: przedmiot zabrany', await itemCount(srv, 'AlaTest', 'minecraft:emerald') === 0);
	gui = await openGui(bob, '/rynek');
	check('rynek: oferta widoczna', gui.slots[0] && gui.slots[0].name === 'emerald' && gui.slots[0].count === 3);
	await clickAndExpect('rynek: za malo pieniedzy', bob, 0, /Za mało pieniędzy \(potrzeba: 100 \$, masz: 50 \$\)/);
	await close(bob);
	await srv.command('eco ustaw BobTest 300', /BobTest: 300 \$/);
	gui = await openGui(bob, '/rynek');
	await clickForWindow(bob, 0);
	await close(bob);
	check('rynek: kupno', bob.log.some((m) => /Kupiono z rynku za 100 \$/.test(m)));
	check('rynek: sprzedawca wie', !!(await lib.waitMsg(ala, /BobTest kupuje Twoją ofertę za 100 \$/)));
	check('rynek: kupiec ma towar', await itemCount(srv, 'BobTest', 'minecraft:emerald') === 3);
	await step('rynek: pieniadze kupca', bob, '/stan', /Stan konta: 200 \$/);
	await step('rynek: pieniadze sprzedawcy', ala, '/stan', /Stan konta: 850 \$/);

	await srv.command('give AlaTest minecraft:gold_ingot 1', /Gave/);
	await step('rynek: druga oferta', ala, '/rynek wystaw 5', /Wystawiono/);
	gui = await openGui(ala, '/rynek');
	check('rynek: wlasna oferta', gui.slots[0] && gui.slots[0].name === 'gold_ingot');
	await clickForWindow(ala, 0);
	await close(ala);
	check('rynek: zdjecie oferty', ala.log.some((m) => /Oferta zdjęta/.test(m)));
	check('rynek: przedmiot wrocil', await itemCount(srv, 'AlaTest', 'minecraft:gold_ingot') === 1);
	await srv.command('clear AlaTest', /Removed|No items/);

	await srv.command('give AlaTest minecraft:diamond_sword[minecraft:enchantments={"minecraft:sharpness":5},minecraft:custom_name="Miecz"] 1', /Gave/);
	await step('rynek: miecz wystawiony', ala, '/rynek wystaw 7', /Wystawiono/);
	await srv.command('give AlaTest minecraft:stick 1', /Gave/);
	await step('rynek: druga oferta do limitu', ala, '/rynek wystaw 7', /Wystawiono/);
	await srv.command('give AlaTest minecraft:stick 1', /Gave/);
	await step('rynek: limit ofert', ala, '/rynek wystaw 7', /Osiągnięto limit ofert \(2\)/);

	gui = await openGui(ala, '/topki');
	check('topki: ikona zabojstw', gui.slots[19] && gui.slots[19].name === 'diamond_sword');
	const list = await clickForWindow(ala, 19);
	check('topki: lista z glowa', list.slots[0] && list.slots[0].name === 'player_head');
	check('topki: lider z nazwa', JSON.stringify(list.slots[0]).includes('AlaTest'), JSON.stringify(list.slots[0]));
	check('topki: przycisk wroc', list.slots[48] && list.slots[48].name === 'barrier');
	const back = await clickForWindow(ala, 48);
	check('topki: powrot', back.slots[19] && back.slots[19].name === 'diamond_sword');
	await close(ala);
	await step('topki tekstem', ala, '/topki pieniadze', /1\. AlaTest: 850 \$/);
	await step('statystyki: serca', ala, '/statystyki', /Serca: \d+/);
	check('statystyki: zabojstwa', !!(await lib.waitMsg(ala, /Zabójstwa: \d+, śmierci: 0, K\/D: \d+\.\d\d, potwory: 0/)));
	check('statystyki: wykopany kamien', !!(await lib.waitMsg(ala, /Wykopane bloki: 1 \(kamień: 1\)/)));
	await step('statystyki innego', ala, '/statystyki BobTest', /Statystyki gracza BobTest/);
	const consoleTop = await srv.command('topki', /Z konsoli użyj/);
	check('topki z konsoli: podpowiedz', !!consoleTop);
	const consoleStats = await srv.command('statystyki BobTest', /Czas gry/);
	check('statystyki z konsoli', !!consoleStats);
	const info = await srv.command('easytp', /Tryb gry:/);
	check('/easytp pokazuje tryb gry', !!info);

	await srv.command('serca ustaw BobTest 13', /BobTest: 13 /);
	ala.quit();
	bob.quit();
	await sleep(1000);
	await srv.stop();
	srv.start();
	await srv.waitFor(/Done \(/, 180000);

	const ala2 = await connectBot('AlaTest');
	const bob2 = await connectBot('BobTest');
	await step('restart: serca i limit', ala2, '/serca', /limit: 40/);
	await step('restart: pieniadze', ala2, '/stan', /Stan konta: 850 \$/);
	check('restart: 26 HP', await maxHealth(srv, 'BobTest') === 26);
	gui = await openGui(bob2, '/rynek');
	const sword = gui.slots.slice(0, 45).findIndex((s) => s && s.name === 'diamond_sword');
	check('restart: miecz na rynku', sword >= 0);
	await clickForWindow(bob2, sword);
	await close(bob2);
	const swordData = await inventoryText(srv, 'BobTest');
	check('rynek: enchant po zapisie', /sharpness": 5/.test(swordData), swordData);
	check('rynek: nazwa po zapisie', /Miecz/.test(swordData), swordData);

	config({}, { enabled: false });
	await reloadConfig(srv);
	await step('wylaczona ekonomia', ala2, '/stan', /Unknown or incomplete command|Nieznana/);
	config({ enabled: false }, {});
	await reloadConfig(srv);
	await step('wylaczone serca', bob2, '/serca', /Unknown or incomplete command|Nieznana/);
	ala2.quit();
	bob2.quit();
	await sleep(1500);
	const bob3 = await connectBot('BobTest');
	check('wylaczone serca: 20 HP', await maxHealth(srv, 'BobTest') === 20);
	bob3.quit();
	await sleep(800);

	const errors = errorLines(srv);
	check('brak bledow w konsoli', errors.length === 0, errors.slice(0, 8).join('\n'));
}, reportFile);
