package pl.easytp.lifesteal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.economy.Shop;
import pl.easytp.gui.ChestGui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HeartShop {
	private static final int SLOT_HEART = 20;
	private static final int SLOT_SET = 24;
	private static final int SLOT_INFO = 49;

	private HeartShop() {
	}

	public static void open(ServerPlayer player) {
		EasyTPConfig.LifeSteal config = EasyTP.config().lifesteal;
		Map<Integer, ChestGui.Entry> slots = new HashMap<>();
		ItemStack heart = ChestGui.withLore(HeartItems.legendaryHeart(), Msg.HEARTSHOP_PRICE.text(config.legendaryHeartPrice));
		slots.put(SLOT_HEART, new ChestGui.Entry(heart,
			p -> buy(p, config.legendaryHeartPrice, Msg.ITEM_LEGENDARY_HEART.text(), List.of(HeartItems.legendaryHeart()))));
		ItemStack set = ChestGui.icon(Items.NETHERITE_CHESTPLATE, Msg.ITEM_LEGENDARY_SET.text(), ChatFormatting.GOLD,
			Msg.ITEM_LEGENDARY_SET_LORE.text(), Msg.HEARTSHOP_PRICE.text(config.legendarySetPrice));
		slots.put(SLOT_SET, new ChestGui.Entry(set,
			p -> buy(p, config.legendarySetPrice, Msg.ITEM_LEGENDARY_SET.text(), HeartItems.legendarySet(p.level().registryAccess()))));
		slots.put(SLOT_INFO, new ChestGui.Entry(ChestGui.icon(Items.HEART_OF_THE_SEA,
			Msg.HEARTSHOP_HAVE.text(HeartItems.countHearts(player)), ChatFormatting.RED), null));
		ChestGui.open(player, Component.literal(Msg.HEARTSHOP_TITLE.text()), slots);
	}

	private static void buy(ServerPlayer player, int price, String name, List<ItemStack> items) {
		int have = HeartItems.countHearts(player);
		if (have < price) {
			player.sendSystemMessage(Chat.error(Msg.HEARTSHOP_NOT_ENOUGH, price, have));
			return;
		}
		HeartItems.takeHearts(player, price);
		for (ItemStack item : items) {
			Shop.give(player, item);
		}
		player.sendSystemMessage(Chat.ok(Msg.HEARTSHOP_BOUGHT, name));
		open(player);
	}
}
