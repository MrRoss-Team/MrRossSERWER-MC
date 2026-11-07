package pl.easytp.duel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloTest {
	@Test
	void equalPlayersExchangeHalfOfK() {
		Elo.Change change = Elo.after(1000, 1000);
		assertEquals(1016, change.winner());
		assertEquals(984, change.loser());
	}

	@Test
	void beatingStrongerGivesMore() {
		int upset = Elo.after(900, 1300).winner() - 900;
		int expected = Elo.after(1300, 900).winner() - 1300;
		assertTrue(upset > expected);
		assertTrue(expected >= 1);
	}

	@Test
	void eloNeverGoesBelowZero() {
		assertEquals(0, Elo.after(1000, 0).loser());
	}

	@Test
	void divisionsCoverAllRanges() {
		assertEquals(Elo.Division.BRONZE, Elo.division(0));
		assertEquals(Elo.Division.BRONZE, Elo.division(950));
		assertEquals(Elo.Division.SILVER, Elo.division(951));
		assertEquals(Elo.Division.SILVER, Elo.division(1000));
		assertEquals(Elo.Division.GOLD, Elo.division(1050));
		assertEquals(Elo.Division.EMERALD, Elo.division(1200));
		assertEquals(Elo.Division.DIAMOND, Elo.division(1260));
		assertEquals(Elo.Division.NETHERITE, Elo.division(1320));
		assertEquals(Elo.Division.MASTER, Elo.division(1321));
		assertEquals(Elo.Division.MASTER, Elo.division(99999));
	}

	@Test
	void divisionNamesHaveSteps() {
		assertTrue(Elo.divisionName(0).endsWith(" I"));
		assertTrue(Elo.divisionName(950).endsWith(" III"));
		assertTrue(Elo.divisionName(1000).endsWith(" III"));
		assertTrue(Elo.divisionName(955).endsWith(" I"));
		assertTrue(!Elo.divisionName(1500).endsWith("I"));
	}
}
