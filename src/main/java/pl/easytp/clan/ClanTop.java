package pl.easytp.clan;

import pl.easytp.EasyTP;
import pl.easytp.player.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ClanTop {
	public record Score(long kills, long deaths) {
	}

	private ClanTop() {
	}

	public static Score score(ClanStore.Clan clan) {
		long kills = 0;
		long deaths = 0;
		for (UUID id : clan.members.keySet()) {
			PlayerData data = EasyTP.players().get(id);
			kills += data.kills;
			deaths += data.deaths;
		}
		return new Score(kills, deaths);
	}

	public static List<ClanStore.Clan> sorted() {
		List<ClanStore.Clan> clans = new ArrayList<>(EasyTP.clans().all());
		clans.sort(Comparator.<ClanStore.Clan>comparingLong(clan -> score(clan).kills()).reversed().thenComparing(clan -> clan.tag));
		return clans;
	}
}
