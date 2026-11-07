package pl.easytp.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.gui.ChestGui;
import pl.easytp.player.PlayerStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Shop {
	private Shop() {
	}

	public static String money(long amount) {
		return amount + " " + EasyTP.config().economy.currency;
	}

	public static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack.copy());
	}

	public static long priceOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) {
			return 0;
		}
		Long price = EasyTP.config().economy.sellPrices.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		return price == null ? 0 : Math.max(0, price);
	}

	public static boolean sellHeld(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		long price = priceOf(stack);
		if (price <= 0) {
			player.sendSystemMessage(Chat.error(Msg.SELL_NO_PRICE));
			return false;
		}
		int count = stack.getCount();
		long total = price * count;
		stack.setCount(0);
		EasyTP.players().deposit(player.getUUID(), total);
		player.sendSystemMessage(Chat.ok(Msg.SELL_OK, count, money(total)));
		return true;
	}

	public static boolean sellAll(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int count = 0;
		long total = 0;
		for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			long price = priceOf(stack);
			if (price > 0) {
				count += stack.getCount();
				total += price * stack.getCount();
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (count == 0) {
			player.sendSystemMessage(Chat.error(Msg.SELL_NOTHING));
			return false;
		}
		EasyTP.players().deposit(player.getUUID(), total);
		player.sendSystemMessage(Chat.ok(Msg.SELL_OK, count, money(total)));
		return true;
	}

	public static void open(ServerPlayer player, int page) {
		List<ChestGui.Entry> entries = new ArrayList<>();
		for (EasyTPConfig.ShopOffer offer : EasyTP.config().economy.shop) {
			Optional<ItemStack> stack = stackOf(offer);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack icon = ChestGui.withLore(stack.get().copy(), Msg.SHOP_PRICE.text(money(offer.price)));
			entries.add(new ChestGui.Entry(icon, p -> buy(p, stack.get(), offer.price)));
		}
		ChestGui.openPaged(player, Component.literal(Msg.SHOP_TITLE.text()), entries, page, null);
	}

	private static Optional<ItemStack> stackOf(EasyTPConfig.ShopOffer offer) {
		if (offer == null || offer.item == null || offer.price <= 0) {
			return Optional.empty();
		}
		Identifier id = Identifier.tryParse(offer.item);
		if (id == null) {
			return Optional.empty();
		}
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		if (item.isEmpty()) {
			return Optional.empty();
		}
		ItemStack stack = new ItemStack(item.get());
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), offer.count)));
		return Optional.of(stack);
	}

	private static void buy(ServerPlayer player, ItemStack stack, long price) {
		PlayerStore players = EasyTP.players();
		if (!players.withdraw(player.getUUID(), price)) {
			player.sendSystemMessage(Chat.error(Msg.NO_MONEY, money(price), money(players.get(player.getUUID()).money)));
			return;
		}
		give(player, stack);
		player.sendSystemMessage(Chat.ok(Msg.SHOP_BOUGHT, money(price)));
	}
}
