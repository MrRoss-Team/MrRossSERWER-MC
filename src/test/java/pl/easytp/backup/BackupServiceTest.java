package pl.easytp.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupServiceTest {
	@TempDir
	Path dir;

	private Path makeWorld() throws IOException {
		Path world = dir.resolve("server").resolve("world");
		Files.createDirectories(world.resolve("region"));
		Files.createDirectories(world.resolve("playerdata"));
		Files.createDirectories(world.resolve("empty-dir"));
		Files.write(world.resolve("level.dat"), "level".getBytes(StandardCharsets.UTF_8));
		Files.write(world.resolve("session.lock"), "lock".getBytes(StandardCharsets.UTF_8));
		Files.write(world.resolve("region").resolve("r.0.0.mca"), new byte[10_000]);
		Files.write(world.resolve("playerdata").resolve("alice.dat"), "alice".getBytes(StandardCharsets.UTF_8));
		return world;
	}

	private static List<String> entryNames(Path zipFile) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			zip.stream().map(ZipEntry::getName).forEach(names::add);
		}
		return names;
	}

	private static String entryText(Path zipFile, String name) throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile()); InputStream in = zip.getInputStream(zip.getEntry(name))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void setMtime(Path file, long millis) throws IOException {
		Files.setLastModifiedTime(file, FileTime.fromMillis(millis));
	}

	@Test
	void zipContainsTheWholeWorldUnderItsFolderNameWithoutTheSessionLock() throws IOException {
		Path world = makeWorld();
		Path target = dir.resolve("backups").resolve("world-20261019-130000.zip");

		BackupService.Result result = BackupService.zipWorld(world, target, () -> false);

		assertEquals(target, result.file());
		assertEquals(3, result.files());
		assertTrue(result.bytes() > 0);
		List<String> names = entryNames(target);
		assertTrue(names.contains("world/level.dat"), names.toString());
		assertTrue(names.contains("world/region/r.0.0.mca"), names.toString());
		assertTrue(names.contains("world/playerdata/alice.dat"), names.toString());
		assertFalse(names.stream().anyMatch(n -> n.endsWith("session.lock")), names.toString());
		assertEquals("alice", entryText(target, "world/playerdata/alice.dat"));
	}

	@Test
	void zipLeavesNoTemporaryFileBehind() throws IOException {
		Path world = makeWorld();
		Path target = dir.resolve("backups").resolve("world-20261019-130000.zip");
		BackupService.zipWorld(world, target, () -> false);

		try (Stream<Path> files = Files.list(target.getParent())) {
			assertEquals(List.of("world-20261019-130000.zip"), files.map(p -> p.getFileName().toString()).toList());
		}
	}

	@Test
	void cancelledZipLeavesNothingBehind() throws IOException {
		Path world = makeWorld();
		Path target = dir.resolve("backups").resolve("world-20261019-130000.zip");

		assertThrows(BackupService.CancelledException.class, () -> BackupService.zipWorld(world, target, () -> true));

		assertFalse(Files.exists(target));
		try (Stream<Path> files = Files.list(target.getParent())) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void fileNameUsesWorldNameAndTimestamp() {
		assertEquals("world-20261019-130005.zip", BackupService.fileName("world", LocalDateTime.of(2026, 10, 19, 13, 0, 5)));
	}

	@Test
	void uniqueTargetAddsSuffixWhenTheNameIsTaken() throws IOException {
		LocalDateTime time = LocalDateTime.of(2026, 10, 19, 13, 0, 5);
		Path first = BackupService.uniqueTarget(dir, "world", time);
		assertEquals("world-20261019-130005.zip", first.getFileName().toString());
		Files.writeString(first, "x");

		Path second = BackupService.uniqueTarget(dir, "world", time);
		assertEquals("world-20261019-130005_2.zip", second.getFileName().toString());
		Files.writeString(second, "x");

		Path third = BackupService.uniqueTarget(dir, "world", time);
		assertEquals("world-20261019-130005_3.zip", third.getFileName().toString());
	}

	@Test
	void listIsNewestFirstAndIgnoresForeignFilesAndOtherWorlds() throws IOException {
		for (String name : List.of(
			"world-20261019-120000.zip", "world-20261019-130000.zip", "world-20261019-130000_2.zip", "world-20261018-235959.zip",
			"nether-20261019-140000.zip", "world-notes.zip", "world-20261019-130000.zip.tmp", "readme.txt")) {
			Files.writeString(dir.resolve(name), "x");
		}
		Files.createDirectory(dir.resolve("world-20261019-150000.zip"));

		List<String> names = BackupService.listBackups(dir, "world").stream().map(p -> p.getFileName().toString()).toList();

		assertEquals(List.of("world-20261019-130000_2.zip", "world-20261019-130000.zip", "world-20261019-120000.zip", "world-20261018-235959.zip"), names);
	}

	@Test
	void listOfMissingFolderIsEmpty() throws IOException {
		assertTrue(BackupService.listBackups(dir.resolve("nie-ma"), "world").isEmpty());
	}

	@Test
	void pruneKeepsTheNewestAndTouchesNothingElse() throws IOException {
		for (int hour = 10; hour <= 14; hour++) {
			Files.writeString(dir.resolve("world-20261019-" + hour + "0000.zip"), "x");
		}
		Files.writeString(dir.resolve("nether-20261019-090000.zip"), "x");
		Files.writeString(dir.resolve("notatki.txt"), "x");

		List<Path> deleted = BackupService.prune(dir, "world", 3);

		assertEquals(2, deleted.size());
		assertEquals(List.of("world-20261019-140000.zip", "world-20261019-130000.zip", "world-20261019-120000.zip"),
			BackupService.listBackups(dir, "world").stream().map(p -> p.getFileName().toString()).toList());
		assertTrue(Files.exists(dir.resolve("nether-20261019-090000.zip")));
		assertTrue(Files.exists(dir.resolve("notatki.txt")));
	}

	@Test
	void pruneNeverDeletesTheLastBackupEvenWithKeepZero() throws IOException {
		Files.writeString(dir.resolve("world-20261019-100000.zip"), "x");
		Files.writeString(dir.resolve("world-20261019-110000.zip"), "x");

		BackupService.prune(dir, "world", 0);

		assertEquals(List.of("world-20261019-110000.zip"),
			BackupService.listBackups(dir, "world").stream().map(p -> p.getFileName().toString()).toList());
	}

	@Test
	void cleanupTempRemovesOnlyUnfinishedZips() throws IOException {
		Files.writeString(dir.resolve("world-20261019-100000.zip.tmp"), "x");
		Files.writeString(dir.resolve("world-20261019-110000.zip"), "x");

		BackupService.cleanupTemp(dir);

		assertFalse(Files.exists(dir.resolve("world-20261019-100000.zip.tmp")));
		assertTrue(Files.exists(dir.resolve("world-20261019-110000.zip")));
	}

	@Test
	void newestModifiedIgnoresLevelDatAndSessionLock() throws IOException {
		Path world = makeWorld();
		setMtime(world.resolve("region").resolve("r.0.0.mca"), 1_000_000);
		setMtime(world.resolve("playerdata").resolve("alice.dat"), 2_000_000);
		setMtime(world.resolve("level.dat"), 9_000_000);
		setMtime(world.resolve("session.lock"), 9_500_000);

		assertEquals(2_000_000, BackupService.newestModified(world));
	}

	@Test
	void filesTheServerRewritesOnEverySaveAreNotChanges() throws IOException {
		Path world = makeWorld();
		setMtime(world.resolve("region").resolve("r.0.0.mca"), 1_000_000);
		setMtime(world.resolve("playerdata").resolve("alice.dat"), 2_000_000);
		Path data = Files.createDirectories(world.resolve("DIM-1").resolve("data"));
		for (String name : List.of("chunks.dat", "raids.dat", "raids_end.dat", "level.dat_old")) {
			Files.write(data.resolve(name), new byte[] {1});
			setMtime(data.resolve(name), 9_000_000);
		}
		Files.write(data.resolve("scoreboard.dat"), new byte[] {1});
		setMtime(data.resolve("scoreboard.dat"), 3_000_000);

		assertEquals(3_000_000, BackupService.newestModified(world), "tablica wyników to prawdziwa zmiana, a chunks/raids nie");
	}
	@Test
	void needsBackupWhenThereIsNoBackupOrTheWorldIsNewer() throws IOException {
		Path world = makeWorld();
		setMtime(world.resolve("region").resolve("r.0.0.mca"), 5_000_000);
		setMtime(world.resolve("playerdata").resolve("alice.dat"), 4_000_000);
		Path backup = dir.resolve("world-20261019-100000.zip");
		Files.writeString(backup, "x");

		assertTrue(BackupService.needsBackup(world, null));

		setMtime(backup, 6_000_000);
		assertFalse(BackupService.needsBackup(world, backup), "świat starszy od kopii nie wymaga nowej kopii");

		setMtime(backup, 4_500_000);
		assertTrue(BackupService.needsBackup(world, backup), "plik regionu jest nowszy od kopii");
	}

	@Test
	void totalSizeCountsFilesButNotTheSessionLock() throws IOException {
		Path world = makeWorld();
		assertEquals(10_000 + "level".length() + "alice".length(), BackupService.totalSize(world));
	}

	@Test
	void spaceCheckNeedsWorldSizePlusMargin() {
		long margin = BackupService.SPACE_MARGIN;
		assertTrue(BackupService.hasEnoughSpace(1_000 + margin, 1_000));
		assertFalse(BackupService.hasEnoughSpace(1_000 + margin - 1, 1_000));
		assertEquals(1_000 + margin, BackupService.requiredSpace(1_000));
	}

	@Test
	void sizesAreFormattedForHumans() {
		assertEquals("0 B", BackupService.formatSize(0));
		assertEquals("999 B", BackupService.formatSize(999));
		assertEquals("1.0 KB", BackupService.formatSize(1024));
		assertEquals("12.5 MB", BackupService.formatSize(13_107_200));
		assertEquals("2.0 GB", BackupService.formatSize(2L * 1024 * 1024 * 1024));
	}
}
