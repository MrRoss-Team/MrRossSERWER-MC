package pl.easytp.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.easytp.moderation.ModerationStore.HistoryEntry;
import pl.easytp.moderation.ModerationStore.Mute;
import pl.easytp.moderation.ModerationStore.Type;
import pl.easytp.moderation.ModerationStore.Warning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationStoreTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@TempDir
	Path dir;

	private final AtomicLong now = new AtomicLong(1_000_000);

	private Path file() {
		return dir.resolve("world").resolve("easytp").resolve("moderation.json");
	}

	private ModerationStore store() {
		return new ModerationStore(file(), now::get);
	}

	@Test
	void permanentMuteStaysActive() {
		ModerationStore store = store();
		store.mute(ALICE, "Alice", "Admin", "spam", 0);
		now.addAndGet(365L * 86_400_000L);
		Optional<Mute> mute = store.activeMute(ALICE);
		assertTrue(mute.isPresent());
		assertTrue(mute.get().isPermanent());
	}

	@Test
	void temporaryMuteExpiresExactlyOnTime() {
		ModerationStore store = store();
		store.mute(ALICE, "Alice", "Admin", "spam", 60_000);
		now.addAndGet(59_999);
		assertTrue(store.activeMute(ALICE).isPresent());
		now.addAndGet(1);
		assertTrue(store.activeMute(ALICE).isEmpty());
		assertTrue(store.activeMute(ALICE).isEmpty());
		assertFalse(store.unmute(ALICE));
	}

	@Test
	void newMuteReplacesThePreviousOne() {
		ModerationStore store = store();
		store.mute(ALICE, "Alice", "Admin", "pierwszy", 60_000);
		store.mute(ALICE, "Alice", "Admin2", "drugi", 0);
		Mute mute = store.activeMute(ALICE).orElseThrow();
		assertEquals("drugi", mute.reason());
		assertEquals("Admin2", mute.by());
		assertTrue(mute.isPermanent());
		assertEquals(1, store.activeMutes().size());
	}

	@Test
	void unmuteReportsWhetherAnyoneWasMuted() {
		ModerationStore store = store();
		assertFalse(store.unmute(ALICE));
		store.mute(ALICE, "Alice", "Admin", "spam", 0);
		assertTrue(store.unmute(ALICE));
		assertFalse(store.unmute(ALICE));
	}

	@Test
	void activeMutesAreSortedByNameAndSkipExpiredOnes() {
		ModerationStore store = store();
		store.mute(BOB, "bob", "Admin", "a", 0);
		store.mute(ALICE, "Alice", "Admin", "b", 0);
		store.mute(UUID.randomUUID(), "Zed", "Admin", "c", 1_000);
		now.addAndGet(2_000);
		List<Mute> mutes = store.activeMutes();
		assertEquals(List.of("Alice", "bob"), mutes.stream().map(Mute::name).toList());
	}

	@Test
	void purgeExpiredMutesReturnsThemOnlyOnce() {
		ModerationStore store = store();
		store.mute(ALICE, "Alice", "Admin", "a", 1_000);
		store.mute(BOB, "Bob", "Admin", "b", 0);
		assertTrue(store.purgeExpiredMutes().isEmpty());
		now.addAndGet(1_000);
		List<Mute> expired = store.purgeExpiredMutes();
		assertEquals(1, expired.size());
		assertEquals(ALICE, expired.get(0).id());
		assertTrue(store.purgeExpiredMutes().isEmpty());
		assertTrue(store.activeMute(BOB).isPresent());
	}

	@Test
	void warningsAreCountedAndCleared() {
		ModerationStore store = store();
		assertTrue(store.warningsOf(ALICE).isEmpty());
		assertEquals(1, store.warn(ALICE, "Admin", "caps"));
		now.addAndGet(5_000);
		assertEquals(2, store.warn(ALICE, "Admin2", "spam"));
		assertEquals(1, store.warn(BOB, "Admin", "inne"));

		List<Warning> warnings = store.warningsOf(ALICE);
		assertEquals(2, warnings.size());
		assertEquals("caps", warnings.get(0).reason());
		assertEquals("spam", warnings.get(1).reason());
		assertEquals(1_005_000L, warnings.get(1).time());

		assertEquals(2, store.clearWarnings(ALICE));
		assertEquals(0, store.clearWarnings(ALICE));
		assertTrue(store.warningsOf(ALICE).isEmpty());
		assertEquals(1, store.warningsOf(BOB).size());
	}

	@Test
	void warningsAreCappedPerPlayerKeepingTheNewest() {
		ModerationStore store = store();
		int total = ModerationStore.MAX_WARNINGS_PER_PLAYER + 5;
		for (int i = 0; i < total; i++) {
			store.warn(ALICE, "Admin", "powód " + i);
		}
		List<Warning> warnings = store.warningsOf(ALICE);
		assertEquals(ModerationStore.MAX_WARNINGS_PER_PLAYER, warnings.size());
		assertEquals("powód 5", warnings.get(0).reason());
		assertEquals("powód " + (total - 1), warnings.get(warnings.size() - 1).reason());
	}

	@Test
	void historyIsFilteredPerPlayerAndCapped() {
		ModerationStore store = new ModerationStore(file(), now::get, 3);
		store.log(Type.WARN, ALICE, "Alice", "Admin", "1", 0);
		store.log(Type.BAN, BOB, "Bob", "Admin", "2", 86_400_000);
		store.log(Type.MUTE, ALICE, "Alice", "Admin", "3", 0);
		store.log(Type.UNMUTE, ALICE, "Alice", "Admin", "4", 0);

		List<HistoryEntry> alice = store.historyOf(ALICE);
		assertEquals(List.of(Type.MUTE, Type.UNMUTE), alice.stream().map(HistoryEntry::type).toList());
		List<HistoryEntry> bob = store.historyOf(BOB);
		assertEquals(1, bob.size());
		assertEquals(86_400_000L, bob.get(0).durationMillis());
		assertTrue(store.historyOf(UUID.randomUUID()).isEmpty());
	}

	@Test
	void negativeDurationInHistoryIsStoredAsZero() {
		ModerationStore store = store();
		store.log(Type.BAN, ALICE, "Alice", "Admin", "x", -1);
		assertEquals(0L, store.historyOf(ALICE).get(0).durationMillis());
	}

	@Test
	void everythingSurvivesReload() throws IOException {
		ModerationStore store = store();
		store.mute(ALICE, "Alice", "Admin", "wulgaryzmy żółć", 0);
		store.mute(BOB, "Bob", "Admin", "spam", 120_000);
		store.warn(ALICE, "Admin", "pierwsze ostrzeżenie");
		store.log(Type.BAN, ALICE, "Alice", "Admin", "powód", 3_600_000);
		store.log(Type.UNBAN, ALICE, "Alice", "Admin2", "", 0);

		ModerationStore reloaded = store();
		assertTrue(reloaded.load().isEmpty());
		Mute alice = reloaded.activeMute(ALICE).orElseThrow();
		assertEquals("wulgaryzmy żółć", alice.reason());
		assertTrue(alice.isPermanent());
		Mute bob = reloaded.activeMute(BOB).orElseThrow();
		assertEquals(now.get() + 120_000, bob.expiresAt());
		assertEquals("pierwsze ostrzeżenie", reloaded.warningsOf(ALICE).get(0).reason());
		List<HistoryEntry> history = reloaded.historyOf(ALICE);
		assertEquals(List.of(Type.BAN, Type.UNBAN), history.stream().map(HistoryEntry::type).toList());
		assertEquals(3_600_000L, history.get(0).durationMillis());
		assertEquals("Admin2", history.get(1).by());
	}

	@Test
	void corruptFileIsMovedAsideAndStoreKeepsWorking() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ to nie jest json");

		ModerationStore store = store();
		Optional<Path> backup = store.load();
		assertTrue(backup.isPresent());
		assertTrue(Files.exists(backup.get()));
		assertFalse(Files.exists(file()));
		assertTrue(store.activeMutes().isEmpty());

		store.mute(ALICE, "Alice", "Admin", "x", 0);
		assertTrue(Files.exists(file()));
	}

	@Test
	void invalidEntriesAreSkippedButValidOnesSurvive() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), """
			{
			  "version": 1,
			  "mutes": [
			    { "uuid": "not-a-uuid", "name": "x" },
			    { "uuid": "00000000-0000-0000-0000-000000000001", "name": "Alice", "by": "Admin", "reason": "spam", "createdAt": 5, "expiresAt": 0 },
			    "oops"
			  ],
			  "warnings": {
			    "garbage": [ { "time": 1, "by": "a", "reason": "r" } ],
			    "00000000-0000-0000-0000-000000000002": [ { "time": 7, "by": "Admin", "reason": "caps" }, "oops" ]
			  },
			  "history": [
			    { "time": 1, "type": "NOPE", "uuid": "00000000-0000-0000-0000-000000000001", "name": "Alice" },
			    { "time": 2, "type": "BAN", "uuid": "00000000-0000-0000-0000-000000000001", "name": "Alice", "by": "Admin", "reason": "x", "durationMillis": 1000 }
			  ]
			}
			""");

		ModerationStore store = store();
		assertTrue(store.load().isEmpty());
		assertEquals(List.of("Alice"), store.activeMutes().stream().map(Mute::name).toList());
		assertEquals(1, store.warningsOf(BOB).size());
		assertEquals("caps", store.warningsOf(BOB).get(0).reason());
		assertEquals(1, store.historyOf(ALICE).size());
		assertEquals(Type.BAN, store.historyOf(ALICE).get(0).type());
	}
}
