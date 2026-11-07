package pl.easytp.tpa;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class TpaManager {
	public enum Type {
		TPA,
		TPAHERE
	}

	public record Request(UUID sender, UUID target, Type type, long createdAt, long expiresAt) {
		public boolean isExpired(long now) {
			return now >= expiresAt;
		}
	}

	private final LongSupplier clock;
	private final Map<UUID, Request> bySender = new LinkedHashMap<>();

	public TpaManager(LongSupplier clock) {
		this.clock = clock;
	}

	public Request create(UUID sender, UUID target, Type type, long timeoutMillis) {
		long now = clock.getAsLong();
		Request request = new Request(sender, target, type, now, now + timeoutMillis);
		bySender.remove(sender);
		bySender.put(sender, request);
		return request;
	}

	public Optional<Request> outgoing(UUID sender) {
		long now = clock.getAsLong();
		return Optional.ofNullable(bySender.get(sender)).filter(r -> !r.isExpired(now));
	}

	public List<Request> incoming(UUID target) {
		long now = clock.getAsLong();
		List<Request> result = new ArrayList<>();
		for (Request request : bySender.values()) {
			if (request.target().equals(target) && !request.isExpired(now)) {
				result.add(request);
			}
		}
		return result;
	}

	public Optional<Request> latestIncoming(UUID target) {
		List<Request> all = incoming(target);
		return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
	}

	public Optional<Request> incomingFrom(UUID target, UUID sender) {
		return outgoing(sender).filter(r -> r.target().equals(target));
	}

	public Optional<Request> remove(UUID sender) {
		long now = clock.getAsLong();
		return Optional.ofNullable(bySender.remove(sender)).filter(r -> !r.isExpired(now));
	}

	public List<Request> removeExpired() {
		long now = clock.getAsLong();
		List<Request> expired = new ArrayList<>();
		Iterator<Request> it = bySender.values().iterator();
		while (it.hasNext()) {
			Request request = it.next();
			if (request.isExpired(now)) {
				expired.add(request);
				it.remove();
			}
		}
		return expired;
	}

	public List<Request> removeInvolving(UUID player) {
		List<Request> removed = new ArrayList<>();
		Iterator<Request> it = bySender.values().iterator();
		while (it.hasNext()) {
			Request request = it.next();
			if (request.sender().equals(player) || request.target().equals(player)) {
				removed.add(request);
				it.remove();
			}
		}
		return removed;
	}
}
