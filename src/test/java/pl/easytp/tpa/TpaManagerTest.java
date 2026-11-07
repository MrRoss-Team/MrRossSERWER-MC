package pl.easytp.tpa;

import org.junit.jupiter.api.Test;
import pl.easytp.tpa.TpaManager.Request;
import pl.easytp.tpa.TpaManager.Type;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaManagerTest {
	private final AtomicLong now = new AtomicLong(0);
	private final TpaManager manager = new TpaManager(now::get);
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();

	@Test
	void createdRequestCanBeFoundFromBothSides() {
		Request request = manager.create(alice, bob, Type.TPA, 60_000);
		assertEquals(Optional.of(request), manager.outgoing(alice));
		assertEquals(List.of(request), manager.incoming(bob));
		assertEquals(Optional.of(request), manager.incomingFrom(bob, alice));
		assertTrue(manager.incomingFrom(carol, alice).isEmpty());
		assertTrue(manager.outgoing(bob).isEmpty());
	}

	@Test
	void newRequestReplacesThePreviousOneFromTheSameSender() {
		manager.create(alice, bob, Type.TPA, 60_000);
		Request second = manager.create(alice, carol, Type.TPAHERE, 60_000);
		assertTrue(manager.incoming(bob).isEmpty());
		assertEquals(List.of(second), manager.incoming(carol));
	}

	@Test
	void latestIncomingIsTheMostRecentlySentRequest() {
		manager.create(alice, carol, Type.TPA, 60_000);
		Request later = manager.create(bob, carol, Type.TPAHERE, 60_000);
		assertEquals(Optional.of(later), manager.latestIncoming(carol));
		assertEquals(2, manager.incoming(carol).size());
	}

	@Test
	void resendingMovesTheRequestToTheEndOfTheQueue() {
		manager.create(alice, carol, Type.TPA, 60_000);
		manager.create(bob, carol, Type.TPA, 60_000);
		Request again = manager.create(alice, carol, Type.TPA, 60_000);
		assertEquals(Optional.of(again), manager.latestIncoming(carol));
	}

	@Test
	void expiredRequestsAreInvisibleAndGetPurgedOnce() {
		Request request = manager.create(alice, bob, Type.TPA, 1_000);
		now.set(999);
		assertTrue(manager.outgoing(alice).isPresent());
		now.set(1_000);
		assertTrue(manager.outgoing(alice).isEmpty());
		assertTrue(manager.incoming(bob).isEmpty());
		assertTrue(manager.latestIncoming(bob).isEmpty());
		assertEquals(List.of(request), manager.removeExpired());
		assertTrue(manager.removeExpired().isEmpty());
	}

	@Test
	void removeReturnsOnlyLiveRequests() {
		Request live = manager.create(alice, bob, Type.TPA, 60_000);
		assertEquals(Optional.of(live), manager.remove(alice));
		assertTrue(manager.remove(alice).isEmpty());

		manager.create(bob, carol, Type.TPA, 1_000);
		now.set(5_000);
		assertTrue(manager.remove(bob).isEmpty());
	}

	@Test
	void removeInvolvingDropsRequestsInBothDirections() {
		manager.create(alice, bob, Type.TPA, 60_000);
		manager.create(carol, alice, Type.TPAHERE, 60_000);
		manager.create(bob, carol, Type.TPA, 60_000);

		assertEquals(2, manager.removeInvolving(alice).size());
		assertTrue(manager.outgoing(alice).isEmpty());
		assertTrue(manager.outgoing(carol).isEmpty());
		assertTrue(manager.outgoing(bob).isPresent());
	}
}
