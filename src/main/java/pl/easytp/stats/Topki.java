package pl.easytp.stats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.clan.ClanStore;
import pl.easytp.clan.ClanTop;
import pl.easytp.economy.Shop;
import pl.easytp.gui.ChestGui;
import pl.easytp.player.PlayerData;
import pl.easytp.player.PlayerStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

public final class Topki {
	public static final int LIST_SIZE = 45;
	public static final int TEXT_SIZE = 10;
	private static final int CLAN_SLOT = 22;

	public enum Category {
		KILLS("zabojstwa", Items.DIAMOND_SWORD, Msg.TOP_KILLS, data -> data.kills, 19),
		HEARTS("serca", Items.HEART_OF_THE_SEA, Msg.TOP_HEARTS, data -> data.hearts, 20),
		MONEY("pieniadze", Items.GOLD_INGOT, Msg.TOP_MONEY, data -> data.money, 21),
		TIME("czas", Items.CLOCK, Msg.TOP_TIME, data -> data.playTicks, 23),
		STONE("kamien", Items.DIAMOND_PICKAXE, Msg.TOP_STONE, data -> data.stoneMined, 24),
		DEATHS("smierci", Items.SKELETON_SKULL, Msg.TOP_DEATHS, data -> data.deaths, 25),
		BOX("box", Items.IRON_SWORD, Msg.TOP_BOX, data -> data.boxKills, 30),
		ELO("elo", Items.NETHERITE_SWORD, Msg.TOP_ELO, data -> data.duelWins + data.duelLosses > 0 ? data.elo : 0, 31),
		STREAK("seria", Items.BLAZE_POWDER, Msg.TOP_STREAK, data -> data.boxBestStreak, 32);

		public final String command;
		private final Item icon;
		private final Msg title;
		private final ToLongFunction<PlayerData> value;
		private final int slot;

		Category(String command, Item icon, Msg title, ToLongFunction<PlayerData> value, int slot) {
			this.command = command;
			this.icon = icon;
			this.title = title;
			this.value = value;
			this.slot = slot;
		}

		public String title() {
			return title.text();
		}

		public String format(PlayerData data) {
			long raw = value.applyAsLong(data);
			return switch (this) {
				case MONEY -> Shop.money(raw);
				case TIME -> PlayerStats.time(raw);
				default -> Long.toString(raw);
			};
		}
	}

	private Topki() {
	}

	public static List<PlayerStore.Entry> top(Category category, int limit) {
		return EasyTP.players().top(category.value, limit);
	}

	public static void open(ServerPlayer player) {
		PlayerStats.syncAll(player.level().getServer());
		Map<Integer, ChestGui.Entry> slots = new HashMap<>();
		for (Category category : Category.values()) {
			ItemStack icon = ChestGui.icon(category.icon, category.title(), ChatFormatting.GOLD, Msg.TOP_CLICK.text());
			slots.put(category.slot, new ChestGui.Entry(icon, p -> openCategory(p, category)));
		}
		if (EasyTP.config().clans.enabled) {
			ItemStack icon = ChestGui.icon(Items.WHITE_BANNER, Msg.TOP_CLANS.text(), ChatFormatting.GOLD, Msg.TOP_CLICK.text());
			slots.put(CLAN_SLOT, new ChestGui.Entry(icon, Topki::openClans));
		}
		ChestGui.open(player, Component.literal(Msg.TOP_TITLE.text()), slots);
	}

	private static void openClans(ServerPlayer player) {
		List<ChestGui.Entry> entries = new ArrayList<>();
		int place = 1;
		for (ClanStore.Clan clan : ClanTop.sorted()) {
			String label = Msg.TOP_LINE.text(place, "[" + clan.tag + "] " + clan.name, Long.toString(ClanTop.score(clan).kills()));
			entries.add(new ChestGui.Entry(ChestGui.icon(Items.WHITE_BANNER, label, ChatFormatting.YELLOW), null));
			place++;
		}
		ChestGui.openPaged(player, Component.literal(Msg.TOP_HEADER.text(Msg.TOP_CLANS.text())), entries, 0, Topki::open);
	}

	private static void openCategory(ServerPlayer player, Category category) {
		List<ChestGui.Entry> entries = new ArrayList<>();
		int place = 1;
		for (PlayerStore.Entry entry : top(category, LIST_SIZE)) {
			ItemStack head = ChestGui.icon(Items.PLAYER_HEAD, Msg.TOP_LINE.text(place, entry.data().name, category.format(entry.data())), ChatFormatting.YELLOW);
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(entry.data().name));
			entries.add(new ChestGui.Entry(head, null));
			place++;
		}
		ChestGui.openPaged(player, Component.literal(Msg.TOP_HEADER.text(category.title())), entries, 0, Topki::open);
	}
}
