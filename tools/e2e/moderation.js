'use strict';
const fs = require('node:fs');
const path = require('node:path');
const lib = require('./lib');

const { reportFile } = lib.parseArgs('moderation.js');
const serverDir = lib.serverDirectory();
const { sleep, check, connectBot, tryJoin, waitEnd, say, expectSay, waitMsg, setConfig, reloadConfig, opPlayer } = lib;

async function collect(bot, command, headerRe, settleMs = 600) {
	const from = bot.log.length;
	bot.chat(command);
	const header = await waitMsg(bot, headerRe, 4000, from);
	await sleep(settleMs);
	return { ok: !!header, all: bot.log.slice(from) };
}

const kickedWith = (reason, ...needles) => needles.every((n) => (reason || '').includes(n));

async function scenario(srv) {
	await srv.waitFor(/Done \(.*\)! For help, type "help"/, 300000);
	check('serwer wystartowal, mod zaladowany z moderacja', srv.lines.some((l) => /EasyTP gotowy.*moderacja/.test(l)));

	const admin = await connectBot('Admin');
	let alice = await connectBot('Alice');
	let bob = await connectBot('Bob');
	await opPlayer(srv, 'Admin');

	for (const cmd of ['/tempban Bob 1m x', '/unban Bob', '/mute Bob x', '/tempmute Bob 1m x', '/unmute Bob', '/mutes', '/warn Bob x', '/warnings Bob', '/clearwarnings Bob', '/history Bob']) {
		const r = await say(alice, cmd, /Unknown or incomplete command|command\.unknown/i);
		check(`zwykly gracz nie ma dostepu: ${cmd}`, r.ok, JSON.stringify(r.all));
		await sleep(700);
	}
	await expectSay('/easytp pokazuje operatorowi komendy moderacji', admin, '/easytp', /Moderacja \(OP\)/);
	{
		const r = await say(alice, '/easytp', /EasyTP \d+\.\d+\.\d+/);
		check('zwykly gracz nie widzi w /easytp linii moderacji', r.ok && !r.all.some((m) => /Moderacja \(OP\)/.test(m)), JSON.stringify(r.all));
	}

	await expectSay('/warn', admin, '/warn Alice Spamowanie czatu', /Dodano ostrzeżenie dla gracza Alice \(łącznie: 1\)\. Powód: Spamowanie czatu/);
	check('ostrzezony gracz dostaje wiadomosc', !!(await waitMsg(alice, /OSTRZEŻENIE od Admin: Spamowanie czatu \(łącznie ostrzeżeń: 1\)/, 3000)));
	await expectSay('/warn drugi raz zwieksza licznik', admin, '/warn Alice CAPS LOCK', /łącznie: 2/);
	{
		const r = await collect(admin, '/warnings Alice', /Ostrzeżenia gracza Alice \(2\):/);
		const text = r.all.join(' | ');
		check('/warnings pokazuje oba ostrzezenia', r.ok && /1\. .*Admin: Spamowanie czatu/.test(text) && /2\. .*Admin: CAPS LOCK/.test(text), text);
	}
	await expectSay('/warn samego siebie jest zablokowane', admin, '/warn Admin test', /Nie możesz ukarać samego siebie/);
	await expectSay('/clearwarnings', admin, '/clearwarnings Alice', /Usunięto ostrzeżenia gracza Alice \(liczba: 2\)/);
	await expectSay('/warnings po wyczyszczeniu', admin, '/warnings Alice', /Gracz Alice nie ma ostrzeżeń/);

	await expectSay('/mute (na stale)', admin, '/mute Bob spam na czacie', /Wyciszono gracza Bob \(na stałe\)\. Powód: spam na czacie/);
	check('wyciszony gracz dostaje powiadomienie', !!(await waitMsg(bob, /Twój czat został wyciszony \(na stałe\)\. Powód: spam na czacie/, 3000)));
	{
		const aliceFrom = alice.log.length;
		const r = await say(bob, 'witam wszystkich', /Masz wyciszony czat \(na stałe\)\. Powód: spam na czacie/);
		check('wyciszony gracz nie moze pisac na czacie (dostaje komunikat)', r.ok, JSON.stringify(r.all));
		await sleep(1200);
		check('wiadomosc wyciszonego NIE dociera do innych', !alice.log.slice(aliceFrom).some((m) => /witam wszystkich/.test(m)), JSON.stringify(alice.log.slice(aliceFrom)));
	}
	{
		const aliceFrom = alice.log.length;
		const r = await say(bob, '/me robi cos glupiego', /Masz wyciszony czat/);
		check('wyciszony gracz nie obejdzie wyciszenia przez /me', r.ok, JSON.stringify(r.all));
		await sleep(800);
		check('/me wyciszonego NIE dociera do innych', !alice.log.slice(aliceFrom).some((m) => /robi cos glupiego/.test(m)), JSON.stringify(alice.log.slice(aliceFrom)));
	}
	for (const cmd of ['/msg Alice psst', '/tell Alice psst2', '/w Alice psst3']) {
		const aliceFrom = alice.log.length;
		const r = await say(bob, cmd, /Masz wyciszony czat/);
		check(`wyciszony gracz nie obejdzie wyciszenia prywatna wiadomoscia: ${cmd}`, r.ok, JSON.stringify(r.all));
		await sleep(600);
		check(`adresat nie dostaje prywatnej wiadomosci (${cmd})`, !alice.log.slice(aliceFrom).some((m) => /psst/.test(m)), JSON.stringify(alice.log.slice(aliceFrom)));
	}
	{
		const r = await collect(admin, '/mutes', /Wyciszeni gracze \(1\):/);
		check('/mutes pokazuje wyciszonego', r.ok && r.all.some((m) => /Bob \(na stałe\), powód: spam na czacie/.test(m)), JSON.stringify(r.all));
	}
	{
		const adminFrom = admin.log.length;
		const bobFrom = bob.log.length;
		alice.chat('hej wszystkim');
		check('czat innych graczy nadal dziala (Admin widzi)', !!(await waitMsg(admin, /<Alice> hej wszystkim/, 3000, adminFrom)));
		check('wyciszony nadal WIDZI czat innych', !!(await waitMsg(bob, /<Alice> hej wszystkim/, 3000, bobFrom)));
	}

	await expectSay('/unmute', admin, '/unmute Bob', /Odciszono gracza Bob\./);
	check('gracz dostaje informacje o odciszeniu', !!(await waitMsg(bob, /Twój czat został odciszony\./, 3000)));
	{
		const aliceFrom = alice.log.length;
		bob.chat('wrocilem');
		check('po odciszeniu czat dziala', !!(await waitMsg(alice, /<Bob> wrocilem/, 3000, aliceFrom)));
		const aliceFrom2 = alice.log.length;
		bob.chat('/msg Alice dziala prywatna');
		check('po odciszeniu /msg dziala', !!(await waitMsg(alice, /dziala prywatna/, 3000, aliceFrom2)));
	}
	await expectSay('/unmute gracza, ktory nie jest wyciszony', admin, '/unmute Bob', /Gracz Bob nie jest wyciszony/);
	await expectSay('/mutes gdy nikt nie jest wyciszony', admin, '/mutes', /Nikt nie jest wyciszony/);

	await expectSay('/tempmute 4s', admin, '/tempmute Bob 4s krotko', /Wyciszono gracza Bob \(4 s\)\. Powód: krotko/);
	await expectSay('wyciszony czasowo nie moze pisac (pokazany pozostaly czas)', bob, 'test', /Masz wyciszony czat \(jeszcze \d+ s\)\. Powód: krotko/);
	check('wyciszenie czasowe samo wygasa, a gracz jest powiadomiony', !!(await waitMsg(bob, /Wyciszenie dobiegło końca, możesz znowu pisać\./, 9000)));
	{
		const aliceFrom = alice.log.length;
		bob.chat('znowu pisze');
		check('po wygasnieciu wyciszenia czat dziala', !!(await waitMsg(alice, /<Bob> znowu pisze/, 3000, aliceFrom)));
	}

	await expectSay('/tempban 1h', admin, '/tempban Bob 1h łamanie regulaminu', /Zbanowano gracza Bob \(1 h\)\. Powód: łamanie regulaminu/);
	check('zbanowany gracz online jest rozlaczony z powodem i data konca bana',
		await waitEnd(bob) && kickedWith(bob.kickReason, 'banned.reason', 'łamanie regulaminu', 'banned.expiration'), bob.kickReason);
	await srv.command('banlist players', /Bob was banned by Admin/, 8000).then(
		(line) => check('ban jest na wbudowanej liscie banow (/banlist)', !!line),
		(e) => check('ban jest na wbudowanej liscie banow (/banlist)', false, e.message));
	{
		const attempt = await tryJoin('Bob');
		check('zbanowany gracz nie wejdzie ponownie (komunikat z powodem)', !attempt.joined && kickedWith(attempt.reason, 'banned.reason', 'łamanie regulaminu'), attempt.reason);
	}
	await expectSay('ponowny /tempban tego samego gracza', admin, '/tempban Bob 1h x', /Gracz Bob ma już bana/);
	await expectSay('/unban', admin, '/unban Bob', /Zdjęto bana z gracza Bob\./);
	{
		const attempt = await tryJoin('Bob');
		check('po /unban gracz wchodzi na serwer', attempt.joined, attempt.reason);
		bob = attempt.bot;
	}
	await expectSay('/unban gracza bez bana', admin, '/unban Bob', /Gracz Bob nie ma bana/);

	await expectSay('/tempban perm (na stale)', admin, '/tempban Bob perm koniec', /Zbanowano gracza Bob \(na stałe\)\. Powód: koniec/);
	check('ban na stale: rozlaczenie z powodem, bez daty konca',
		await waitEnd(bob) && kickedWith(bob.kickReason, 'banned.reason', 'koniec') && !(bob.kickReason || '').includes('banned.expiration'), bob.kickReason);
	{
		const entries = JSON.parse(fs.readFileSync(path.join(serverDir, 'banned-players.json'), 'utf8'));
		const entry = entries.find((e) => e.name === 'Bob');
		check('banned-players.json: wpis "expires": "forever" z powodem i autorem', !!entry && entry.expires === 'forever' && entry.reason === 'koniec' && entry.source === 'Admin', JSON.stringify(entry));
	}
	await expectSay('/unban po banie na stale', admin, '/unban Bob', /Zdjęto bana z gracza Bob/);

	{
		const first = await tryJoin('Bob');
		bob = first.bot;
		check('gracz wchodzi przed krotkim banem', first.joined, first.reason);
		await expectSay('/tempban 3s', admin, '/tempban Bob 3s krotki', /Zbanowano gracza Bob \(3 s\)/);
		await waitEnd(bob);
		await sleep(4500);
		const afterExpiry = await tryJoin('Bob');
		check('po wygasnieciu bana czasowego gracz wchodzi od razu, za pierwszym razem', afterExpiry.joined, afterExpiry.reason);
		bob = afterExpiry.bot;
	}
	for (const offsetMs of [2100, 2400, 2700]) {
		const t0 = Date.now();
		await srv.command('tempban Bob 2s kroteczek', /Banned Bob|Zbanowano gracza Bob/, 8000).catch(() => null);
		await waitEnd(bob);
		await sleep(Math.max(0, offsetMs - (Date.now() - t0)));
		const attempt = await tryJoin('Bob');
		check(`wejscie ${offsetMs} ms po wydaniu bana 2 s (tuz po wygasnieciu)`, attempt.joined, attempt.reason);
		bob = attempt.bot;
		await sleep(600);
	}

	{
		const carol = await connectBot('Carol');
		carol.quit();
		await sleep(1000);
		await expectSay('/tempban gracza, ktory jest offline', admin, '/tempban Carol 2h offline', /Zbanowano gracza Carol \(2 h\)\. Powód: offline/);
		const attempt = await tryJoin('Carol');
		check('zbanowany gracz offline nie wchodzi', !attempt.joined && kickedWith(attempt.reason, 'banned.reason', 'offline'), attempt.reason);
	}

	await expectSay('zly format czasu', admin, '/tempban Bob abc', /Zły czas/);
	await expectSay('zerowy czas', admin, '/tempban Bob 0m', /Zły czas/);
	await expectSay('za dlugi czas (domyslny limit 365 d)', admin, '/tempban Bob 999d', /Za długi czas\. Maksimum to 365 d/);
	await expectSay('ban samego siebie', admin, '/tempban Admin 1h x', /Nie możesz ukarać samego siebie/);
	await expectSay('wyciszenie samego siebie', admin, '/mute Admin x', /Nie możesz ukarać samego siebie/);
	await expectSay('selektor obejmujacy wielu graczy jest odrzucony', admin, '/tempban @a 1h x', /Podaj dokładnie jednego gracza/);

	{
		const r = await collect(admin, '/history Bob', /Historia kar gracza Bob \(wpisy: \d+/);
		const text = r.all.join('\n');
		check('/history Bob: MUTE, UNMUTE, BAN i UNBAN z czasami',
			r.ok && /MUTE \(na stałe\)/.test(text) && /UNMUTE/.test(text) && /MUTE \(4 s\)/.test(text) && /BAN \(1 h\)/.test(text)
			&& /UNBAN/.test(text) && /BAN \(na stałe\)/.test(text) && /BAN \(3 s\)/.test(text), text);
	}
	{
		const r = await collect(admin, '/history Alice', /Historia kar gracza Alice/);
		const text = r.all.join('\n');
		check('/history Alice: WARN i CLEAR_WARNINGS', r.ok && /WARN/.test(text) && /CLEAR_WARNINGS/.test(text), text);
	}
	await expectSay('/history gracza bez wpisow', admin, '/history Admin', /Gracz Admin nie ma wpisów w historii kar/);

	await srv.command('reload', /Reload|reload/i, 30000);
	await sleep(2500);
	await expectSay('/mute po /reload serwera', admin, '/mute Bob po reloadzie', /Wyciszono gracza Bob/);
	{
		const aliceFrom = alice.log.length;
		const r = await say(bob, '/msg Alice po reloadzie', /Masz wyciszony czat/);
		check('po /reload serwera wyciszenie nadal obejmuje /msg', r.ok, JSON.stringify(r.all));
		await sleep(600);
		check('adresat nie dostaje wiadomosci po /reload', !alice.log.slice(aliceFrom).some((m) => /po reloadzie/.test(m)), JSON.stringify(alice.log.slice(aliceFrom)));
	}
	await expectSay('/unmute po testach /reload', admin, '/unmute Bob', /Odciszono gracza Bob/);

	setConfig({ maxPunishmentDays: 1 });
	await reloadConfig(srv);
	await expectSay('limit czasu z konfiguracji (1 d)', admin, '/tempban Bob 2d x', /Za długi czas\. Maksimum to 1 d/);
	setConfig({ language: 'en' });
	await reloadConfig(srv);
	await expectSay('komunikaty po angielsku', admin, '/tempban Bob abc', /Invalid duration/);
	setConfig({});
	await reloadConfig(srv);

	await expectSay('/mute (na stale) przed restartem', admin, '/mute Alice po restarcie', /Wyciszono gracza Alice/);
	await expectSay('/warn przed restartem', admin, '/warn Bob zostaje w pliku', /Dodano ostrzeżenie dla gracza Bob \(łącznie: 1\)/);
	admin.quit();
	alice.quit();
	bob.quit();
	await sleep(1000);
	await srv.stop();
	const moderationFile = path.join(serverDir, 'world', 'easytp', 'moderation.json');
	check('plik moderation.json zapisany na dysku', fs.existsSync(moderationFile));
	if (fs.existsSync(moderationFile)) {
		const data = JSON.parse(fs.readFileSync(moderationFile, 'utf8'));
		check('moderation.json zawiera wyciszenie, ostrzezenie i historie',
			data.mutes.some((m) => m.name === 'Alice' && m.expiresAt === 0) && Object.keys(data.warnings).length === 1 && data.history.length >= 10,
			JSON.stringify(data).slice(0, 300));
	}

	srv.start();
	await srv.waitFor(/Done \(.*\)! For help, type "help"/, 300000);
	const admin2 = await connectBot('Admin');
	const alice2 = await connectBot('Alice');
	await expectSay('wyciszenie przetrwalo restart serwera', alice2, 'czy mnie slychac', /Masz wyciszony czat \(na stałe\)\. Powód: po restarcie/);
	await expectSay('ostrzezenia przetrwaly restart serwera', admin2, '/warnings Bob', /Ostrzeżenia gracza Bob \(1\):/);
	{
		const attempt = await tryJoin('Carol');
		check('ban przetrwal restart serwera', !attempt.joined && kickedWith(attempt.reason, 'banned.reason', 'offline'), attempt.reason);
	}
	await expectSay('/unmute po restarcie', admin2, '/unmute Alice', /Odciszono gracza Alice/);
	admin2.quit();
	alice2.quit();
	await sleep(800);

	const errors = lib.errorLines(srv);
	check('brak bledow w logu serwera', errors.length === 0, errors.slice(0, 6).join('\n        '));
}

lib.runMain(scenario, reportFile);
