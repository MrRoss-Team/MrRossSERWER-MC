# EasyTP: /home, /tpa, /spawn, moderacja, kopie zapasowe i tryby gry (serca, ekonomia, klany, Duels, OneBlock, BoxPvP) dla Minecrafta 1.21.11 (Fabric)

Mod **tylko po stronie serwera**. Gracze **nie instalują nic**: wchodzą zwykłym Minecraftem 1.21.11.

## Komendy

| Komenda | Co robi |
|---|---|
| `/sethome [nazwa]` | Zapisuje dom w miejscu, w którym stoisz (wymiar, pozycja i kierunek patrzenia). Bez nazwy: dom `home`. Istniejący dom jest nadpisywany. |
| `/home [nazwa]` | Teleportuje do domu. Bez nazwy: dom `home` albo jedyny, który masz. |
| `/delhome [nazwa]` | Usuwa dom (bez nazwy: `home`). |
| `/homes` | Lista domów ze współrzędnymi. Nazwy są klikalne. |
| `/tpa <gracz>` | Prosi wskazanego gracza o zgodę na teleportację do jego miejsca. |
| `/tpahere <gracz>` | Prosi gracza o teleportację do Ciebie. |
| `/tpaccept [gracz]` | Akceptuje prośbę (bez nazwy: najnowszą). Można też kliknąć **[Akceptuj]** w czacie. |
| `/tpdeny [gracz]` | Odrzuca prośbę (albo kliknięcie **[Odrzuć]**). |
| `/tpcancel` | Anuluje wysłaną przez Ciebie prośbę. |
| `/spawn` | Teleportuje na spawn serwera. |
| `/setspawn` | **Tylko OP.** Ustawia spawn serwera w Twoim miejscu. Bez tego `/spawn` używa punktu z `/setworldspawn`. |
| `/easytp` | Informacja o modzie. `/easytp reload` (OP) wczytuje ponownie konfigurację. `/easytp backup` i `/easytp backup list` (OP) opisano niżej, w sekcji o kopiach zapasowych. |

Nazwy domów: 1-16 znaków (litery bez polskich znaków, cyfry, `_`, `-`), wielkość liter nie ma znaczenia.

Przed teleportacją do domu lub na spawn mod sprawdza, czy miejsce nie jest zablokowane ani groźne (lawa, ogień, kaktus, pustka). W razie potrzeby szuka wolnego miejsca do 4 bloków wyżej lub niżej, a gdy go nie ma, odmawia. Teleportacja do gracza (`/tpa`) ląduje dokładnie w jego miejscu.

## Moderacja (tylko dla operatorów)

Minecraft ma już `/ban`, `/ban-ip`, `/pardon`, `/pardon-ip`, `/kick` i `/banlist` (dla operatorów) i one dalej działają. EasyTP dodaje to, czego brakuje:

| Komenda | Co robi |
|---|---|
| `/tempban <gracz> <czas> [powód]` | Ban na czas (`30m`, `2h`, `1d`, `1w`, `1d12h`) albo na stałe (`perm`). Gracz online jest od razu wyrzucany z powodem i datą końca bana. Ban trafia na wbudowaną listę banów, więc widać go w `/banlist`, a zdjąć można też przez `/pardon`. |
| `/unban <gracz>` | Zdejmuje bana (podpowiada zbanowanych). |
| `/mute <gracz> [powód]` | Wycisza na stałe: gracz nie może pisać na czacie, używać `/me` ani wiadomości prywatnych (`/msg`, `/tell`, `/w`, `/teammsg`), ale widzi czat innych. |
| `/tempmute <gracz> <czas> [powód]` | Wyciszenie na czas. Samo wygasa, a gracz dostaje informację. |
| `/unmute <gracz>` | Odcisza. |
| `/mutes` | Lista wyciszonych z pozostałym czasem. |
| `/warn <gracz> <powód>` | Zapisuje ostrzeżenie i pokazuje je graczowi. |
| `/warnings <gracz>` | Lista ostrzeżeń gracza. |
| `/clearwarnings <gracz>` | Kasuje ostrzeżenia gracza. |
| `/history <gracz>` | Historia kar nałożonych komendami EasyTP (ostatnie 20 wpisów). |

- Dostęp mają operatorzy na poziomie takim jak `/ban` i `/kick` (poziom 3 lub wyższy; domyślny poziom po `/op` to 4) oraz konsola serwera. Zwykli gracze tych komend nie widzą.
- Karać można też graczy, którzy są offline (po nicku). Nie można ukarać samego siebie ani kilku osób naraz (selektory typu `@a` są odrzucane).
- Czas to liczba z jednostką: `s`, `m`, `h`, `d`, `w`, także połączona, np. `1d12h`. Najdłuższy czas ustawia `maxPunishmentDays` (domyślnie 365 dni); `perm` jest zawsze dozwolone.
- Wyciszenie obejmuje czat, `/me`, `/say` i wiadomości prywatne, ale nie napisy na tabliczkach ani w książkach.
- Wyciszenia, ostrzeżenia i historia są w `<świat>/easytp/moderation.json`, a bany w `banned-players.json` serwera. Historia obejmuje tylko komendy EasyTP (nie `/ban` i `/kick` z Minecrafta).

## Kopie zapasowe świata (tylko dla operatorów)

Mod sam robi kopie zapasowe świata **bez zatrzymywania serwera** i bez wyrzucania graczy. Działa tak, jak ręcznie `/save-off`, `/save-all flush`, pakowanie plików i `/save-on`: zapis świata jest wyłączony tylko na czas pakowania (zwykle kilka sekund), a samo pakowanie dzieje się w tle.

- Kopia powstaje najwcześniej 2 minuty po starcie serwera, a potem co `backupIntervalMinutes` (domyślnie 180 min), **ale tylko gdy świat zmienił się od poprzedniej kopii**. Pusty serwer nie produkuje więc niepotrzebnych plików. Zegar działa także wtedy, gdy serwer jest wstrzymany z powodu braku graczy.
- Kopie trafiają do folderu `backups` w folderze serwera (`backupDir`; może to być też inny dysk albo folder OneDrive, bo gotowy ZIP już się nie zmienia). Nazwa: `world-20261019-130000.zip`. Zostaje `backupKeep` najnowszych kopii (domyślnie 24), starsze są usuwane. Gdy na dysku brakuje miejsca (rozmiar świata plus 256 MB zapasu), kopia jest pomijana, a w logu serwera pojawia się ostrzeżenie.
- Komendy (operatorzy poziomu 3 lub wyższego i konsola serwera): `/easytp backup` robi kopię od razu, także gdy nic się nie zmieniło (działa też przy `backupEnabled: false`), a `/easytp backup list` pokazuje 10 najnowszych kopii z rozmiarem i wiekiem.
- Kopia jest kompletna albo jej nie ma: ZIP powstaje jako `*.zip.tmp`, jest sprawdzany i dopiero potem dostaje właściwą nazwę. Niedokończone pliki po awarii są sprzątane przy starcie serwera. Pomijany jest tylko `session.lock` (zablokowany przez serwer i niepotrzebny).
- Kopia obejmuje świat razem z danymi EasyTP (`world/easytp/`: domy, spawn, moderacja), ale **nie** `whitelist.json`, `ops.json`, banów serwera ani `server.properties`. To małe pliki, łatwo skopiować je ręcznie.

**Przywracanie:** wpisz `stop`, zmień nazwę folderu `world` na `world-stary`, rozpakuj wybrany ZIP do folderu serwera (w środku jest folder `world`; w Eksploratorze: prawy przycisk, „Wypakuj wszystko…”, a jako folder docelowy wskaż folder serwera) i uruchom serwer.

## Serca (LifeSteal)

Zabójstwo gracza daje **+1 serce**, a ofiara traci jedno. Gracz zaczyna z 10 sercami, limit to 30, minimum to 1 (nikt nie wypada z gry). Liczba serc to prawdziwe maksymalne zdrowie, widoczne na pasku w zwykłym kliencie.

| Komenda | Działanie |
|---|---|
| `/serca [gracz]` | ile serc ma gracz i jaki ma limit |
| `/wyplacserce [ile]` | zamienia serca na przedmioty; kliknięcie przedmiotu prawym przyciskiem dodaje serce z powrotem |
| `/sklepzaserca` | okno sklepu: Legendarne serce (limit +10, raz na gracza) i Legendarny set (netherytowa zbroja: Ochrona V, Niezniszczalność III), płatne sercami-przedmiotami z ekwipunku |
| `/serca ustaw <gracz> <ile>` | ustawia serca (OP poziomu 2 lub konsola) |

- Gdy zabójca ma limit serc, serce trafia do jego ekwipunku jako przedmiot.
- Rozbity kamień lub łupek daje serce z szansą 0,01%.
- Zabójstwo nie daje nagrody, gdy: ofiara ma to samo IP, gra krócej niż 30 minut, ten sam gracz zabił ją w ostatnich 30 minutach, niedawno zginęła (3 minuty) albo zabójca przekroczył 15 nagrodzonych zabójstw na godzinę. Zabójca widzi powód na czacie.
- Przez tunel Playit serwer widzi wszystkich graczy pod adresem lokalnym `127.0.0.1`, dlatego adres lokalny domyślnie nie liczy się jako „to samo IP” (`ignoreLocalIp`). Za tunelem ta jedna zasada więc nie działa, pozostałe tak.

## Ekonomia

| Komenda | Działanie |
|---|---|
| `/stan [gracz]` | stan konta |
| `/pay <gracz> <kwota>`, `/przelej ...` | przelew (także do gracza offline, który jest znany serwerowi) |
| `/baltop` | 10 najbogatszych graczy |
| `/sprzedaj`, `/sprzedaj wszystko` | sprzedaż przedmiotu z ręki albo całego ekwipunku (bez zbroi i bez przedmiotów nazwanych lub zaklętych) według cennika `sellPrices` |
| `/sklep` | okno sklepu z ofertami z konfiguracji (`shop`) |
| `/rynek` | okno rynku graczy: kliknięcie kupuje ofertę, a kliknięcie własnej ją zdejmuje |
| `/rynek wystaw <cena>` | wystawia przedmiot trzymany w ręce (do 10 ofert na gracza, pieniądze trafiają też do sprzedawcy offline) |
| `/eco daj\|zabierz\|ustaw <gracz> <kwota>` | zarządzanie pieniędzmi (OP poziomu 2 lub konsola) |

Za nagrodzone zabójstwo zabójca dostaje `killReward` (50 $) oraz `killTheftPercent` (10%) pieniędzy ofiary.

## Topki i statystyki

- `/topki` otwiera okno z kategoriami: zabójstwa, serca, pieniądze, czas gry, wykopany kamień, śmierci. `/topki <kategoria>` (`zabojstwa`, `serca`, `pieniadze`, `czas`, `kamien`, `smierci`) pokazuje 10 najlepszych na czacie i działa też z konsoli.
- `/statystyki [gracz]` pokazuje serca, pieniądze, K/D, czas gry oraz wykopane i postawione bloki.
- Liczby pochodzą ze statystyk gry i są odświeżane co minutę oraz przy wejściu i wyjściu. Gracz, który grał przed wersją 1.3, pojawi się w topkach po najbliższym wejściu na serwer.

## Klany

Tag klanu widać przed nickiem na czacie, na liście graczy i nad głową. Członkowie klanu nie mogą się nawzajem ranić, a zabicie kogoś z własnego klanu nie daje serca ani pieniędzy.

| Komenda | Kto | Działanie |
|---|---|---|
| `/zaloz <tag> <nazwa>` | każdy | zakłada klan: tag 2-5 liter lub cyfr, nazwa 3-24 znaki |
| `/zapros <gracz>` | lider, zastępca | zaproszenie ważne 5 minut |
| `/dolacz <tag>` | zaproszony | dołącza do klanu |
| `/wyrzuc <gracz>` | lider, zastępca | wyrzuca członka; zastępca nie wyrzuci lidera ani innego zastępcy |
| `/zastepca <gracz>` | lider | nadaje albo zabiera rangę zastępcy |
| `/opusc` | członek | opuszcza klan (lider musi najpierw przekazać klan) |
| `/klan lider <gracz>` | lider | przekazuje klan |
| `/klan rozwiaz` | lider | rozwiązuje klan |
| `/klan`, `/klan info [tag]`, `/klan lista` | każdy | pomoc, dane klanu, lista klanów |
| `/k <wiadomość>` | członek | czat klanu (działa też blokada z `/mute`) |

Wszystkie komendy działają też jako `/klan zaloz ...`, `/klan zapros ...` itd. `/topki klany` oraz ikona w `/topki` pokazują ranking klanów według zabójstw członków.

## Osobne tryby gry

Duels, OneBlock i BoxPvP działają na tym samym serwerze, każdy w swoim świecie (`easytp:duels`, `easytp:oneblock`, `easytp:boxpvp`). Gracze wchodzą zwykłym klientem, bez instalowania czegokolwiek.

- **Ekwipunek:** świat główny (razem z Netherem i Endem), OneBlock i BoxPvP mają osobne ekwipunki, skrzynie Endu, doświadczenie, efekty i jedzenie. Przy przejściu między trybami, także przez `/spawn` albo `/home`, stan jest zapisywany w `<świat>/easytp/profiles/`, a przedmioty nie przechodzą między trybami.
- **Serca** działają tylko w świecie głównym. W pozostałych trybach każdy ma zwykłe 10 serc.
- **Pieniądze, klany i statystyki** są wspólne dla wszystkich trybów, także zarobek z kopalni BoxPvP.
- Punkt odrodzenia z łóżka jest jeden na gracza. Śmierć na wyspie albo w BoxPvP zawsze przenosi do tego trybu, a śmierć w świecie głównym nie korzysta z łóżka postawionego na wyspie.

## Duels

Pojedynki 1 na 1 na arenach tworzonych automatycznie. Ekwipunek, pozycja, serca i doświadczenie są zapisywane przed walką i wracają po niej, także po wyjściu z gry i po awarii serwera (przy następnym wejściu). Śmierć w pojedynku nie jest prawdziwą śmiercią i nie zabiera serc.

| Komenda | Działanie |
|---|---|
| `/duel <gracz> [zestaw] [rundy]` | wyzwanie, bez wpływu na ELO; `rundy` to 1, 3, 5, ... (do większości wygranych) |
| `/duel akceptuj <gracz>`, `/duel odrzuc <gracz>` | odpowiedź na wyzwanie (działają też przyciski na czacie) |
| `/duel toggle` | włącza albo wyłącza przyjmowanie wyzwań |
| `/ranked [zestaw]` (`/queue`, `/kolejka`) | kolejka rankingowa: przeciwnik o zbliżonym ELO, zakres rośnie z czasem oczekiwania |
| `/queue leave` | wyjście z kolejki |
| `/ff` (`/surrender`, `/poddaj`) | poddanie walki |
| `/kity`, `/previewkit <zestaw>` | lista zestawów i podgląd w oknie |
| `/elo [gracz]`, `/dywizje` (`/ranks`) | ELO, wygrane, przegrane i progi dywizji |

- Zestawy: `standard`, `siekiera`, `sumo`, `trojzab`, `totemy`, `mace`, `krysztal`, `netheryt`, `mikstury`, `uhc`, `start`. W `krysztal`, `netheryt`, `uhc` i `start` można budować. W `sumo` ciosy nie ranią, a przegrywa ten, kto spadnie z platformy.
- Podczas walki działają tylko `/ff`, `/msg` i `/k`. Wyjście z serwera oznacza przegraną. Totem z zestawu ratuje jak zwykle.
- ELO zmienia tylko kolejka. Start: 1000, dywizje: Brąz, Srebro, Złoto, Szmaragd, Diament, Netheryt (każda po 3 stopnie) i Mistrz. `/topki elo` pokazuje ranking.

## OneBlock

| Komenda | Działanie |
|---|---|
| `/is stworz` | zakłada wyspę: jeden blok w pustce (na start skrzynia z przedmiotami) |
| `/is` (`/wyspa`, `/baza`) | teleport na wyspę |
| `/is zapros <gracz>`, `/is dolacz` | zaproszenie (każdy członek) i dołączenie |
| `/is wyrzuc <gracz>`, `/is usun` | właściciel: wyrzucenie członka, usunięcie wyspy (z potwierdzeniem) |
| `/is opusc` | członek opuszcza wyspę |
| `/is info [gracz]`, `/is topki` | dane wyspy i ranking |
| `/is odwiedz <gracz>` (`/is warp`) | odwiedziny cudzej wyspy |
| `/is ulepszenia` | okno ulepszeń: rozmiar od 50x50 do 200x200, limit członków od 5 do 45 |
| `/is skarbiec [wplac <kwota>]` | wspólne pieniądze wyspy na ulepszenia |
| `/motyw [nazwa\|auto]` | lista motywów i wybór odblokowanego |

- Rozbity środkowy blok od razu się odradza: 88% jako blok motywu (czasem razem z potworem albo zwierzęciem), 4% jako skrzynia z łupami. Przedmioty z bloku trafiają pod nogi gracza. Każdy blok to doświadczenie wyspy, a nowy poziom to pieniądze w skarbcu.
- Motywy i poziomy: Polana (0), Kopalnia (5), Las (12), Pustynia (20), Ocean (30), Piekło (45), Kres (60).
- Budować można tylko w granicach własnej wyspy, goście nie niszczą bloków i nie ruszają zwierząt, a na wyspach nie ma PvP. Spadek w pustkę wraca na wyspę bez utraty przedmiotów.

## BoxPvP

| Komenda | Działanie |
|---|---|
| `/box` | wejście do trybu |
| `/box wyjdz` | powrót do świata głównego |
| `/kit` | zestaw startowy (co 5 minut, bez dublowania posiadanego sprzętu) |
| `/warpy` | okno teleportów: spawn, kopalnia, strefa PvP, strefa AFK |
| `/ulepsz` | ulepszanie zbroi, miecza i kilofa do 10 gwiazdek |
| `/box staty` | zabójstwa, śmierci i serie |

- **Kopalnia:** wykopany blok od razu zamienia się w pieniądze i nic nie wypada. Każda gwiazdka kilofa to +25% zarobku. Kopalnia odnawia się co 5 minut. Poza kopalnią nie można niszczyć ani stawiać bloków.
- **Ulepszanie:** kosztuje pieniądze i może się nie udać (szansa 90%, mniej o 7 punktów na każdą gwiazdkę; przy porażce pieniądze przepadają, a przedmiot zostaje). Gwiazdki dają Ochronę, Ostrość (od 5 gwiazdek też Zaklęty ogień) albo Wydajność, a materiał rośnie od żelaza przez diament po netheryt. Sprzęt jest niezniszczalny.
- **Strefa PvP:** tylko tam gracze mogą się ranić. Nagrodzone zabójstwo to 100 $ i licznik serii (te same zabezpieczenia przed nadużyciami, co przy sercach, `/topki box`, `/topki seria`).
- **Znacznik walki (20 s):** po wymianie ciosów działają tylko `/msg` i `/k`, nie można rzucić perły ani użyć warpów, a wyjście z gry oznacza śmierć. Po śmierci przedmioty wypadają na ziemię, a każda część sprzętu ma 10% szans, że wróci do gracza.
- **Strefa AFK:** 20 $ na minutę. W trybie nie działają `/sklep`, `/rynek` ani `/sprzedaj`.

## Czego nie ma w porównaniu z anarchia.gg

Mod odtwarza główne mechaniki, ale to nie jest pełna kopia ich serwerów. Nie ma: skrzynek i kluczy, zadań dziennych (`/zadania`), rajdów, zrzutów, Battle Royale i innych wydarzeń, magicznych książek, zwierzaków, kostiumów, rang VIP, kowala, kantoru, `/mail`, `/sklepzaczas`. W Duels nie ma walk 2v2 i drużyn, turniejów, obserwowania, edytora zestawów ani pojedynków o pieniądze. W OneBlock nie ma minionków, umiejętności, kolekcji, banku, giełdy i resetu wyspy po 250. poziomie. W BoxPvP nie ma odłamków, alchemii, elytry jako ulepszenia, sakiewki z łupami, prywatnych działek i NPC (zastępują je komendy i okna). Nazwy przedmiotów są własne, nie ich.

## Instalacja na serwerze

Potrzebujesz serwera **Fabric** na Minecrafta **1.21.11** (Fabric Loader 0.18 lub nowszy, Java 21) oraz **Fabric API**. Aternos nie przyjmuje własnych plików .jar, więc serwer uruchom na własnym komputerze albo u hostingu, który na to pozwala.

1. Do folderu `mods` serwera wrzuć `easytp-1.7.0+1.21.11.jar` oraz [Fabric API](https://modrinth.com/mod/fabric-api) dla 1.21.11 (testowane z `0.141.6+1.21.11`). Przy aktualizacji usuń stary plik `easytp-*.jar` z `mods` (dwie wersje naraz zatrzymają serwer).
2. Uruchom serwer. Przy pierwszym starcie powstaje `config/easytp.json`, a dane graczy trafiają do `<świat>/easytp/` (`data.json` z domami i spawnem, `moderation.json` z moderacją, `players.json` z sercami, pieniędzmi i statystykami, `market.json` z rynkiem, `clans.json` z klanami, `islands.json` z wyspami, foldery `profiles/` i `duel-snapshots/` z zapisanym stanem graczy). Światy trybów powstają w `<świat>/dimensions/easytp/`.

### Serwer na własnym komputerze

1. Utwórz pusty folder poza OneDrive, np. `C:\mc-serwer` (OneDrive synchronizuje pliki w trakcie zapisu, a to może uszkodzić świat). Pobierz do niego [serwer Fabric](https://fabricmc.net/use/server/) dla 1.21.11 i zapisz go jako `fabric-server-launch.jar`, a do podfolderu `mods` wrzuć Fabric API i ten mod.
2. Skopiuj do tego folderu skrypty z folderu [`serwer`](serwer) tego repozytorium (sekcja „Serwer 24/7 na komputerze z Windows” niżej).
3. Uruchom `start-serwer.bat` (potrzebna Java 21). Serwer utworzy `eula.txt` i się zatrzyma.
4. Przeczytaj [EULA Minecrafta](https://aka.ms/MinecraftEULA) i, jeśli się zgadzasz, zmień w `eula.txt` wartość na `eula=true`. Uruchom `start-serwer.bat` jeszcze raz.
5. Gdy zobaczysz `Done`, wpisz w oknie serwera `whitelist add TwójNick` i `op TwójNick`, po czym wejdź w Minecrafcie 1.21.11 na adres `localhost`.

### Znajomi przez internet: Playit.gg

Darmowy plan, bez karty ([cennik](https://playit.gg/pricing)).

1. Zainstaluj agenta z [playit.gg/download](https://playit.gg/download) (instalator MSI) i zatwierdź go w swoim koncie (link do zatwierdzenia pokaże agent).
2. W panelu [Tunnels](https://playit.gg/account/tunnels) dodaj tunel **Minecraft Java** z lokalnym adresem `127.0.0.1` i portem `25565`.
3. Adres tunelu (np. `jakies-slowa.tun.ply.gg`) podaj znajomym, a każdego z nich dodaj komendą `whitelist add Nick`.

Pod tunel ustaw w `server.properties`: `white-list=true` (wchodzą tylko dodani gracze), `online-mode=true` (tylko prawdziwe konta Minecraft) i `server-ip=127.0.0.1` (serwer słucha tylko na tym komputerze, co do tunelu wystarcza). Żeby grać też przez sieć domową, usuń linię `server-ip=127.0.0.1`. Tunel działa, gdy działa usługa Playit i serwer; `start-serwer.bat` sam uruchamia usługę Playit, jeśli nie działa.

## Serwer 24/7 na komputerze z Windows

`start-serwer.bat` to **nadzorca** (zwykły plik tekstowy, można go otworzyć w Notatniku). On:

- startuje serwer i włącza go ponownie 15 s po awarii (gdy serwer pada 5 razy z rzędu, za każdym razem w ciągu minuty od startu, nadzorca się poddaje i zostawia komunikat, zamiast kręcić się w kółko),
- w tle pilnuje Playit: co minutę sprawdza jego usługę i w razie potrzeby uruchamia ją (bez uprawnień administratora). Playit sam się zamyka, gdy nie ma internetu (np. tuż po starcie komputera albo przy awarii łącza), więc nadzorca ponawia próby; po trzech nieudanych robi to rzadziej, żeby nie śmiecić w logu,
- nie dopuszcza drugiej kopii (blokada pliku i sprawdzenie portu serwera),
- sam znajduje prawdziwy `java.exe` (skrót z PATH bywa tylko przekaźnikiem, który psuje kod wyjścia) i zatrzymuje się z komunikatem, gdy EULA nie jest zaakceptowana,
- zapisuje przebieg w `serwer-24-7.log` (powyżej 1 MB stary log trafia do `serwer-24-7.old.log`).

**Zatrzymanie:** wpisz `stop` w oknie serwera. Serwer zapisze świat i się zamknie, a nadzorca nie będzie go włączał z powrotem. Samo zamknięcie okna przerywa serwer w pół kroku i można stracić kilka ostatnich minut gry. Kliknięcie myszką w oknie konsoli może wstrzymać serwer do naciśnięcia Esc lub Enter.

**Autostart:** `autostart-wlacz.bat` dodaje do Harmonogramu zadań Windows zadanie „EasyTP Serwer Minecraft 24-7” (bez uprawnień administratora), które 30 s po zalogowaniu uruchamia nadzorcę w zminimalizowanym oknie. `autostart-wylacz.bat` usuwa zadanie i nie rusza samego serwera. Zadanie można też odpalić od ręki: `schtasks /run /tn "EasyTP Serwer Minecraft 24-7"`.

**Gdzie trzymać serwer:** poza OneDrive, najlepiej w `C:\mc-serwer`. Skrypty uruchomione z folderu w OneDrive same przekierowują na `C:\mc-serwer`, jeśli jest tam `start-serwer.bat`. Limit pamięci serwera (`-Xmx`) jest w wierszu `set "MEM=2G"` na początku `start-serwer.bat`.

**Czego skrypty nie zmieniają** (to ustawienia Windows i zostają Twoje):

- Komputer musi być włączony i zalogowany na Twoje konto. Po restarcie (np. po aktualizacji Windows) serwer wstanie dopiero po zalogowaniu, chyba że włączysz automatyczne logowanie.
- Uśpienie i hibernacja wyłączają serwer. Skrypty nie blokują uśpienia ani nie zmieniają ustawień zasilania, więc w Windows ustaw „nigdy” dla uśpienia przy zasilaniu z sieci.
- Serwer zajmuje 1-2 GB pamięci. Gdy nikogo nie ma, Minecraft sam wstrzymuje symulację świata po minucie (`pause-when-empty-seconds` w `server.properties`).
- Antywirus (np. Avast) bywa nieufny wobec nowych skryptów: potrafi uznać taki plik za groźny, usunąć go i zakończyć serwer. Jeśli tak się stanie, otwórz Skrzynię z wirusami, zaznacz wpis `start-serwer.bat` i wybierz „Przywróć i dodaj wyjątek”. Samo dodanie folderu `C:\mc-serwer` do wyjątków nie wystarczyło, bo Avast wykrywa zachowanie skryptu uruchamianego przy starcie komputera.
- Programy do „optymalizacji” komputera (np. Avast Cleanup) potrafią wyłączyć zadania w tle, także zadanie autostartu. Gdyby serwer przestał wstawać po zalogowaniu, kliknij `autostart-wlacz.bat` (włącza zadanie ponownie).
## Konfiguracja (`config/easytp.json`)

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `language` | Język komunikatów: `"pl"` lub `"en"` | `"pl"` |
| `maxHomes` | Ile domów może mieć gracz (1-100) | `3` |
| `tpaTimeoutSeconds` | Po ilu sekundach wygasa prośba `/tpa` (5-600) | `60` |
| `cooldownSeconds` | Przerwa między teleportacjami tego samego gracza (0-3600 s) | `3` |
| `warmupSeconds` | Czas oczekiwania przed teleportacją; ruch albo obrażenia ją przerywają (0-30 s) | `0` |
| `opsBypassLimits` | Czy operatorzy pomijają limit domów, przerwę i czas oczekiwania | `true` |
| `maxPunishmentDays` | Najdłuższy ban czasowy i wyciszenie w dniach (1-3650); `perm` zawsze działa | `365` |
| `backupEnabled` | Czy mod sam robi kopie zapasowe świata (`/easytp backup` działa zawsze) | `true` |
| `backupIntervalMinutes` | Co ile minut sprawdzać, czy trzeba zrobić kopię (1-1440); kopia powstaje tylko po zmianie świata | `180` |
| `backupKeep` | Ile najnowszych kopii zostawić (1-1000) | `24` |
| `backupDir` | Folder kopii: względny (od folderu serwera) albo bezwzględny, np. `D:/kopie`. Nie może leżeć wewnątrz świata | `"backups"` |

Sekcja `lifesteal`:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | Czy serca działają; po wyłączeniu gracze wracają do 10 serc (20 HP), a dane zostają | `true` |
| `startHearts`, `minHearts`, `maxHearts` | Serca na start, minimum i limit (1-200) | `10`, `1`, `30` |
| `legendaryHeartBonus` | O ile Legendarne serce podnosi limit | `10` |
| `blockSameIp` | Bez nagrody, gdy zabójca i ofiara mają to samo IP | `true` |
| `ignoreLocalIp` | Adres lokalny (np. przez tunel Playit) nie liczy się jako to samo IP | `true` |
| `minVictimPlayMinutes` | Bez nagrody za gracza z krótszym czasem gry | `30` |
| `sameVictimCooldownMinutes` | Przerwa przed kolejną nagrodą za tę samą ofiarę | `30` |
| `recentDeathCooldownMinutes` | Bez nagrody, gdy ofiara właśnie zginęła z ręki gracza | `3` |
| `killWindowMinutes`, `maxKillsPerWindow` | Limit nagrodzonych zabójstw w oknie czasu | `60`, `15` |
| `stoneHeartChance` | Szansa na serce z kamienia (0-1) | `0.0001` |
| `legendaryHeartPrice`, `legendarySetPrice` | Ceny w `/sklepzaserca` (serca-przedmioty) | `15`, `20` |

Sekcja `economy`:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | Czy ekonomia działa | `true` |
| `currency` | Znak waluty | `"$"` |
| `killReward`, `killTheftPercent` | Nagroda za zabójstwo i procent zabierany ofierze | `50`, `10` |
| `marketMaxListings`, `marketMaxPrice` | Limit ofert na gracza i najwyższa cena na rynku | `10`, `1000000000` |
| `sellPrices` | Cennik skupu, np. `"minecraft:diamond": 100` | 10 pozycji |
| `shop` | Oferty `/sklep`: `{"item": "minecraft:golden_apple", "count": 1, "price": 150}` | 10 pozycji |

Sekcja `clans`:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | Czy klany działają; po wyłączeniu tagi znikają, a dane zostają | `true` |
| `maxMembers` | Limit członków klanu (1-500) | `20` |
| `inviteSeconds` | Ważność zaproszenia w sekundach | `300` |
| `createCost` | Koszt założenia klanu (gdy ekonomia działa) | `0` |

Sekcja `duels`:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | Czy pojedynki działają | `true` |
| `maxArenas` | Ile walk naraz (1-256) | `16` |
| `timeLimitSeconds` | Limit czasu rundy; po nim remis | `600` |
| `countdownSeconds` | Odliczanie przed walką | `3` |
| `challengeSeconds` | Ważność wyzwania | `60` |
| `queueRange`, `queueRangePerSecond` | Dozwolona różnica ELO w kolejce i jej wzrost na sekundę | `100`, `10` |

Sekcja `oneblock`:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | Czy OneBlock działa | `true` |
| `inviteSeconds` | Ważność zaproszenia na wyspę | `300` |
| `levelRewardPerLevel` | Pieniądze do skarbca: ta kwota razy nowy poziom wyspy | `100` |

Sekcja `boxpvp`:

| Klucz | Znaczenie | Domyślnie |
|---|---|---|
| `enabled` | Czy BoxPvP działa | `true` |
| `combatTagSeconds` | Długość znacznika walki | `20` |
| `keepGearPercent` | Szansa na zachowanie każdej części sprzętu po śmierci (0-100) | `10` |
| `killReward` | Pieniądze za nagrodzone zabójstwo | `100` |
| `blockSameIp`, `ignoreLocalIp` | Jak w sekcji `lifesteal` | `true`, `true` |
| `minVictimPlayMinutes`, `sameVictimCooldownMinutes`, `recentDeathCooldownMinutes` | Zabezpieczenia przed nadużyciami | `10`, `30`, `3` |
| `kitCooldownSeconds` | Przerwa między odbiorami `/kit` | `300` |
| `upgradeBaseCost` | Koszt ulepszenia: ta kwota razy (gwiazdki + 1) do kwadratu | `500` |
| `upgradeBaseChance`, `upgradeChancePenalty` | Szansa na ulepszenie i spadek na gwiazdkę | `90`, `7` |
| `mineResetSeconds` | Co ile sekund odnawia się kopalnia | `300` |
| `afkIntervalSeconds`, `afkReward` | Co ile sekund i ile płaci strefa AFK | `60`, `20` |

Po wyłączeniu sekcji jej komendy przestają działać od razu; podpowiedzi na czacie odświeżają się po ponownym wejściu gracza na serwer.

Po zmianach wpisz `/easytp reload` (OP albo konsola serwera). Błędne wartości są poprawiane na najbliższe dozwolone, a uszkodzony plik jest odkładany obok (`*.broken-...`).

## Budowanie ze źródeł

Wymagana Java 21. W tym folderze:

```
gradlew build
```

Gotowy mod pojawi się w `build/libs/`. Wersje Minecrafta, Fabric Loadera i Fabric API są w `gradle.properties`. Loom jest przypięty do 1.17.21, bo nowsze wydania (1.18+) wymagają Javy 25 do samego uruchomienia Gradle.

Zawartość repozytorium: `src/main/java` (kod moda), `src/main/resources` (opis moda, mixin i światy trybów), `src/test/java` (testy jednostkowe), `tools/e2e` (testy na żywo z botami), `serwer` (skrypty do pracy serwera non stop na Windows). Kod nie ma komentarzy; to, jak działa każda funkcja, opisuje ten plik. Licencja: MIT (plik `LICENSE`). Skrypty `gradlew` i `gradlew.bat` pochodzą z Gradle i mają własny nagłówek licencji Apache 2.0.

## Testy

- 131 testów jednostkowych (`gradlew test`): zapis i odczyt danych, uszkodzone pliki, prośby TPA, przerwy, konfiguracja, parser czasu, magazyn moderacji, zgodność komunikatów PL/EN, kopie zapasowe, zasady nagród za zabójstwa, pieniądze, rynek, klany, ELO i kolejka, wyspy oraz sprzęt BoxPvP.
- Testy na żywo (667 sprawdzeń, skrypty w `tools/e2e`): prawdziwy serwer Fabric 1.21.11 z modem i boty łączące się **bez moda po stronie klienta**.
  - `lifesteal.js` (118): serca, zabezpieczenia przed nadużyciami, ekonomia, sklepy, rynek, topki i zapis po restarcie.
  - `clans.js` (69): klany, rangi, czat, tagi, ogień sojuszniczy i zapis po restarcie.
  - `duels.js` (96): wyzwania, zestawy, rundy, sumo, totemy, kolejka i ELO, blokada komend oraz zwrot ekwipunku po walce, po wyjściu z gry i po awarii serwera.
  - `oneblock.js` (79): wyspy, odradzający się blok, poziomy, motywy, ulepszenia, ochrona terenu i osobne ekwipunki.
  - `boxpvp.js` (79): kopalnia, ulepszenia, strefy, znacznik walki, łupy, wyjście w trakcie walki i strefa AFK.
  - `upgrade.js` (22): wszystkie nowe tryby na kopii istniejącego świata i ze starą konfiguracją.
  - `harness.js` (85): domy, TPA, spawn, podpowiedzi, wymiary (Nether), bezpieczne lądowanie, odliczanie, `/reload` i zapis danych po restarcie.
  - `moderation.js` (89): bany czasowe i na stałe, wyciszenia (także `/me` i wiadomości prywatne), ostrzeżenia, historia, uprawnienia i trwałość danych po restarcie.
  - `backup.js` (30): ręczna kopia z czatu i z konsoli, zawartość ZIP-a (bez `session.lock`, z danymi moda, rozmiary plików regionów jak na dysku), przywracanie automatycznego zapisu świata, rotacja, odrzucenie złego folderu, kopie automatyczne (także na pustym, wstrzymanym serwerze), wyłączenie w konfiguracji i pomijanie kopii, gdy świat się nie zmienił. Trwa ok. 9 minut.
- Nadzorca z sekcji o 24/7 sprawdzony na atrapach serwera (zatrzymanie, awaria i powrót do działania, pętla awarii, brak pliku serwera, druga kopia, zajęty port, Playit wyłączony i niemożliwy do uruchomienia, EULA, prawdziwa Java w ścieżce ze spacją) oraz na prawdziwym serwerze: po zatrzymaniu procesu Javy nadzorca włączył go ponownie po 15 s, a mniej więcej pół minuty później serwer znów odpowiadał lokalnie i przez tunel Playit. Kopię z żywego serwera rozpakowano próbnie i porównano plik po pliku ze światem (35 plików, zero różnic).