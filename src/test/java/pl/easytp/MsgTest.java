package pl.easytp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MsgTest {
	private static final Pattern SPECIFIER = Pattern.compile("%[sd]");

	private static List<String> specifiers(String pattern) {
		List<String> found = new ArrayList<>();
		Matcher matcher = SPECIFIER.matcher(pattern);
		while (matcher.find()) {
			found.add(matcher.group());
		}
		return found;
	}

	@Test
	void polishAndEnglishTextsUseTheSameFormatSpecifiersInTheSameOrder() {
		for (Msg msg : Msg.values()) {
			assertEquals(specifiers(msg.pattern(true)), specifiers(msg.pattern(false)), msg.name());
		}
	}

	@Test
	void noTextHasStrayPercentSigns() {
		for (Msg msg : Msg.values()) {
			for (boolean polish : new boolean[] {true, false}) {
				String withoutSpecifiers = SPECIFIER.matcher(msg.pattern(polish)).replaceAll("");
				assertFalse(withoutSpecifiers.contains("%"), msg.name() + (polish ? " (pl)" : " (en)"));
			}
		}
	}

	@Test
	void everyTextFormatsInBothLanguages() {
		try {
			for (String language : new String[] {"pl", "en"}) {
				Msg.setLanguage(language);
				for (Msg msg : Msg.values()) {
					List<String> specifiers = specifiers(msg.pattern("pl".equals(language)));
					Object[] args = specifiers.stream().map(s -> s.equals("%d") ? (Object) 7 : (Object) "x").toArray();
					String text = msg.text(args);
					assertFalse(text.isBlank(), msg.name());
					assertFalse(text.contains("%"), msg.name());
				}
			}
		} finally {
			Msg.setLanguage("pl");
		}
	}

	@Test
	void englishSwitchActuallyChangesTheText() {
		try {
			Msg.setLanguage("en");
			assertTrue(Msg.UNBAN_DONE.text("Alice").startsWith("Unbanned"));
			Msg.setLanguage("pl");
			assertTrue(Msg.UNBAN_DONE.text("Alice").startsWith("Zdjęto"));
		} finally {
			Msg.setLanguage("pl");
		}
	}
}
