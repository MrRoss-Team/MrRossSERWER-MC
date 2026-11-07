package pl.easytp.duel;

import pl.easytp.Msg;

public final class Elo {
	private static final int K = 32;

	public record Change(int winner, int loser) {
	}

	public enum Division {
		BRONZE(Msg.DIV_BRONZE, 0, 950),
		SILVER(Msg.DIV_SILVER, 951, 1010),
		GOLD(Msg.DIV_GOLD, 1011, 1050),
		EMERALD(Msg.DIV_EMERALD, 1051, 1200),
		DIAMOND(Msg.DIV_DIAMOND, 1201, 1260),
		NETHERITE(Msg.DIV_NETHERITE, 1261, 1320),
		MASTER(Msg.DIV_MASTER, 1321, Integer.MAX_VALUE);

		public final Msg title;
		public final int min;
		public final int max;

		Division(Msg title, int min, int max) {
			this.title = title;
			this.min = min;
			this.max = max;
		}
	}

	private Elo() {
	}

	public static Change after(int winner, int loser) {
		double expected = 1.0 / (1.0 + Math.pow(10.0, (loser - winner) / 400.0));
		int delta = Math.max(1, (int) Math.round(K * (1.0 - expected)));
		return new Change(winner + delta, Math.max(0, loser - delta));
	}

	public static Division division(int elo) {
		for (Division division : Division.values()) {
			if (elo <= division.max) {
				return division;
			}
		}
		return Division.MASTER;
	}

	public static String divisionName(int elo) {
		Division division = division(elo);
		if (division == Division.MASTER) {
			return division.title.text();
		}
		int low = Math.max(division.min, 0);
		int span = division.max - low + 1;
		int step = Math.min(2, (Math.max(elo, 0) - low) * 3 / span);
		return division.title.text() + " " + "I".repeat(step + 1);
	}
}
