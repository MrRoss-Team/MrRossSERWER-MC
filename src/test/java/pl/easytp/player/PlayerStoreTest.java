package pl.easytp.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStoreTest {
	@TempDir
	Path dir;

	private PlayerStore store() {
		return new PlayerStore(dir.resolve("players.json"));
	}

	@Test
	void depositAndWithdrawTrackTotals() {
		PlayerStore store = store();
		UUID id = UUID.randomUUID();
		assertEquals(100, store.deposit(id, 100));
		assertTrue(store.withdraw(id, 30));
		assertFalse(store.withdraw(id, 71));
		assertEquals(70, store.get(id).money);
		assertEquals(100, store.get(id).earned);
		assertEquals(30, store.get(id).spent);
	}

	@Test
	void negativeAmountsAreIgnored() {
		PlayerStore store = store();
		UUID id = UUID.randomUUID();
		store.deposit(id, 50);
		store.deposit(id, -20);
		assertFalse(store.withdraw(id, -20));
		assertEquals(50, store.get(id).money);
	}

	@Test
	void transferMovesMoneyOnlyWhenPossible() {
		PlayerStore store = store();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		store.deposit(a, 40);
		assertFalse(store.transfer(a, b, 41));
		assertFalse(store.transfer(a, a, 10));
		assertFalse(store.transfer(a, b, 0));
		assertTrue(store.transfer(a, b, 15));
		assertEquals(25, store.get(a).money);
		assertEquals(15, store.get(b).money);
	}

	@Test
	void moneyCannotOverflow() {
		PlayerStore store = store();
		UUID id = UUID.randomUUID();
		store.deposit(id, Long.MAX_VALUE);
		store.deposit(id, Long.MAX_VALUE);
		assertTrue(store.get(id).money > 0);
		UUID other = UUID.randomUUID();
		store.deposit(other, 5);
		assertFalse(store.transfer(other, id, 5));
		assertEquals(5, store.get(other).money);
	}

	@Test
	void topSkipsZeroAndUnnamedAndSortsDescending() {
		PlayerStore store = store();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		UUID unnamed = UUID.randomUUID();
		store.touch(a, "Ala");
		store.touch(b, "Bob");
		store.touch(c, "Cez");
		store.deposit(a, 5);
		store.deposit(b, 9);
		store.deposit(unnamed, 100);
		List<PlayerStore.Entry> top = store.top(data -> data.money, 10);
		assertEquals(2, top.size());
		assertEquals("Bob", top.get(0).data().name);
		assertEquals("Ala", top.get(1).data().name);
		assertEquals(1, store.top(data -> data.money, 1).size());
	}

	@Test
	void savesAndLoads() throws IOException {
		PlayerStore store = store();
		UUID id = UUID.randomUUID();
		store.touch(id, "Ala").hearts = 12;
		store.get(id).capBonus = 10;
		store.deposit(id, 77);
		store.save();
		assertFalse(store.isDirty());

		PlayerStore loaded = store();
		assertTrue(loaded.load().isEmpty());
		assertEquals(12, loaded.get(id).hearts);
		assertEquals(10, loaded.get(id).capBonus);
		assertEquals(77, loaded.get(id).money);
		assertEquals(id, loaded.findByName("ALA").orElseThrow().id());
	}

	@Test
	void flushWritesOnlyWhenDirty() {
		PlayerStore store = store();
		store.flush();
		assertFalse(Files.exists(dir.resolve("players.json")));
		store.deposit(UUID.randomUUID(), 1);
		store.flush();
		assertTrue(Files.exists(dir.resolve("players.json")));
		assertFalse(store.isDirty());
	}

	@Test
	void brokenFileIsMovedAside() throws IOException {
		Files.writeString(dir.resolve("players.json"), "{nie json", StandardCharsets.UTF_8);
		PlayerStore store = store();
		assertTrue(store.load().isPresent());
		assertFalse(Files.exists(dir.resolve("players.json")));
	}

	@Test
	void badEntryIsSkipped() throws IOException {
		UUID id = UUID.randomUUID();
		Files.writeString(dir.resolve("players.json"),
			"{\"players\":{\"zly-uuid\":{\"money\":5},\"" + id + "\":{\"name\":\"Ala\",\"money\":-5}}}", StandardCharsets.UTF_8);
		PlayerStore store = store();
		assertTrue(store.load().isEmpty());
		assertEquals("Ala", store.get(id).name);
		assertEquals(0, store.get(id).money);
	}
}
