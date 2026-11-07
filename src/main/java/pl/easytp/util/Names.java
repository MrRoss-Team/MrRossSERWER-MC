package pl.easytp.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Names {
	public static final int MAX_LENGTH = 16;
	public static final String DEFAULT_HOME = "home";

	private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1," + MAX_LENGTH + "}");

	private Names() {
	}

	public static String normalize(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public static boolean isValid(String name) {
		return name != null && VALID.matcher(normalize(name)).matches();
	}
}
