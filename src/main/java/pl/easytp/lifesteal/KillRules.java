package pl.easytp.lifesteal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class KillRules {
	public enum Verdict {
		ALLOWED,
		SAME_IP,
		NEW_PLAYER,
		SAME_VICTIM,
		RECENT_DEATH,
		TOO_MANY_KILLS
	}

	public record Limits(boolean blockSameIp, long minVictimPlayMillis, long sameVictimMillis, long recentDeathMillis,
		long killWindowMillis, int maxKillsPerWindow) {
	}

	private record Pair(UUID killer, UUID victim) {
	}

	private final LongSupplier clock;
	private final Map<Pair, Long> lastKillOf = new HashMap<>();
	private final Map<UUID, Long> lastDeath = new HashMap<>();
	private final Map<UUID, Deque<Long>> rewardedKills = new HashMap<>();

	public KillRules(LongSupplier clock) {
		this.clock = clock;
	}

	public static boolean sameIp(String a, String b, boolean ignoreLocal) {
		if (!a.equals(b)) {
			return false;
		}
		return !(ignoreLocal && (a.startsWith("127.") || a.equals("::1") || a.equals("0:0:0:0:0:0:0:1")));
	}

	public Verdict check(UUID killer, UUID victim, boolean sameIp, long victimPlayMillis, Limits limits) {
		long now = clock.getAsLong();
		if (limits.blockSameIp() && sameIp) {
			return Verdict.SAME_IP;
		}
		if (victimPlayMillis < limits.minVictimPlayMillis()) {
			return Verdict.NEW_PLAYER;
		}
		Long previous = lastKillOf.get(new Pair(killer, victim));
		if (previous != null && now - previous < limits.sameVictimMillis()) {
			return Verdict.SAME_VICTIM;
		}
		Long death = lastDeath.get(victim);
		if (death != null && now - death < limits.recentDeathMillis()) {
			return Verdict.RECENT_DEATH;
		}
		Deque<Long> kills = rewardedKills.get(killer);
		if (kills != null) {
			while (!kills.isEmpty() && now - kills.peekFirst() >= limits.killWindowMillis()) {
				kills.pollFirst();
			}
			if (kills.size() >= limits.maxKillsPerWindow()) {
				return Verdict.TOO_MANY_KILLS;
			}
		}
		return Verdict.ALLOWED;
	}

	public void recordKill(UUID killer, UUID victim, boolean rewarded) {
		long now = clock.getAsLong();
		lastDeath.put(victim, now);
		if (rewarded) {
			lastKillOf.put(new Pair(killer, victim), now);
			rewardedKills.computeIfAbsent(killer, key -> new ArrayDeque<>()).addLast(now);
		}
	}

	public void prune(long olderThanMillis) {
		long now = clock.getAsLong();
		lastKillOf.values().removeIf(time -> now - time >= olderThanMillis);
		lastDeath.values().removeIf(time -> now - time >= olderThanMillis);
		Iterator<Deque<Long>> it = rewardedKills.values().iterator();
		while (it.hasNext()) {
			Deque<Long> kills = it.next();
			while (!kills.isEmpty() && now - kills.peekFirst() >= olderThanMillis) {
				kills.pollFirst();
			}
			if (kills.isEmpty()) {
				it.remove();
			}
		}
	}
}
