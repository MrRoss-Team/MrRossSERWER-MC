package pl.easytp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamesTest {
	@Test
	void acceptsSimpleNames() {
		assertTrue(Names.isValid("home"));
		assertTrue(Names.isValid("Dom_1"));
		assertTrue(Names.isValid("kopalnia-2"));
		assertTrue(Names.isValid("1234567890123456"));
	}

	@Test
	void rejectsEverythingElse() {
		assertFalse(Names.isValid(null));
		assertFalse(Names.isValid(""));
		assertFalse(Names.isValid("12345678901234567"));
		assertFalse(Names.isValid("dom z"));
		assertFalse(Names.isValid("dóm"));
		assertFalse(Names.isValid("a.b"));
		assertFalse(Names.isValid("\"; drop"));
	}

	@Test
	void normalizeLowercases() {
		assertEquals("dom_1", Names.normalize("DoM_1"));
	}
}
