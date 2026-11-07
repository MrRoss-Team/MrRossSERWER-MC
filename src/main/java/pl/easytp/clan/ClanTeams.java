package pl.easytp.clan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import pl.easytp.EasyTP;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClanTeams {
	private static final String PREFIX = "easytp_";

	private ClanTeams() {
	}

	private static String teamName(String tag) {
		return PREFIX + tag.toLowerCase(Locale.ROOT);
	}

	public static void reconcile(MinecraftServer server) {
		ServerScoreboard board = server.getScoreboard();
		Set<String> wanted = new HashSet<>();
		if (EasyTP.config().clans.enabled) {
			for (ClanStore.Clan clan : EasyTP.clans().all()) {
				wanted.add(teamName(clan.tag));
				update(server, clan);
			}
		}
		for (PlayerTeam team : new ArrayList<>(board.getPlayerTeams())) {
			if (team.getName().startsWith(PREFIX) && !wanted.contains(team.getName())) {
				board.removePlayerTeam(team);
			}
		}
	}

	public static void update(MinecraftServer server, ClanStore.Clan clan) {
		ServerScoreboard board = server.getScoreboard();
		String name = teamName(clan.tag);
		PlayerTeam team = board.getPlayerTeam(name);
		if (team == null) {
			team = board.addPlayerTeam(name);
		}
		team.setDisplayName(Component.literal(clan.name));
		team.setPlayerPrefix(Component.literal("[" + clan.tag + "] ").withStyle(ChatFormatting.GOLD));
		team.setAllowFriendlyFire(false);
		Set<String> names = new HashSet<>(clan.members.values());
		List<String> current = new ArrayList<>(team.getPlayers());
		for (String existing : current) {
			if (!names.contains(existing)) {
				board.removePlayerFromTeam(existing, team);
			}
		}
		for (String member : names) {
			if (!team.getPlayers().contains(member)) {
				board.addPlayerToTeam(member, team);
			}
		}
	}

	public static void remove(MinecraftServer server, String tag) {
		ServerScoreboard board = server.getScoreboard();
		PlayerTeam team = board.getPlayerTeam(teamName(tag));
		if (team != null) {
			board.removePlayerTeam(team);
		}
	}

	public static void onJoin(ServerPlayer player) {
		ClanStore store = EasyTP.clans();
		MinecraftServer server = player.level().getServer();
		store.rename(player.getUUID(), player.getPlainTextName());
		store.clanOf(player.getUUID()).ifPresent(clan -> {
			if (EasyTP.config().clans.enabled) {
				update(server, clan);
			}
		});
	}
}
