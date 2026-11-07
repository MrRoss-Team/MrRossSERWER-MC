package pl.easytp.stats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.player.PlayerData;
import pl.easytp.player.PlayerStore;

public final class PlayerStats {
	private PlayerStats() {
	}

	public static void sync(ServerPlayer player) {
		PlayerStore store = EasyTP.players();
		PlayerData data = store.touch(player.getUUID(), player.getPlainTextName());
		ServerStatsCounter stats = player.getStats();
		int kills = stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
		int deaths = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
		int mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
		int playTicks = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
		int stone = stats.getValue(Stats.BLOCK_MINED.get(Blocks.STONE)) + stats.getValue(Stats.BLOCK_MINED.get(Blocks.DEEPSLATE));
		int mined = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			mined += stats.getValue(Stats.BLOCK_MINED.get(block));
		}
		int placed = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof BlockItem) {
				placed += stats.getValue(Stats.ITEM_USED.get(item));
			}
		}
		if (kills != data.kills || deaths != data.deaths || mobKills != data.mobKills || playTicks != data.playTicks
			|| stone != data.stoneMined || mined != data.blocksMined || placed != data.blocksPlaced) {
			data.kills = kills;
			data.deaths = deaths;
			data.mobKills = mobKills;
			data.playTicks = playTicks;
			data.stoneMined = stone;
			data.blocksMined = mined;
			data.blocksPlaced = placed;
			store.markDirty();
		}
	}

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
		}
	}

	public static String time(long ticks) {
		long minutes = ticks / 20 / 60;
		return Msg.TIME_FORMAT.text(minutes / 60, minutes % 60);
	}
}
