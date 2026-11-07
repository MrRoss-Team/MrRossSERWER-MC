'use strict';
const lib = require('./lib');
const { parseArgs, check, connectBot, sleep, say, setConfig, reloadConfig, runMain, errorLines } = lib;

const { reportFile } = parseArgs('clans.js');

const LIFESTEAL = { blockSameIp: false, minVictimPlayMinutes: 0, sameVictimCooldownMinutes: 0, recentDeathCooldownMinutes: 0 };
const config = (clans = {}) => setConfig({ lifesteal: LIFESTEAL, clans: { maxMembers: 3, inviteSeconds: 3, ...clans } });
config();

async function step(name, bot, line, re, timeoutMs) {
	await sleep(1000);
	const r = await say(bot, line, re, timeoutMs);
	check(name, r.ok, r.ok ? '' : `${bot.username}: ${line} -> ${JSON.stringify(r.all)}`);
	return r;
}

async function health(srv, name) {
	const line = await srv.command(`data get entity ${name} Health`, new RegExp(`${name} has the following entity data: ([0-9.]+)`));
	return Number(line.match(/entity data: ([0-9.]+)/)[1]);
}

async function teamMembers(srv, team) {
	const line = await srv.command(`team list ${team}`, /has \d+ member|has no members|Unknown team/);
	return line;
}

runMain(async (srv) => {
	await srv.waitFor(/Done \(/, 180000);
	const ala = await connectBot('AlaKlan');
	const bob = await connectBot('BobKlan');
	const cez = await connectBot('CezKlan');
	const dan = await connectBot('DanKlan');

	await step('/klan pokazuje pomoc', ala, '/klan', /Klany: \/zaloz <tag> <nazwa>/);
	await step('/k bez klanu', ala, '/k halo', /Nie należysz do żadnego klanu/);
	await step('/klan lista pusta', ala, '/klan lista', /Nie ma jeszcze żadnego klanu/);
	await step('/opusc bez klanu', ala, '/opusc', /Nie należysz do żadnego klanu/);
	await step('zly tag', ala, '/zaloz a Dobra nazwa', /Tag musi mieć od 2 do 5/);
	await step('zla nazwa', ala, '/zaloz abc xy', /Nazwa musi mieć od 3 do 24/);
	await step('zalozenie klanu', ala, '/zaloz abc Wielki Klan', /Nowy klan \[ABC\] Wielki Klan, lider: AlaKlan/);
	check('inni widza ogloszenie', !!(await lib.waitMsg(bob, /Nowy klan \[ABC\] Wielki Klan/)));
	await step('tag zajety', bob, '/zaloz ABC Inny Klan', /Tag ABC jest już zajęty/);
	await step('nazwa zajeta', bob, '/zaloz XYZ wielki klan', /Nazwa wielki klan jest już zajęta/);
	await step('drugi klan tego samego gracza', ala, '/zaloz XYZ Inny Klan', /Już należysz do klanu/);
	check('druzyna gry istnieje', /has 1 member/.test(await teamMembers(srv, 'easytp_abc')));

	await step('dolaczenie bez zaproszenia', bob, '/dolacz abc', /Brak zaproszenia do klanu abc/);
	await step('nieznany klan', bob, '/dolacz nie', /Nie ma klanu nie/);
	await step('zaproszenie', ala, '/zapros BobKlan', /Zaproszono gracza BobKlan \(ważne 3 s\)/);
	check('zaproszony widzi powiadomienie', !!(await lib.waitMsg(bob, /Zaproszenie do klanu \[ABC\] Wielki Klan\. Dołącz: \/dolacz ABC/)));
	await sleep(3100);
	await step('zaproszenie wygasa', bob, '/dolacz abc', /Brak zaproszenia do klanu abc/);
	await sleep(1000);
	ala.chat('/zapros BobKlan');
	await sleep(400);
	bob.chat('/dolacz abc');
	check('dolaczenie', !!(await lib.waitMsg(ala, /BobKlan dołącza do klanu/)));
	check('druzyna gry: dwa miejsca', /has 2 member/.test(await teamMembers(srv, 'easytp_abc')));
	await step('zapraszanie samego siebie', ala, '/zapros AlaKlan', /Tego nie można zrobić samemu sobie/);
	await step('czlonek nie zaprasza', bob, '/zapros CezKlan', /Nie masz do tego uprawnień/);
	await step('czlonek nie wyrzuca', bob, '/wyrzuc AlaKlan', /Nie masz do tego uprawnień/);
	await step('zaproszenie klanowicza', ala, '/zapros BobKlan', /należy już do klanu/);

	const from = cez.log.length;
	await sleep(1000);
	ala.chat('/k tajna narada');
	check('czat klanu dochodzi', !!(await lib.waitMsg(bob, /\[Klan\] AlaKlan: tajna narada/)));
	await sleep(500);
	check('czat klanu nie dochodzi do obcych', !cez.log.slice(from).some((m) => /tajna narada/.test(m)));
	const logged = srv.lines.some((l) => /\[ABC\] \[Klan\] AlaKlan: tajna narada/.test(l));
	check('czat klanu w logu serwera', logged);

	await sleep(1000);
	const chatFrom = cez.log.length;
	ala.chat('czesc wszystkim');
	check('tag w zwyklym czacie', !!(await lib.waitMsg(cez, /\[ABC\] AlaKlan.*czesc wszystkim/, 4000, chatFrom)));

	await srv.command('mute AlaKlan test', /Wyciszono gracza AlaKlan/);
	await step('wyciszony nie pisze na /k', ala, '/k halo', /Masz wyciszony czat/);
	await srv.command('unmute AlaKlan', /Odciszono/);

	await srv.command('tp AlaKlan 0 -60 0', /Teleported/);
	await srv.command('tp BobKlan 1.5 -60 0', /Teleported/);
	await srv.command('tp CezKlan 0 -60 1.5', /Teleported/);
	await srv.command('tp DanKlan -1.5 -60 0', /Teleported/);
	await srv.command('give AlaKlan minecraft:netherite_sword 1', /Gave/);
	ala.setQuickBarSlot(0);
	await sleep(1500);
	const friend = ala.nearestEntity((e) => e.username === 'BobKlan');
	const enemy = ala.nearestEntity((e) => e.username === 'CezKlan');
	const stranger = ala.nearestEntity((e) => e.username === 'DanKlan');
	check('boty widza sie nawzajem', !!friend && !!enemy && !!stranger);
	await ala.lookAt(friend.position.offset(0, 1, 0), true);
	await sleep(1000);
	ala.attack(friend);
	await sleep(500);
	check('ogien sojuszniczy wylaczony', await health(srv, 'BobKlan') === 20);
	const blocked = await srv.command('damage BobKlan 5 minecraft:player_attack by AlaKlan', /invulnerable|damage to/);
	check('obrazenia od klanowicza zablokowane', /invulnerable/.test(blocked), blocked);

	await ala.lookAt(stranger.position.offset(0, 1, 0), true);
	await sleep(1000);
	ala.attack(stranger);
	await sleep(500);
	check('atak na obcego dziala', await health(srv, 'DanKlan') < 20);
	let spawned = new Promise((resolve) => dan.once('spawn', resolve));
	let mark = ala.log.length;
	await srv.command('kill DanKlan', /Killed DanKlan/);
	await Promise.race([spawned, sleep(5000)]);
	check('zabojstwo przypisane po ciosie', !!(await lib.waitMsg(ala, /Zabójstwo DanKlan: \+1 serce \(masz 11\)/, 3000, mark)));
	await sleep(1000);

	await ala.lookAt(enemy.position.offset(0, 1, 0), true);
	await sleep(1000);
	ala.attack(enemy);
	await sleep(200);
	ala.chat('/zapros CezKlan');
	await sleep(300);
	cez.chat('/dolacz abc');
	check('cel dolacza do klanu', !!(await lib.waitMsg(ala, /CezKlan dołącza do klanu/)));
	spawned = new Promise((resolve) => cez.once('spawn', resolve));
	mark = ala.log.length;
	await srv.command('kill CezKlan', /Killed .*CezKlan/);
	await Promise.race([spawned, sleep(5000)]);
	check('zabicie klanowicza bez nagrody', !!(await lib.waitMsg(ala, /Bez nagrody za CezKlan: to ktoś z Twojego klanu/, 3000, mark)));
	await step('klanowicz nie stracil serca', cez, '/serca', /Serca: 10 /);
	await step('wyrzucenie po tescie', ala, '/wyrzuc CezKlan', /CezKlan wyrzucony z klanu/);

	await step('zastepca: nie lider', bob, '/zastepca AlaKlan', /Nie masz do tego uprawnień/);
	await step('zastepca: awans', ala, '/zastepca BobKlan', /BobKlan jest teraz zastępcą/);
	await step('zastepca zaprasza', bob, '/klan zapros CezKlan', /Zaproszono gracza CezKlan/);
	await sleep(400);
	cez.chat('/klan dolacz abc');
	check('trzeci czlonek', !!(await lib.waitMsg(ala, /CezKlan dołącza do klanu/)));
	await step('limit: zaproszenie czwartego', ala, '/zapros DanKlan', /Zaproszono gracza DanKlan/);
	await step('limit czlonkow', dan, '/dolacz abc', /Klan jest pełny \(limit: 3\)/);
	await step('info klanu', dan, '/klan info abc', /Klan \[ABC\] Wielki Klan/);
	check('info: lider', !!(await lib.waitMsg(dan, /Lider: AlaKlan/)));
	check('info: zastepcy', !!(await lib.waitMsg(dan, /Zastępcy: BobKlan/)));
	check('info: czlonkowie', !!(await lib.waitMsg(dan, /Członkowie \(3\/3\): AlaKlan, BobKlan, CezKlan/)));
	await step('lista klanow', dan, '/klan lista', /\[ABC\] Wielki Klan: członkowie 3, zabójstwa \d+/);
	await step('topki klany tekstem', dan, '/topki klany', /1\. \[ABC\] Wielki Klan: \d+/);

	const opened = new Promise((resolve) => dan.once('windowOpen', resolve));
	dan.chat('/topki');
	const gui = await opened;
	await sleep(300);
	check('topki: ikona klanow', gui.slots[22] && gui.slots[22].name === 'white_banner');
	const listOpened = new Promise((resolve) => dan.once('windowOpen', resolve));
	dan.clickWindow(22, 0, 0).catch(() => {});
	const list = await listOpened;
	await sleep(300);
	check('topki: klan na liscie', JSON.stringify(list.slots[0]).includes('Wielki Klan'), JSON.stringify(list.slots[0]));
	dan.closeWindow(list);
	await sleep(500);

	await step('zastepca nie wyrzuca lidera', bob, '/wyrzuc AlaKlan', /Nie masz do tego uprawnień/);
	await step('zastepca wyrzuca czlonka', bob, '/wyrzuc CezKlan', /CezKlan wyrzucony z klanu/);
	check('wyrzucony wie', !!(await lib.waitMsg(cez, /Wyrzucono Cię z klanu \[ABC\]/)));
	await sleep(300);
	check('druzyna gry bez wyrzuconego', !/CezKlan/.test(await teamMembers(srv, 'easytp_abc')));
	await step('lider nie odchodzi', ala, '/opusc', /Lider nie może odejść/);
	await step('przekazanie klanu', ala, '/klan lider BobKlan', /Nowy lider klanu: BobKlan/);
	await step('byly lider odchodzi', ala, '/opusc', /AlaKlan opuszcza klan/);
	await step('byly lider bez klanu', ala, '/klan info', /Nie należysz do żadnego klanu/);

	ala.quit();
	bob.quit();
	cez.quit();
	dan.quit();
	await sleep(1000);
	await srv.stop();
	srv.start();
	await srv.waitFor(/Done \(/, 180000);

	const bob2 = await connectBot('BobKlan');
	await step('restart: klan zostaje', bob2, '/klan info', /Klan \[ABC\] Wielki Klan/);
	check('restart: lider', !!(await lib.waitMsg(bob2, /Lider: BobKlan/)));
	check('restart: druzyna gry', /BobKlan/.test(await teamMembers(srv, 'easytp_abc')));

	config({ enabled: false });
	await reloadConfig(srv);
	await sleep(500);
	check('wylaczone klany: druzyna usunieta', /Unknown team/.test(await srv.command('team list easytp_abc', /has \d+ member|has no members|Unknown team/)));
	await step('wylaczone klany: komenda', bob2, '/klan', /Unknown or incomplete command/);
	config();
	await reloadConfig(srv);
	await sleep(500);
	check('wlaczone klany: druzyna wraca', /BobKlan/.test(await teamMembers(srv, 'easytp_abc')));

	await step('rozwiazanie', bob2, '/klan rozwiaz', /Klan \[ABC\] został rozwiązany/);
	check('rozwiazanie: druzyna gry usunieta', /Unknown team/.test(await srv.command('team list easytp_abc', /has \d+ member|has no members|Unknown team/)));
	await step('rozwiazany klan znika', bob2, '/klan info abc', /Nie ma klanu abc/);
	bob2.quit();
	await sleep(800);

	const errors = errorLines(srv);
	check('brak bledow w konsoli', errors.length === 0, errors.slice(0, 8).join('\n'));
}, reportFile);
