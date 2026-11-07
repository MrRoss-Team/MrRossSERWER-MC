package pl.easytp.moderation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {
	public static final long PERMANENT = -1;

	private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");

	private Durations() {
	}

	public static long parse(String text) {
		if (text == null) {
			throw new IllegalArgumentException("brak czasu");
		}
		String s = text.trim().toLowerCase(Locale.ROOT);
		if (s.equals("perm") || s.equals("permanent")) {
			return PERMANENT;
		}
		if (s.isEmpty()) {
			throw new IllegalArgumentException("pusty czas");
		}

		Matcher matcher = PART.matcher(s);
		long total = 0;
		int pos = 0;
		try {
			while (pos < s.length()) {
				if (!matcher.find(pos) || matcher.start() != pos) {
					throw new IllegalArgumentException("zły format czasu: " + text);
				}
				long amount = Long.parseLong(matcher.group(1));
				total = Math.addExact(total, Math.multiplyExact(amount, unitMillis(matcher.group(2).charAt(0))));
				pos = matcher.end();
			}
		} catch (ArithmeticException | NumberFormatException e) {
			throw new IllegalArgumentException("za duży czas: " + text, e);
		}
		if (total <= 0) {
			throw new IllegalArgumentException("czas musi być dodatni: " + text);
		}
		return total;
	}

	public static String format(long millis) {
		long totalSeconds = millis <= 0 ? 0 : (millis + 999) / 1000;
		if (totalSeconds == 0) {
			return "0 s";
		}
		long days = totalSeconds / 86_400;
		long hours = totalSeconds % 86_400 / 3_600;
		long minutes = totalSeconds % 3_600 / 60;
		long seconds = totalSeconds % 60;

		StringBuilder text = new StringBuilder();
		append(text, days, "d");
		append(text, hours, "h");
		append(text, minutes, "min");
		append(text, seconds, "s");
		return text.toString();
	}

	private static void append(StringBuilder text, long value, String unit) {
		if (value == 0) {
			return;
		}
		if (!text.isEmpty()) {
			text.append(' ');
		}
		text.append(value).append(' ').append(unit);
	}

	private static long unitMillis(char unit) {
		return switch (unit) {
			case 's' -> 1_000L;
			case 'm' -> 60_000L;
			case 'h' -> 3_600_000L;
			case 'd' -> 86_400_000L;
			case 'w' -> 604_800_000L;
			default -> throw new IllegalArgumentException("nieznana jednostka: " + unit);
		};
	}
}
