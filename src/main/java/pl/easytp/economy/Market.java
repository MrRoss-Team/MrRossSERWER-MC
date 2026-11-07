package pl.easytp.economy;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
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

public final class Market {
	private Market() {
	}

	public static boolean list(ServerPlayer player, long price) {
		EasyTPConfig.Economy config = EasyTP.config().economy;
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			player.sendSystemMessage(Chat.error(Msg.MARKET_NO_ITEM));
			return false;
		}
		if (price < 1 || price > config.marketMaxPrice) {
			player.sendSystemMessage(Chat.error(Msg.MARKET_PRICE_RANGE, config.marketMaxPrice));
			return false;
		}
		MarketStore market = EasyTP.market();
		if (market.countOf(player.getUUID()) >= config.marketMaxListings) {
			player.sendSystemMessage(Chat.error(Msg.MARKET_LIMIT, config.marketMaxListings));
			return false;
		}
		JsonElement encoded = ItemStack.CODEC.encodeStart(ops(player), stack).getOrThrow();
		market.add(player.getUUID(), player.getPlainTextName(), price, encoded);
		stack.setCount(0);
		player.sendSystemMessage(Chat.ok(Msg.MARKET_LISTED, Shop.money(price)));
		return true;
	}

	public static void open(ServerPlayer player, int page) {
		List<ChestGui.Entry> entries = new ArrayList<>();
		for (MarketStore.Listing listing : EasyTP.market().all()) {
			Optional<ItemStack> stack = decode(player, listing);
			if (stack.isEmpty()) {
				continue;
			}
			boolean own = listing.seller().equals(player.getUUID());
			ItemStack icon = ChestGui.withLore(stack.get(),
				Msg.SHOP_PRICE.text(Shop.money(listing.price())),
				Msg.MARKET_LORE_SELLER.text(listing.sellerName()),
				(own ? Msg.MARKET_LORE_OWN : Msg.MARKET_LORE_BUY).text());
			entries.add(new ChestGui.Entry(icon, p -> click(p, listing.id(), page)));
		}
		ChestGui.openPaged(player, Component.literal(Msg.MARKET_TITLE.text()), entries, page, null);
	}

	private static void click(ServerPlayer player, long id, int page) {
		MarketStore market = EasyTP.market();
		Optional<MarketStore.Listing> found = market.find(id);
		if (found.isEmpty()) {
			player.sendSystemMessage(Chat.error(Msg.MARKET_GONE));
			open(player, page);
			return;
		}
		MarketStore.Listing listing = found.get();
		Optional<ItemStack> stack = decode(player, listing);
		if (stack.isEmpty()) {
			player.sendSystemMessage(Chat.error(Msg.MARKET_GONE));
			return;
		}
		if (listing.seller().equals(player.getUUID())) {
			market.remove(id);
			Shop.give(player, stack.get());
			player.sendSystemMessage(Chat.ok(Msg.MARKET_WITHDRAWN));
			open(player, page);
			return;
		}
		PlayerStore players = EasyTP.players();
		if (!players.withdraw(player.getUUID(), listing.price())) {
			player.sendSystemMessage(Chat.error(Msg.NO_MONEY, Shop.money(listing.price()), Shop.money(players.get(player.getUUID()).money)));
			return;
		}
		market.remove(id);
		players.deposit(listing.seller(), listing.price());
		Shop.give(player, stack.get());
		player.sendSystemMessage(Chat.ok(Msg.MARKET_BOUGHT, Shop.money(listing.price())));
		ServerPlayer seller = player.level().getServer().getPlayerList().getPlayer(listing.seller());
		if (seller != null) {
			seller.sendSystemMessage(Chat.ok(Msg.MARKET_SOLD, player.getPlainTextName(), Shop.money(listing.price())));
		}
		open(player, page);
	}

	private static Optional<ItemStack> decode(ServerPlayer player, MarketStore.Listing listing) {
		Optional<ItemStack> stack = ItemStack.CODEC.parse(ops(player), listing.item()).result();
		if (stack.isEmpty()) {
			EasyTP.LOGGER.warn("EasyTP: nie można odczytać przedmiotu z oferty rynku {}", listing.id());
		}
		return stack;
	}

	private static RegistryOps<JsonElement> ops(ServerPlayer player) {
		HolderLookup.Provider registries = player.level().registryAccess();
		return RegistryOps.create(JsonOps.INSTANCE, registries);
	}
}
