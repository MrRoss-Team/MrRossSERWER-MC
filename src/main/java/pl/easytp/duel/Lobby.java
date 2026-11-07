package pl.easytp.duel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class Lobby {
	public record Challenge(UUID from, UUID to, String kit, int bestOf, long expiresAt) {
	}

	public record Queued(UUID id, String kit, int elo, long joinedAt) {
	}

	public record Match(UUID a, UUID b, String kit) {
	}

	private final LongSupplier clock;
	private final List<Challenge> challenges = new ArrayList<>();
	private final Map<UUID, Queued> queue = new LinkedHashMap<>();
	private final Set<UUID> refusing = new HashSet<>();

	public Lobby(LongSupplier clock) {
		this.clock = clock;
	}

	public void challenge(UUID from, UUID to, String kit, int bestOf, long ttlMillis) {
		challenges.removeIf(challenge -> challenge.from().equals(from) && challenge.to().equals(to));
		challenges.add(new Challenge(from, to, kit, bestOf, clock.getAsLong() + ttlMillis));
	}

	public Optional<Challenge> take(UUID from, UUID to) {
		long now = clock.getAsLong();
		Iterator<Challenge> it = challenges.iterator();
		while (it.hasNext()) {
			Challenge challenge = it.next();
			if (challenge.from().equals(from) && challenge.to().equals(to)) {
				it.remove();
				return challenge.expiresAt() > now ? Optional.of(challenge) : Optional.empty();
			}
		}
		return Optional.empty();
	}

	public List<Challenge> removeExpired() {
		long now = clock.getAsLong();
		List<Challenge> expired = new ArrayList<>();
		Iterator<Challenge> it = challenges.iterator();
		while (it.hasNext()) {
			Challenge challenge = it.next();
			if (challenge.expiresAt() <= now) {
				expired.add(challenge);
				it.remove();
			}
		}
		return expired;
	}

	public void removeInvolving(UUID id) {
		challenges.removeIf(challenge -> challenge.from().equals(id) || challenge.to().equals(id));
		queue.remove(id);
	}

	public boolean toggleRefusing(UUID id) {
		if (refusing.remove(id)) {
			return false;
		}
		refusing.add(id);
		return true;
	}

	public boolean isRefusing(UUID id) {
		return refusing.contains(id);
	}

	public void enqueue(UUID id, String kit, int elo) {
		queue.put(id, new Queued(id, kit, elo, clock.getAsLong()));
	}

	public boolean dequeue(UUID id) {
		return queue.remove(id) != null;
	}

	public boolean isQueued(UUID id) {
		return queue.containsKey(id);
	}

	public List<Match> matches(int baseRange, int rangePerSecond) {
		long now = clock.getAsLong();
		List<Match> matches = new ArrayList<>();
		List<Queued> waiting = new ArrayList<>(queue.values());
		Set<UUID> taken = new HashSet<>();
		for (int i = 0; i < waiting.size(); i++) {
			Queued first = waiting.get(i);
			if (taken.contains(first.id())) {
				continue;
			}
			Queued best = null;
			for (int j = i + 1; j < waiting.size(); j++) {
				Queued second = waiting.get(j);
				if (taken.contains(second.id()) || !second.kit().equals(first.kit())) {
					continue;
				}
				long waited = Math.max(now - first.joinedAt(), now - second.joinedAt()) / 1000;
				long range = baseRange + waited * rangePerSecond;
				int difference = Math.abs(first.elo() - second.elo());
				if (difference <= range && (best == null || difference < Math.abs(first.elo() - best.elo()))) {
					best = second;
				}
			}
			if (best != null) {
				taken.add(first.id());
				taken.add(best.id());
				matches.add(new Match(first.id(), best.id(), first.kit()));
			}
		}
		for (Match match : matches) {
			queue.remove(match.a());
			queue.remove(match.b());
		}
		return matches;
	}
}
