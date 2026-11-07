package pl.easytp.moderation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationsTest {
	@Test
	void parsesSingleUnits() {
		assertEquals(10_000L, Durations.parse("10s"));
		assertEquals(1_800_000L, Durations.parse("30m"));
		assertEquals(7_200_000L, Durations.parse("2h"));
		assertEquals(86_400_000L, Durations.parse("1d"));
		assertEquals(604_800_000L, Durations.parse("1w"));
	}

	@Test
	void parsesCombinationsAndIgnoresCaseAndSurroundingSpaces() {
		assertEquals(129_600_000L, Durations.parse("1d12h"));
		assertEquals(5_400_000L, Durations.parse("1h30m"));
		assertEquals(3_600_000L, Durations.parse("  1H "));
		assertEquals(90_000L, Durations.parse("90s"));
	}

	@Test
	void permanentKeywords() {
		assertEquals(Durations.PERMANENT, Durations.parse("perm"));
		assertEquals(Durations.PERMANENT, Durations.parse("PERM"));
		assertEquals(Durations.PERMANENT, Durations.parse("permanent"));
	}

	@Test
	void rejectsBadInput() {
		for (String bad : new String[] {"", "   ", "abc", "10", "m10", "1x", "0m", "0d0h", "-5m", "1d 2h", "1.5h", "1d,2h", "10minutes"}) {
			assertThrows(IllegalArgumentException.class, () -> Durations.parse(bad), "\"" + bad + "\"");
		}
		assertThrows(IllegalArgumentException.class, () -> Durations.parse(null));
	}

	@Test
	void rejectsOverflow() {
		assertThrows(IllegalArgumentException.class, () -> Durations.parse("99999999999999999999d"));
		assertThrows(IllegalArgumentException.class, () -> Durations.parse("9999999999999w"));
	}

	@Test
	void formatsReadably() {
		assertEquals("0 s", Durations.format(0));
		assertEquals("0 s", Durations.format(-5));
		assertEquals("1 s", Durations.format(1));
		assertEquals("1 s", Durations.format(1_000));
		assertEquals("2 s", Durations.format(1_001));
		assertEquals("1 min 1 s", Durations.format(61_000));
		assertEquals("1 h", Durations.format(3_600_000));
		assertEquals("1 d 1 h 1 min 1 s", Durations.format(90_061_000));
		assertEquals("7 d", Durations.format(604_800_000));
	}
}
