# Testy na żywo (opcjonalne)

Uruchamiają prawdziwy serwer Fabric 1.21.11 z modem, a boty ([mineflayer](https://github.com/PrismarineJS/mineflayer), czysty klient bez moda) wykonują komendy i sprawdzają odpowiedzi, pozycje i zachowanie serwera. Wymagane: Node.js 20+ i Java 21.

1. Zbuduj mod (`gradlew build` w folderze wyżej).
2. Przygotuj osobny folder serwera testowego z:
   - `fabric-server-launch.jar` ([pobierz](https://fabricmc.net/use/server/) dla 1.21.11),
   - `mods/` z Fabric API i zbudowanym `easytp-*.jar`,
   - `eula.txt` z `eula=true` (po zapoznaniu się z [EULA Minecrafta](https://aka.ms/MinecraftEULA)),
   - pustym folderem `config/`,
   - `server.properties` z: `online-mode=false`, `server-ip=127.0.0.1`, `server-port=25599`, `level-type=minecraft:flat`, `difficulty=peaceful`, `spawn-protection=0`, `view-distance=4`, `simulation-distance=4`, `enforce-secure-profile=false`, `generate-structures=false`, `allow-flight=true`.
3. W tym folderze: `npm install --ignore-scripts`.
4. Uruchom scenariusz (każdy zaczyna od świeżego świata, więc przed kolejnym usuń z folderu serwera testowego `world`, `backups`, `ops.json`, `banned-players.json`, `usercache.json`):
   - `node harness.js <folder-serwera> <ścieżka-do-java.exe> [raport.txt]` (domy, TPA, spawn),
   - `node moderation.js <folder-serwera> <ścieżka-do-java.exe> [raport.txt]` (bany, wyciszenia, ostrzeżenia, historia),
   - `node backup.js <folder-serwera> <ścieżka-do-java.exe> [raport.txt]` (kopie zapasowe; trwa ok. 10 minut, bo czeka na zegar kopii. Przed startem wpisz w `config/easytp.json` `"backupIntervalMinutes": 1440`, żeby zegar nie wtrącał się w część ręczną),
   - `node lifesteal.js ...` (serca, ekonomia, sklepy, rynek, topki),
   - `node clans.js ...` (klany),
   - `node duels.js ...` (pojedynki, zestawy, ELO, kolejka, zwrot ekwipunku, także po awarii serwera),
   - `node oneblock.js ...` (wyspy, generator, ochrona terenu, osobne ekwipunki),
   - `node boxpvp.js ...` (kopalnia, ulepszenia, strefy, znacznik walki, łupy),
   - `node upgrade.js ...` (sprawdzenie na kopii istniejącego świata: przed startem skopiuj do folderu serwera testowego `world` bez `session.lock` oraz `config/easytp.json`; działającego świata nie wolno kopiować).

Wspólny kod (uruchamianie serwera, boty, pomocniki) jest w `lib.js`. Testy używają portu 25599, same zmieniają `config/easytp.json`, tymczasowo nadają botom OP i ustawiają bany, wyciszenia i bloki w świecie testowym. **Nie uruchamiaj ich na prawdziwym serwerze ani na świecie, na którym Ci zależy.**
