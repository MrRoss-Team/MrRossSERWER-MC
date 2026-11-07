package pl.easytp.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownsTest {
	private final AtomicLong now = new AtomicLong(1_000);
	private final Cooldowns cooldowns = new Cooldowns(now::get);
	private final UUID player = UUID.randomUUID();

	@Test
	void unknownPlayerCanActImmediately() {
		assertEquals(0, cooldowns.remainingMillis(player));
	}

	@Test
	void countsDownAndExpires() {
		cooldowns.start(player, 3_000);
		assertEquals(3_000, cooldowns.remainingMillis(player));
		now.set(2_500);
		assertEquals(1_500, cooldowns.remainingMillis(player));
		now.set(4_000);
		assertEquals(0, cooldowns.remainingMillis(player));
	}

	@Test
	void zeroDurationMeansNoCooldown() {
		cooldowns.start(player, 3_000);
		cooldowns.start(player, 0);
		assertEquals(0, cooldowns.remainingMillis(player));
	}

	@Test
	void clearRemovesCooldown() {
		cooldowns.start(player, 3_000);
		cooldowns.clear(player);
		assertEquals(0, cooldowns.remainingMillis(player));
	}
}
