package pl.easytp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyTPConfigTest {
	@TempDir
	Path dir;

	@Test
	void missingFileGivesDefaultsAndCreatesTheFile() throws IOException {
		Path file = dir.resolve("config").resolve("easytp.json");
		EasyTPConfig config = EasyTPConfig.load(file);

		assertEquals("pl", config.language);
		assertEquals(3, config.maxHomes);
		assertEquals(60, config.tpaTimeoutSeconds);
		assertEquals(3, config.cooldownSeconds);
		assertEquals(0, config.warmupSeconds);
		assertTrue(config.opsBypassLimits);
		assertEquals(365, config.maxPunishmentDays);
		assertTrue(config.backupEnabled);
		assertEquals(180, config.backupIntervalMinutes);
		assertEquals(24, config.backupKeep);
		assertEquals("backups", config.backupDir);
		assertTrue(Files.exists(file));
	}

	@Test
	void backupSettingsAreClampedAndAnEmptyFolderFallsBackToTheDefault() throws IOException {
		Path file = dir.resolve("easytp.json");
		Files.writeString(file, "{\"backupIntervalMinutes\":0,\"backupKeep\":99999,\"backupDir\":\"   \"}");

		EasyTPConfig config = EasyTPConfig.load(file);
		assertEquals(1, config.backupIntervalMinutes);
		assertEquals(1000, config.backupKeep);
		assertEquals("backups", config.backupDir);

		Files.writeString(file, "{\"backupIntervalMinutes\":99999,\"backupKeep\":0,\"backupDir\":\" D:/kopie \",\"backupEnabled\":false}");
		config = EasyTPConfig.load(file);
		assertEquals(1440, config.backupIntervalMinutes);
		assertEquals(1, config.backupKeep);
		assertEquals("D:/kopie", config.backupDir);
		assertEquals(false, config.backupEnabled);
	}

	@Test
	void outOfRangeValuesAreClamped() throws IOException {
		Path file = dir.resolve("easytp.json");
		Files.writeString(file, "{\"language\":\"de\",\"maxHomes\":1000,\"tpaTimeoutSeconds\":1,\"cooldownSeconds\":-5,\"warmupSeconds\":99,\"maxPunishmentDays\":99999}");

		EasyTPConfig config = EasyTPConfig.load(file);
		assertEquals("pl", config.language);
		assertEquals(100, config.maxHomes);
		assertEquals(5, config.tpaTimeoutSeconds);
		assertEquals(0, config.cooldownSeconds);
		assertEquals(30, config.warmupSeconds);
		assertEquals(3650, config.maxPunishmentDays);
	}

	@Test
	void partialFileKeepsDefaultsForMissingKeysAndLanguageIsCaseInsensitive() throws IOException {
		Path file = dir.resolve("easytp.json");
		Files.writeString(file, "{\"language\":\"EN\",\"maxHomes\":7}");

		EasyTPConfig config = EasyTPConfig.load(file);
		assertEquals("en", config.language);
		assertEquals(7, config.maxHomes);
		assertEquals(60, config.tpaTimeoutSeconds);

		String saved = Files.readString(file);
		assertTrue(saved.contains("tpaTimeoutSeconds"));
	}

	@Test
	void brokenFileIsMovedAsideAndDefaultsAreWritten() throws IOException {
		Path file = dir.resolve("easytp.json");
		Files.writeString(file, "{ nie json");

		EasyTPConfig config = EasyTPConfig.load(file);
		assertEquals(3, config.maxHomes);
		assertTrue(Files.exists(file));
		try (Stream<Path> files = Files.list(dir)) {
			List<String> names = files.map(p -> p.getFileName().toString()).toList();
			assertTrue(names.stream().anyMatch(n -> n.startsWith("easytp.json.broken-")), names.toString());
		}
	}
}
