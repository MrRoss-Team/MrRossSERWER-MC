package pl.easytp.clan;

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

class ClanStoreTest {
	@TempDir
	Path dir;

	private final AtomicLong now = new AtomicLong(1000);
	private final UUID leader = UUID.randomUUID();
	private final UUID deputy = UUID.randomUUID();
	private final UUID member = UUID.randomUUID();
	private final UUID stranger = UUID.randomUUID();

	private ClanStore store() {
		return new ClanStore(dir.resolve("clans.json"), now::get);
	}

	private ClanStore withClan() {
		ClanStore store = store();
		assertEquals(ClanStore.Result.OK, store.create("abc", "Moj klan", leader, "Lider"));
		assertEquals(ClanStore.Result.OK, store.invite(leader, deputy, 5000));
		assertEquals(ClanStore.Result.OK, store.join("ABC", deputy, "Zastepca", 20));
		assertEquals(ClanStore.Result.OK, store.toggleDeputy(leader, deputy));
		assertEquals(ClanStore.Result.OK, store.invite(deputy, member, 5000));
		assertEquals(ClanStore.Result.OK, store.join("abc", member, "Czlonek", 20));
		return store;
	}

	@Test
	void validatesTagAndName() {
		ClanStore store = store();
		assertEquals(ClanStore.Result.BAD_TAG, store.create("a", "Dobra nazwa", leader, "L"));
		assertEquals(ClanStore.Result.BAD_TAG, store.create("abcdef", "Dobra nazwa", leader, "L"));
		assertEquals(ClanStore.Result.BAD_TAG, store.create("a b", "Dobra nazwa", leader, "L"));
		assertEquals(ClanStore.Result.BAD_NAME, store.create("abc", "xy", leader, "L"));
		assertEquals(ClanStore.Result.BAD_NAME, store.create("abc", "§cKolor", leader, "L"));
		assertEquals(ClanStore.Result.OK, store.create("abc", "Żółte Łąki", leader, "L"));
		assertEquals("ABC", store.clanOf(leader).orElseThrow().tag);
	}

	@Test
	void rejectsDuplicates() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.ALREADY_IN_CLAN, store.create("xyz", "Inny klan", member, "C"));
		assertEquals(ClanStore.Result.TAG_TAKEN, store.create("ABC", "Inny klan", stranger, "S"));
		assertEquals(ClanStore.Result.NAME_TAKEN, store.create("xyz", "moj KLAN", stranger, "S"));
	}

	@Test
	void joinNeedsValidInvite() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.UNKNOWN_CLAN, store.join("NIE", stranger, "S", 20));
		assertEquals(ClanStore.Result.NOT_INVITED, store.join("ABC", stranger, "S", 20));
		assertEquals(ClanStore.Result.OK, store.invite(leader, stranger, 100));
		now.addAndGet(100);
		assertEquals(ClanStore.Result.NOT_INVITED, store.join("ABC", stranger, "S", 20));
		assertEquals(ClanStore.Result.OK, store.invite(leader, stranger, 100));
		assertEquals(ClanStore.Result.FULL, store.join("ABC", stranger, "S", 3));
		assertEquals(ClanStore.Result.OK, store.join("ABC", stranger, "S", 4));
	}

	@Test
	void onlyManagersInvite() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.NOT_IN_CLAN, store.invite(stranger, UUID.randomUUID(), 100));
		assertEquals(ClanStore.Result.NO_PERMISSION, store.invite(member, stranger, 100));
		assertEquals(ClanStore.Result.TARGET_IN_CLAN, store.invite(leader, member, 100));
		assertEquals(ClanStore.Result.SELF, store.invite(leader, leader, 100));
	}

	@Test
	void kickRespectsRanks() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.NO_PERMISSION, store.kick(member, deputy));
		assertEquals(ClanStore.Result.NO_PERMISSION, store.kick(deputy, leader));
		assertEquals(ClanStore.Result.TARGET_NOT_MEMBER, store.kick(leader, stranger));
		assertEquals(ClanStore.Result.SELF, store.kick(leader, leader));
		assertEquals(ClanStore.Result.OK, store.kick(deputy, member));
		assertTrue(store.clanOf(member).isEmpty());
		assertEquals(ClanStore.Result.OK, store.kick(leader, deputy));
		assertTrue(store.clanOf(leader).orElseThrow().deputies.isEmpty());
	}

	@Test
	void deputyCannotKickDeputy() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.OK, store.toggleDeputy(leader, member));
		assertEquals(ClanStore.Result.NO_PERMISSION, store.kick(deputy, member));
		assertEquals(ClanStore.Result.OK, store.toggleDeputy(leader, member));
		assertEquals(ClanStore.Result.OK, store.kick(deputy, member));
	}

	@Test
	void leaderCannotLeaveButCanTransfer() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.LEADER_MUST_TRANSFER, store.leave(leader));
		assertEquals(ClanStore.Result.NO_PERMISSION, store.transferLeader(deputy, member));
		assertEquals(ClanStore.Result.TARGET_NOT_MEMBER, store.transferLeader(leader, stranger));
		assertEquals(ClanStore.Result.OK, store.transferLeader(leader, member));
		ClanStore.Clan clan = store.clanOf(member).orElseThrow();
		assertEquals(member, clan.leader);
		assertTrue(clan.deputies.contains(leader));
		assertEquals(ClanStore.Result.OK, store.leave(leader));
		assertTrue(store.clanOf(leader).isEmpty());
	}

	@Test
	void disbandFreesEveryoneAndCancelsInvites() {
		ClanStore store = withClan();
		assertEquals(ClanStore.Result.OK, store.invite(leader, stranger, 5000));
		assertEquals(ClanStore.Result.NO_PERMISSION, store.disband(deputy));
		assertEquals(ClanStore.Result.OK, store.disband(leader));
		assertTrue(store.clanOf(member).isEmpty());
		assertTrue(store.byTag("ABC").isEmpty());
		assertEquals(ClanStore.Result.OK, store.create("abc", "Nowy klan", member, "C"));
		assertEquals(ClanStore.Result.NOT_INVITED, store.join("ABC", stranger, "S", 20));
	}

	@Test
	void sameClanChecksMembership() {
		ClanStore store = withClan();
		assertTrue(store.sameClan(leader, member));
		assertFalse(store.sameClan(leader, stranger));
		assertFalse(store.sameClan(stranger, UUID.randomUUID()));
	}

	@Test
	void savesAndLoads() throws IOException {
		withClan();
		ClanStore loaded = store();
		assertTrue(loaded.load().isEmpty());
		ClanStore.Clan clan = loaded.clanOf(member).orElseThrow();
		assertEquals("ABC", clan.tag);
		assertEquals("Moj klan", clan.name);
		assertEquals(leader, clan.leader);
		assertTrue(clan.deputies.contains(deputy));
		assertEquals("Czlonek", clan.members.get(member));
		assertTrue(loaded.rename(member, "Nowa"));
		assertFalse(loaded.rename(member, "Nowa"));
		assertFalse(loaded.rename(stranger, "Nowa"));
	}

	@Test
	void brokenFileAndBadEntries() throws IOException {
		Files.writeString(dir.resolve("clans.json"), "{zle", StandardCharsets.UTF_8);
		assertTrue(store().load().isPresent());
		UUID id = UUID.randomUUID();
		Files.writeString(dir.resolve("clans.json"),
			"{\"clans\":[{\"tag\":\"AA\"},{\"tag\":\"BB\",\"name\":\"Dobry\",\"leader\":\"" + id + "\",\"createdAt\":1,\"members\":{\"" + id + "\":\"X\"},\"deputies\":[]},"
				+ "{\"tag\":\"CC\",\"name\":\"Bez lidera\",\"leader\":\"" + id + "\",\"createdAt\":1,\"members\":{},\"deputies\":[]}]}", StandardCharsets.UTF_8);
		ClanStore store = store();
		assertTrue(store.load().isEmpty());
		assertEquals(1, store.all().size());
		assertEquals("BB", store.clanOf(id).orElseThrow().tag);
	}
}
