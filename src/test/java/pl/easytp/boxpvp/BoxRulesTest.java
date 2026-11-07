package pl.easytp.boxpvp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxRulesTest {
	private final AtomicLong now = new AtomicLong(1000);
	private final CombatTags tags = new CombatTags(now::get);
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();

	@Test
	void tagExpires() {
		tags.tag(a, b, 500);
		assertTrue(tags.isTagged(a));
		assertEquals(500, tags.remainingMillis(a));
		assertEquals(b, tags.attackerOf(a).orElseThrow());
		now.addAndGet(500);
		assertFalse(tags.isTagged(a));
		assertTrue(tags.attackerOf(a).isEmpty());
		assertEquals(List.of(a), tags.removeExpired());
		assertTrue(tags.taggedPlayers().isEmpty());
	}

	@Test
	void newTagExtendsAndClearRemoves() {
		tags.tag(a, b, 100);
		now.addAndGet(50);
		tags.tag(a, b, 100);
		assertEquals(100, tags.remainingMillis(a));
		tags.clear(a);
		assertFalse(tags.isTagged(a));
	}

	@Test
	void costGrowsWithStars() {
		assertEquals(500, Gear.cost(500, 0));
		assertEquals(2000, Gear.cost(500, 1));
		assertEquals(50000, Gear.cost(500, 9));
	}

	@Test
	void chanceFallsButStaysAboveFloor() {
		assertEquals(90, Gear.chance(90, 7, 0));
		assertEquals(27, Gear.chance(90, 7, 9));
		assertEquals(5, Gear.chance(90, 50, 9));
		assertEquals(100, Gear.chance(100, 0, 5));
		assertEquals(0, Gear.chance(0, 0, 5));
	}

	@Test
	void tiersFollowStars() {
		assertEquals(0, Gear.tierOf(0));
		assertEquals(0, Gear.tierOf(2));
		assertEquals(1, Gear.tierOf(3));
		assertEquals(2, Gear.tierOf(6));
		assertEquals(2, Gear.tierOf(10));
	}

	@Test
	void miningBonusAndStars() {
		assertEquals(1.0, Gear.miningBonus(0));
		assertEquals(3.5, Gear.miningBonus(10));
		assertEquals("", Gear.starsText(0));
		assertEquals(" ★★", Gear.starsText(2));
	}

	@Test
	void zonesDoNotOverlap() {
		assertEquals(BoxMap.Zone.SPAWN, BoxMap.zoneAt(0.5, 0.5));
		assertEquals(BoxMap.Zone.MINE, BoxMap.zoneAt(45.5, 0.5));
		assertEquals(BoxMap.Zone.PVP, BoxMap.zoneAt(-45.5, 0.5));
		assertEquals(BoxMap.Zone.AFK, BoxMap.zoneAt(0.5, 38.5));
		assertEquals(null, BoxMap.zoneAt(20.5, 0.5));
		assertEquals(BoxMap.Zone.SPAWN, BoxMap.zoneAt(10.9, 0));
		assertEquals(null, BoxMap.zoneAt(11.1, 0));
	}
}
