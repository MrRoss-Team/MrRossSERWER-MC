package pl.easytp.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class Cooldowns {
	private final LongSupplier clock;
	private final Map<UUID, Long> readyAt = new HashMap<>();

	public Cooldowns(LongSupplier clock) {
		this.clock = clock;
	}

	public long remainingMillis(UUID player) {
		Long ready = readyAt.get(player);
		if (ready == null) {
			return 0;
		}
		long left = ready - clock.getAsLong();
		if (left <= 0) {
			readyAt.remove(player);
			return 0;
		}
		return left;
	}

	public void start(UUID player, long durationMillis) {
		if (durationMillis > 0) {
			readyAt.put(player, clock.getAsLong() + durationMillis);
		} else {
			readyAt.remove(player);
		}
	}

	public void clear(UUID player) {
		readyAt.remove(player);
	}
}
