package pl.easytp.duel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {
	private final AtomicLong now = new AtomicLong(100_000);
	private final Lobby lobby = new Lobby(now::get);
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();
	private final UUID c = UUID.randomUUID();

	@Test
	void challengeCanBeTakenOnce() {
		lobby.challenge(a, b, "sumo", 3, 1000);
		assertTrue(lobby.take(b, a).isEmpty());
		Lobby.Challenge challenge = lobby.take(a, b).orElseThrow();
		assertEquals("sumo", challenge.kit());
		assertEquals(3, challenge.bestOf());
		assertTrue(lobby.take(a, b).isEmpty());
	}

	@Test
	void expiredChallengeCannotBeTaken() {
		lobby.challenge(a, b, "sumo", 1, 1000);
		now.addAndGet(1000);
		assertTrue(lobby.take(a, b).isEmpty());
	}

	@Test
	void removeExpiredReturnsOnlyOldOnes() {
		lobby.challenge(a, b, "sumo", 1, 1000);
		lobby.challenge(a, c, "sumo", 1, 5000);
		now.addAndGet(2000);
		List<Lobby.Challenge> expired = lobby.removeExpired();
		assertEquals(1, expired.size());
		assertEquals(b, expired.get(0).to());
		assertTrue(lobby.take(a, c).isPresent());
	}

	@Test
	void newChallengeReplacesOld() {
		lobby.challenge(a, b, "sumo", 1, 1000);
		lobby.challenge(a, b, "standard", 1, 1000);
		assertEquals("standard", lobby.take(a, b).orElseThrow().kit());
		assertTrue(lobby.take(a, b).isEmpty());
	}

	@Test
	void removeInvolvingClearsChallengesAndQueue() {
		lobby.challenge(a, b, "sumo", 1, 1000);
		lobby.challenge(c, a, "sumo", 1, 1000);
		lobby.enqueue(a, "sumo", 1000);
		lobby.removeInvolving(a);
		assertTrue(lobby.take(a, b).isEmpty());
		assertTrue(lobby.take(c, a).isEmpty());
		assertFalse(lobby.isQueued(a));
	}

	@Test
	void toggleRefusing() {
		assertFalse(lobby.isRefusing(a));
		assertTrue(lobby.toggleRefusing(a));
		assertTrue(lobby.isRefusing(a));
		assertFalse(lobby.toggleRefusing(a));
		assertFalse(lobby.isRefusing(a));
	}

	@Test
	void queueMatchesSameKitWithinRange() {
		lobby.enqueue(a, "sumo", 1000);
		lobby.enqueue(b, "standard", 1000);
		assertTrue(lobby.matches(100, 10).isEmpty());
		lobby.enqueue(c, "sumo", 1050);
		List<Lobby.Match> matches = lobby.matches(100, 10);
		assertEquals(1, matches.size());
		assertEquals(a, matches.get(0).a());
		assertEquals(c, matches.get(0).b());
		assertFalse(lobby.isQueued(a));
		assertFalse(lobby.isQueued(c));
		assertTrue(lobby.isQueued(b));
	}

	@Test
	void rangeWidensWithTime() {
		lobby.enqueue(a, "sumo", 1000);
		lobby.enqueue(b, "sumo", 1300);
		assertTrue(lobby.matches(100, 10).isEmpty());
		now.addAndGet(19_000);
		assertTrue(lobby.matches(100, 10).isEmpty());
		now.addAndGet(1000);
		assertEquals(1, lobby.matches(100, 10).size());
	}

	@Test
	void picksClosestOpponent() {
		lobby.enqueue(a, "sumo", 1000);
		lobby.enqueue(b, "sumo", 1090);
		lobby.enqueue(c, "sumo", 1010);
		List<Lobby.Match> matches = lobby.matches(100, 0);
		assertEquals(1, matches.size());
		assertEquals(c, matches.get(0).b());
		assertTrue(lobby.isQueued(b));
	}

	@Test
	void dequeue() {
		lobby.enqueue(a, "sumo", 1000);
		assertTrue(lobby.dequeue(a));
		assertFalse(lobby.dequeue(a));
	}
}
