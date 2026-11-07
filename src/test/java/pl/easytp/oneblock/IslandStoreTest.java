package pl.easytp.oneblock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandStoreTest {
	@TempDir
	Path dir;

	private final AtomicLong now = new AtomicLong(1000);
	private final UUID owner = UUID.randomUUID();
	private final UUID guest = UUID.randomUUID();

	private IslandStore store() {
		return new IslandStore(dir.resolve("islands.json"), now::get);
	}

	@Test
	void levelsGrowWithBlocks() {
		assertEquals(0, IslandStore.levelOf(0));
		assertEquals(0, IslandStore.levelOf(9));
		assertEquals(1, IslandStore.levelOf(10));
		assertEquals(1, IslandStore.levelOf(21));
		assertEquals(2, IslandStore.levelOf(22));
		assertEquals(0, IslandStore.progressOf(22));
		assertEquals(3, IslandStore.progressOf(25));
	}

	@Test
	void islandsHaveSeparatePositions() {
		IslandStore store = store();
		IslandStore.Island first = store.create(owner, "Ala");
		IslandStore.Island second = store.create(guest, "Bob");
		assertEquals(new IslandStore.Position(0, 0), IslandStore.center(first.index));
		assertEquals(new IslandStore.Position(IslandStore.SPACING, 0), IslandStore.center(second.index));
		assertEquals(new IslandStore.Position(0, IslandStore.SPACING), IslandStore.center(IslandStore.PER_ROW));
		assertEquals(first.index, store.islandAt(10, -10).orElseThrow().index);
		assertEquals(second.index, store.islandAt(IslandStore.SPACING - 5, 5).orElseThrow().index);
		assertTrue(store.islandAt(0, IslandStore.SPACING * 5).isEmpty());
	}

	@Test
	void boundsFollowSizeLevel() {
		IslandStore.Island island = store().create(owner, "Ala");
		assertTrue(IslandStore.inside(island, 25, -25));
		assertFalse(IslandStore.inside(island, 26, 0));
		island.sizeLevel = 3;
		assertEquals(100, IslandStore.halfSize(island.sizeLevel));
		assertTrue(IslandStore.inside(island, 100, 100));
		assertFalse(IslandStore.inside(island, 101, 0));
	}

	@Test
	void inviteAndJoin() {
		IslandStore store = store();
		IslandStore.Island island = store.create(owner, "Ala");
		assertEquals(IslandStore.Result.NOT_INVITED, store.join(guest, "Bob"));
		assertEquals(IslandStore.Result.NONE, store.invite(guest, owner, 100));
		assertEquals(IslandStore.Result.SELF, store.invite(owner, owner, 100));
		assertEquals(IslandStore.Result.OK, store.invite(owner, guest, 100));
		now.addAndGet(100);
		assertEquals(IslandStore.Result.NOT_INVITED, store.join(guest, "Bob"));
		assertEquals(IslandStore.Result.OK, store.invite(owner, guest, 100));
		assertEquals(IslandStore.Result.OK, store.join(guest, "Bob"));
		assertEquals(island, store.islandOf(guest).orElseThrow());
		assertEquals(IslandStore.Result.ALREADY_HAS, store.join(guest, "Bob"));
		assertEquals(IslandStore.Result.TARGET_HAS, store.invite(owner, guest, 100));
	}

	@Test
	void memberLimitIsEnforced() {
		IslandStore store = store();
		store.create(owner, "Ala");
		for (int i = 0; i < 4; i++) {
			UUID id = UUID.randomUUID();
			store.invite(owner, id, 100);
			assertEquals(IslandStore.Result.OK, store.join(id, "G" + i));
		}
		store.invite(owner, guest, 100);
		assertEquals(IslandStore.Result.FULL, store.join(guest, "Bob"));
	}

	@Test
	void kickLeaveDelete() {
		IslandStore store = store();
		store.create(owner, "Ala");
		store.invite(owner, guest, 100);
		store.join(guest, "Bob");
		assertEquals(IslandStore.Result.NOT_OWNER, store.kick(guest, owner));
		assertEquals(IslandStore.Result.SELF, store.kick(owner, owner));
		assertEquals(IslandStore.Result.NOT_MEMBER, store.kick(owner, UUID.randomUUID()));
		assertEquals(IslandStore.Result.OWNER_CANNOT_LEAVE, store.leave(owner));
		assertEquals(IslandStore.Result.NOT_OWNER, store.delete(guest));
		assertEquals(IslandStore.Result.OK, store.leave(guest));
		assertTrue(store.islandOf(guest).isEmpty());
		store.invite(owner, guest, 100);
		store.join(guest, "Bob");
		assertEquals(IslandStore.Result.OK, store.kick(owner, guest));
		store.invite(owner, guest, 100);
		store.join(guest, "Bob");
		assertEquals(IslandStore.Result.OK, store.delete(owner));
		assertTrue(store.islandOf(owner).isEmpty());
		assertTrue(store.islandOf(guest).isEmpty());
		assertEquals(1, store.create(owner, "Ala").index);
	}

	@Test
	void treasury() {
		IslandStore store = store();
		IslandStore.Island island = store.create(owner, "Ala");
		assertFalse(store.deposit(island, 0));
		assertTrue(store.deposit(island, 100));
		assertFalse(store.spend(island, 101));
		assertTrue(store.spend(island, 40));
		assertEquals(60, island.treasury);
	}

	@Test
	void topSortsByBlocks() {
		IslandStore store = store();
		IslandStore.Island small = store.create(owner, "Ala");
		IslandStore.Island big = store.create(guest, "Bob");
		small.blocks = 5;
		big.blocks = 50;
		assertEquals(big, store.top().get(0));
	}

	@Test
	void savesAndLoads() throws IOException {
		IslandStore store = store();
		IslandStore.Island island = store.create(owner, "Ala");
		island.blocks = 77;
		island.sizeLevel = 2;
		island.memberLevel = 1;
		island.theme = "las";
		store.invite(owner, guest, 100);
		store.join(guest, "Bob");
		store.deposit(island, 55);

		IslandStore loaded = store();
		assertTrue(loaded.load().isEmpty());
		IslandStore.Island back = loaded.islandOf(guest).orElseThrow();
		assertEquals(77, back.blocks);
		assertEquals(2, back.sizeLevel);
		assertEquals(1, back.memberLevel);
		assertEquals("las", back.theme);
		assertEquals(55, back.treasury);
		assertEquals(owner, back.owner);
		assertEquals(1, loaded.create(UUID.randomUUID(), "Cez").index);
	}

	@Test
	void brokenFileAndBadEntries() throws IOException {
		Files.writeString(dir.resolve("islands.json"), "[", StandardCharsets.UTF_8);
		assertTrue(store().load().isPresent());
		UUID id = UUID.randomUUID();
		Files.writeString(dir.resolve("islands.json"),
			"{\"nextIndex\":9,\"islands\":[{\"index\":1},{\"index\":2,\"owner\":\"" + id + "\",\"members\":{},\"blocks\":0,\"treasury\":0,\"sizeLevel\":0,\"memberLevel\":0},"
				+ "{\"index\":3,\"owner\":\"" + id + "\",\"members\":{\"" + id + "\":\"X\"},\"blocks\":-5,\"treasury\":-1,\"sizeLevel\":0,\"memberLevel\":0}]}", StandardCharsets.UTF_8);
		IslandStore store = store();
		assertTrue(store.load().isEmpty());
		assertEquals(1, store.all().size());
		assertEquals(0, store.islandOf(id).orElseThrow().blocks);
		assertEquals(9, store.create(UUID.randomUUID(), "Y").index);
	}
}
