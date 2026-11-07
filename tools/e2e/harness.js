'use strict';
const fs = require('node:fs');
const path = require('node:path');
const lib = require('./lib');

const { reportFile } = lib.parseArgs('harness.js');
const serverDir = lib.serverDirectory();
const { sleep, check, connectBot, say, expectSay, expectSilence, waitMsg, near, fmt, waitNear, consoleTp, setConfig, reloadConfig, angleDiff, COOLDOWN_WAIT } = lib;

async function scenario(srv) {
	await srv.waitFor(/Done \(.*\)! For help, type "help"/, 300000);
	check('serwer wystartowal', true);
	check('mod EasyTP zaladowany (log)', srv.lines.some((l) => /EasyTP gotowy/.test(l)));
	check('brak bledow ladowania modow w logu', !srv.lines.some((l) => /Mod resolution failed|Incompatible mod set|Failed to load mod/i.test(l)));

	let alice = await connectBot('Alice');
	const bob = await connectBot('Bob');
	check('boty (czysty klient bez moda) weszly na serwer', alice.entity && bob.entity);

	await srv.command('setworldspawn 0 -60 0', /Set the world spawn point/i);
	await srv.command('gamemode survival Alice');
	await srv.command('gamemode survival Bob');

	await expectSay('/homes bez domow -> komunikat', alice, '/homes', /Nie masz jeszcze żadnego domu/);
	await consoleTp(srv, alice, 10, -60, 10);
	await expectSay('/sethome (domyslnie "home")', alice, '/sethome', /Ustawiono dom 'home'/);
	await consoleTp(srv, alice, 20, -60, 20);
	await alice.look(1.0, 0.2, true);
	await sleep(300);
	const savedYaw = alice.entity.yaw;
	await expectSay('/sethome base', alice, '/sethome base', /Ustawiono dom 'base'/);
	await consoleTp(srv, alice, 30, -60, 30);
	await expectSay('/sethome mine', alice, '/sethome mine', /Ustawiono dom 'mine'/);
	await expectSay('limit domow (3)', alice, '/sethome extra', /Osiągnięto limit domów \(3\)/);
	await expectSay('nadpisanie domu (wielkosc liter bez znaczenia)', alice, '/sethome BASE', /Zaktualizowano dom 'base'/);
	await expectSay('zla nazwa domu', alice, '/sethome a.b', /Nazwa domu: 1-16 znaków/);
	{
		const r = await say(alice, '/homes', /Twoje domy \(3\/3\)/);
		const joined = r.all.join(' | ');
		check('/homes pokazuje 3 domy ze wspolrzednymi',
			r.ok && /base.*Overworld \(30, -60, 30\)/.test(joined) && /home.*Overworld \(10, -60, 10\)/.test(joined) && /mine.*Overworld \(30, -60, 30\)/.test(joined),
			joined);
		const raw = alice.raw.join('\n');
		check('/homes ma klikalne nazwy (run_command /home ...)', /home base/.test(raw) && /run_command/i.test(raw), raw.slice(-400));
	}

	await consoleTp(srv, alice, 100, -60, 100);
	await alice.look(2.5, 0, true);
	await sleep(300);
	await expectSay('/home base -> komunikat', alice, '/home base', /Teleportowano do domu 'base'/);
	check('/home base -> pozycja = zapisana', await waitNear(alice, 30, -60, 30, 0.8), `jest ${fmt(alice)}`);
	check('/home base przywraca kierunek patrzenia', angleDiff(alice.entity.yaw, savedYaw) < 0.1, `yaw=${alice.entity.yaw} oczekiwano ${savedYaw}`);

	await expectSay('przerwa miedzy teleportacjami (cooldown)', alice, '/home mine', /Poczekaj jeszcze \d+ s/);
	await sleep(COOLDOWN_WAIT);
	await expectSay('/home mine po przerwie', alice, '/home mine', /Teleportowano do domu 'mine'/);
	await sleep(COOLDOWN_WAIT);
	await expectSay('/home bez nazwy -> dom "home"', alice, '/home', /Teleportowano do domu 'home'/);
	check('/home bez nazwy -> pozycja domu "home"', await waitNear(alice, 10, -60, 10, 0.8), `jest ${fmt(alice)}`);
	await expectSay('/home nieistniejacy', alice, '/home nope', /Nie masz domu o nazwie 'nope'/);
	await expectSay('/delhome mine', alice, '/delhome mine', /Usunięto dom 'mine'/);
	await expectSay('/delhome drugi raz', alice, '/delhome mine', /Nie masz domu o nazwie 'mine'/);

	try {
		const raw = await alice.tabComplete('/home ', true, false, 4000);
		const names = (raw || []).map((m) => (typeof m === 'string' ? m : (m.match ?? m.text ?? JSON.stringify(m))));
		check('tab-complete /home podpowiada domy', names.includes('base') && names.includes('home'), JSON.stringify(names));
	} catch (e) {
		check('tab-complete /home podpowiada domy', false, e.message);
	}

	await sleep(COOLDOWN_WAIT);
	await consoleTp(srv, alice, 40, -60, 40);
	await consoleTp(srv, bob, -40, -60, -40);
	{
		const bobFrom = bob.log.length;
		await expectSay('/tpa -> potwierdzenie dla nadawcy', alice, '/tpa Bob', /Wysłano prośbę o teleportację do gracza Bob \(wygasa za 5 s\)/);
		const m = await waitMsg(bob, /Alice chce się do Ciebie teleportować/, 3000, bobFrom);
		check('/tpa -> adresat dostaje prosbe', !!m);
		const raw = bob.raw.slice(-5).join('\n');
		check('prosba ma klikalne [Akceptuj] i [Odrzuc]', /tpaccept Alice/.test(raw) && /tpdeny Alice/.test(raw), raw.slice(-500));
		const aliceFrom = alice.log.length;
		await expectSay('/tpaccept -> potwierdzenie', bob, '/tpaccept', /Zaakceptowano prośbę gracza Alice/);
		check('nadawca dostaje informacje o akceptacji', !!(await waitMsg(alice, /Prośba została zaakceptowana przez Bob/, 3000, aliceFrom)));
		check('/tpa: Alice poleciala do Boba', await waitNear(alice, -40, -60, -40, 0.8), `Alice ${fmt(alice)}`);
	}

	await sleep(COOLDOWN_WAIT + 800);
	await consoleTp(srv, alice, 40, -60, 40);
	{
		const aliceFrom = alice.log.length;
		await expectSay('/tpahere -> potwierdzenie dla nadawcy', bob, '/tpahere Alice', /Wysłano graczowi Alice prośbę o teleportację do Ciebie/);
		check('/tpahere -> adresat dostaje prosbe', !!(await waitMsg(alice, /Bob prosi o Twoją teleportację do siebie/, 3000, aliceFrom)));
		await expectSay('/tpaccept Bob (po nazwie)', alice, '/tpaccept Bob', /Zaakceptowano prośbę gracza Bob/);
		check('/tpahere: Alice poleciala do Boba (zgodnie z TPAHERE)', await waitNear(alice, -40, -60, -40, 0.8), `Alice ${fmt(alice)}`);
	}

	await sleep(3200);
	{
		const aliceFrom = alice.log.length;
		await expectSay('/tpa przed odrzuceniem', alice, '/tpa Bob', /Wysłano prośbę/);
		await expectSay('/tpdeny', bob, '/tpdeny Alice', /Odrzucono prośbę gracza Alice/);
		check('nadawca dostaje informacje o odrzuceniu', !!(await waitMsg(alice, /Prośba została odrzucona przez Bob/, 3000, aliceFrom)));
		await expectSay('/tpaccept po odrzuceniu -> brak prosb', bob, '/tpaccept', /Nie masz oczekujących próśb o teleportację/);
	}
	await sleep(3200);
	{
		const bobFrom = bob.log.length;
		await expectSay('/tpa przed anulowaniem', alice, '/tpa Bob', /Wysłano prośbę/);
		await expectSay('/tpcancel', alice, '/tpcancel', /Anulowano prośbę do gracza Bob/);
		check('adresat dostaje informacje o anulowaniu', !!(await waitMsg(bob, /Prośba od Alice została anulowana/, 3000, bobFrom)));
		await expectSay('/tpcancel bez prosby', alice, '/tpcancel', /Nie masz wysłanej prośby do anulowania/);
	}
	await sleep(3200);
	{
		const aliceFrom = alice.log.length;
		await expectSay('/tpa przed wygasnieciem', alice, '/tpa Bob', /Wysłano prośbę/);
		check('prosba wygasa po czasie (5 s) i nadawca jest powiadomiony', !!(await waitMsg(alice, /Twoja prośba do gracza Bob wygasła/, 9000, aliceFrom)));
		await expectSay('/tpaccept po wygasnieciu -> brak prosb', bob, '/tpaccept', /Nie masz oczekujących próśb/);
	}
	await expectSay('/tpa do samego siebie', alice, '/tpa Alice', /Nie możesz wysłać prośby do samego siebie/);
	await sleep(3200);
	await expectSay('/tpa (1)', alice, '/tpa Bob', /Wysłano prośbę/);
	await expectSay('ochrona przed spamem prosb', alice, '/tpa Bob', /Poczekaj chwilę przed wysłaniem kolejnej prośby/);
	await sleep(3200);
	await expectSay('/tpaccept z nieistniejacym nadawca', bob, '/tpaccept Nikt', /Brak aktywnej prośby od gracza Nikt/);

	await expectSay('/tpa tuz przed wyjsciem', alice, '/tpa Bob', /Wysłano prośbę/);
	alice.quit();
	await sleep(1500);
	await expectSay('po wyjsciu nadawcy prosba znika', bob, '/tpaccept', /Nie masz oczekujących próśb/);
	alice = await connectBot('Alice');

	await sleep(COOLDOWN_WAIT);
	await consoleTp(srv, alice, 60, -60, 60);
	await expectSay('/spawn (swiat: 0 -60 0)', alice, '/spawn', /Teleportowano na spawn/);
	check('/spawn -> pozycja punktu odradzania swiata', await waitNear(alice, 0.5, -60, 0.5, 1.0), `Alice ${fmt(alice)}`);
	await expectSay('/setspawn jest niedostepny dla zwyklego gracza', alice, '/setspawn', /Unknown or incomplete command|command\.unknown/i);
	await srv.command('op Alice', /Made Alice a server operator/);
	await sleep(600);
	await consoleTp(srv, alice, 50, -60, -50);
	await expectSay('/setspawn (operator)', alice, '/setspawn', /Ustawiono spawn serwera/);
	await consoleTp(srv, bob, -20, -60, 20);
	await expectSay('/spawn po /setspawn', bob, '/spawn', /Teleportowano na spawn/);
	check('/spawn -> pozycja ustawiona przez /setspawn', await waitNear(bob, 50, -60, -50, 1.0), `Bob ${fmt(bob)}`);
	await srv.command('deop Alice', /Made Alice no longer a server operator/);
	await sleep(600);

	await consoleTp(srv, alice, 60, -60, 60);
	await expectSay('/delhome base (zwolnienie miejsca)', alice, '/delhome base', /Usunięto dom 'base'/);
	await expectSay('/sethome lava', alice, '/sethome lava', /Ustawiono dom 'lava'/);
	await srv.command('forceload add 60 60', /force loaded|Marked/i);
	await consoleTp(srv, alice, 5, -60, 5);
	await srv.command('setblock 60 -60 60 minecraft:lava', /Changed the block/);
	await sleep(COOLDOWN_WAIT);
	await expectSay('/home w miejscu z lawa -> odmowa', alice, '/home lava', /Miejsce docelowe jest niebezpieczne lub zablokowane/);
	check('po odmowie gracz zostaje na miejscu', near(alice, 5, -60, 5, 1.0), `Alice ${fmt(alice)}`);
	await srv.command('setblock 60 -60 60 minecraft:air', /Changed the block/);

	await srv.command('forceload add 10 10', /force loaded|Marked/i);
	await srv.command('fill 10 -60 10 10 -59 10 minecraft:stone', /Successfully filled|No blocks were filled/i);
	await sleep(COOLDOWN_WAIT);
	await expectSay('/home zablokowany kamieniem -> znajduje miejsce wyzej', alice, '/home home', /Teleportowano do domu 'home'/);
	check('gracz stoi na szczycie kamiennego slupka (y=-58)', await waitNear(alice, 10.5, -58, 10.5, 0.3), `Alice ${fmt(alice)}`);
	await srv.command('fill 10 -60 10 10 -59 10 minecraft:air', /Successfully filled|No blocks were filled/i);

	await expectSay('/delhome lava', alice, '/delhome lava', /Usunięto dom 'lava'/);
	await srv.command('execute in minecraft:the_nether run forceload add 0 0', /force loaded|Marked/i, 20000);
	await sleep(6000);
	await srv.command('execute in minecraft:the_nether run fill -2 99 -2 2 99 2 minecraft:stone', /Successfully filled|No blocks were filled/i, 20000);
	await srv.command('execute in minecraft:the_nether run fill -2 100 -2 2 104 2 minecraft:air', /Successfully filled|No blocks were filled/i, 20000);
	await consoleTp(srv, alice, 0.5, 100, 0.5, 'minecraft:the_nether');
	await expectSay('/sethome nether (w Netherze)', alice, '/sethome nether', /Ustawiono dom 'nether'/);
	await expectSay('/homes pokazuje wymiar Nether', alice, '/homes', /nether.*Nether \(0, 100, 0\)/);
	await consoleTp(srv, alice, 5, -60, 5, 'minecraft:overworld');
	await sleep(COOLDOWN_WAIT);
	await expectSay('/home nether -> z innego wymiaru', alice, '/home nether', /Teleportowano do domu 'nether'/);
	check('/home nether -> pozycja w Netherze', await waitNear(alice, 0.5, 100, 0.5, 1.0, 8000), `Alice ${fmt(alice)}`);
	await consoleTp(srv, alice, 5, -60, 5, 'minecraft:overworld');

	setConfig({ warmupSeconds: 3, cooldownSeconds: 0 });
	await reloadConfig(srv);
	await sleep(COOLDOWN_WAIT);
	{
		const from = alice.log.length;
		alice.chat('/home home');
		check('warmup: komunikat o odliczaniu', !!(await waitMsg(alice, /Teleportacja za 3 s/, 3000, from)));
		await sleep(1200);
		check('warmup: przed uplywem czasu gracz stoi w miejscu', near(alice, 5, -60, 5, 1.0), `Alice ${fmt(alice)}`);
		check('warmup: po 3 s nastepuje teleportacja', await waitNear(alice, 10, -60, 10, 1.0, 5000), `Alice ${fmt(alice)}`);
	}
	await consoleTp(srv, alice, 5, -60, 5);
	{
		const from = alice.log.length;
		alice.chat('/home home');
		await waitMsg(alice, /Teleportacja za 3 s/, 3000, from);
		alice.setControlState('forward', true);
		const cancelled = await waitMsg(alice, /Teleportacja anulowana \(ruch\)/, 4000, from);
		alice.setControlState('forward', false);
		check('warmup: ruch anuluje teleportacje', !!cancelled);
		await sleep(3500);
		check('warmup: po anulowaniu gracz NIE zostal teleportowany', !near(alice, 10, -60, 10, 1.5), `Alice ${fmt(alice)}`);
	}
	await consoleTp(srv, alice, 5, -60, 5);
	{
		const from = alice.log.length;
		alice.chat('/home home');
		await waitMsg(alice, /Teleportacja za 3 s/, 3000, from);
		await expectSay('warmup: druga teleportacja w trakcie odliczania jest odrzucona', alice, '/spawn', /Teleportacja już trwa/);
		await srv.command('damage Alice 1', /Applied .* damage to Alice/i);
		check('warmup: obrazenia anuluja teleportacje', !!(await waitMsg(alice, /Teleportacja anulowana \(obrażenia\)/, 3000, from)));
		await sleep(3500);
		check('warmup: po obrazeniach gracz NIE zostal teleportowany', !near(alice, 10.5, -60, 10.5, 1.5), `Alice ${fmt(alice)}`);
	}

	await srv.command('op Alice', /Made Alice a server operator/);
	await sleep(600);
	await consoleTp(srv, alice, 5, -60, 5);
	{
		const from = alice.log.length;
		alice.chat('/home home');
		const msg = await waitMsg(alice, /Teleportowano do domu 'home'/, 1500, from);
		check('operator pomija czas oczekiwania (opsBypassLimits)', !!msg, JSON.stringify(alice.log.slice(from)));
	}
	await srv.command('deop Alice', /no longer a server operator/);

	setConfig({ language: 'en', cooldownSeconds: 0 });
	await reloadConfig(srv);
	await expectSay('jezyk angielski po przeladowaniu', alice, '/homes', /Your homes \(/);
	setConfig({});
	await reloadConfig(srv);
	await expectSay('powrot do jezyka polskiego', alice, '/homes', /Twoje domy \(/);

	await srv.command('reload', /Reload|reload/i, 30000);
	await sleep(2500);
	await expectSay('komendy dzialaja po /reload', alice, '/homes', /Twoje domy \(/);
	alice.quit();
	bob.quit();
	await sleep(1000);
	await srv.stop();
	const dataFile = path.join(serverDir, 'world', 'easytp', 'data.json');
	check('plik danych zapisany na dysku', fs.existsSync(dataFile));
	if (fs.existsSync(dataFile)) {
		const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
		const homes = Object.values(data.homes || {})[0] || {};
		check('plik danych zawiera domy i spawn', Object.keys(homes).includes('home') && !!data.spawn, JSON.stringify(data).slice(0, 300));
	}

	srv.start();
	await srv.waitFor(/Done \(.*\)! For help, type "help"/, 300000);
	const alice2 = await connectBot('Alice');
	await expectSay('po restarcie serwera domy sa wczytane', alice2, '/homes', /Twoje domy \(\d\/3\)/);
	await lib.sleep(500);
	const joined = alice2.log.join(' | ');
	check('po restarcie lista zawiera dom "home" i "nether"', /home/.test(joined) && /nether/.test(joined), joined);
	alice2.quit();
	await sleep(800);

	const errors = lib.errorLines(srv);
	check('brak bledow w logu serwera', errors.length === 0, errors.slice(0, 5).join('\n        '));
}

lib.runMain(scenario, reportFile);
