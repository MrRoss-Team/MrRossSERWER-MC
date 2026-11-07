package pl.easytp.lifesteal;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillRulesTest {
	private static final KillRules.Limits LIMITS = new KillRules.Limits(true, 1000, 500, 100, 10_000, 2);

	private final AtomicLong now = new AtomicLong(1_000_000);
	private final KillRules rules = new KillRules(now::get);
	private final UUID killer = UUID.randomUUID();
	private final UUID victim = UUID.randomUUID();
	private final UUID other = UUID.randomUUID();

	@Test
	void allowsNormalKill() {
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, false, 5000, LIMITS));
	}

	@Test
	void blocksSameIpOnlyWhenEnabled() {
		assertEquals(KillRules.Verdict.SAME_IP, rules.check(killer, victim, true, 5000, LIMITS));
		KillRules.Limits open = new KillRules.Limits(false, 1000, 500, 100, 10_000, 2);
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, true, 5000, open));
	}

	@Test
	void localAddressesCountAsDifferentPlayersBehindATunnel() {
		assertEquals(true, KillRules.sameIp("83.1.2.3", "83.1.2.3", true));
		assertEquals(false, KillRules.sameIp("83.1.2.3", "83.1.2.4", true));
		assertEquals(false, KillRules.sameIp("127.0.0.1", "127.0.0.1", true));
		assertEquals(false, KillRules.sameIp("0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:1", true));
		assertEquals(true, KillRules.sameIp("127.0.0.1", "127.0.0.1", false));
	}

	@Test
	void blocksNewPlayer() {
		assertEquals(KillRules.Verdict.NEW_PLAYER, rules.check(killer, victim, false, 999, LIMITS));
	}

	@Test
	void blocksSameVictimUntilCooldownEnds() {
		rules.recordKill(killer, victim, true);
		now.addAndGet(499);
		assertEquals(KillRules.Verdict.SAME_VICTIM, rules.check(killer, victim, false, 5000, LIMITS));
		now.addAndGet(1);
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, false, 5000, LIMITS));
	}

	@Test
	void blocksRecentlyDeadVictimForEveryKiller() {
		rules.recordKill(other, victim, false);
		assertEquals(KillRules.Verdict.RECENT_DEATH, rules.check(killer, victim, false, 5000, LIMITS));
		now.addAndGet(100);
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, false, 5000, LIMITS));
	}

	@Test
	void unrewardedKillDoesNotCountTowardsLimits() {
		rules.recordKill(killer, victim, false);
		rules.recordKill(killer, victim, false);
		rules.recordKill(killer, victim, false);
		now.addAndGet(100);
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, false, 5000, LIMITS));
	}

	@Test
	void limitsKillsPerWindow() {
		rules.recordKill(killer, UUID.randomUUID(), true);
		now.addAndGet(1000);
		rules.recordKill(killer, UUID.randomUUID(), true);
		now.addAndGet(1000);
		assertEquals(KillRules.Verdict.TOO_MANY_KILLS, rules.check(killer, victim, false, 5000, LIMITS));
		now.addAndGet(8000);
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, false, 5000, LIMITS));
	}

	@Test
	void pruneForgetsOldKills() {
		rules.recordKill(killer, victim, true);
		now.addAndGet(50);
		rules.prune(10);
		assertEquals(KillRules.Verdict.ALLOWED, rules.check(killer, victim, false, 5000, LIMITS));
	}
}
