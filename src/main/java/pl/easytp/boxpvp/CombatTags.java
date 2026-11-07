package pl.easytp.boxpvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class CombatTags {
	private record Tag(UUID attacker, long until) {
	}

	private final LongSupplier clock;
	private final Map<UUID, Tag> tags = new HashMap<>();

	public CombatTags(LongSupplier clock) {
		this.clock = clock;
	}

	public void tag(UUID player, UUID attacker, long millis) {
		tags.put(player, new Tag(attacker, clock.getAsLong() + millis));
	}

	public boolean isTagged(UUID player) {
		return remainingMillis(player) > 0;
	}

	public long remainingMillis(UUID player) {
		Tag tag = tags.get(player);
		return tag == null ? 0 : Math.max(0, tag.until() - clock.getAsLong());
	}

	public Optional<UUID> attackerOf(UUID player) {
		Tag tag = tags.get(player);
		return tag == null || tag.until() <= clock.getAsLong() ? Optional.empty() : Optional.of(tag.attacker());
	}

	public void clear(UUID player) {
		tags.remove(player);
	}

	public List<UUID> taggedPlayers() {
		return List.copyOf(tags.keySet());
	}

	public List<UUID> removeExpired() {
		long now = clock.getAsLong();
		List<UUID> expired = new ArrayList<>();
		Iterator<Map.Entry<UUID, Tag>> it = tags.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Tag> entry = it.next();
			if (entry.getValue().until() <= now) {
				expired.add(entry.getKey());
				it.remove();
			}
		}
		return expired;
	}
}
