package pl.easytp.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import pl.easytp.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ChestGui extends ChestMenu {
	public record Entry(ItemStack icon, Consumer<ServerPlayer> onClick) {
	}

	private static final int ROWS = 6;
	private static final int SIZE = ROWS * 9;
	private static final int PAGE_SIZE = 45;

	private final Map<Integer, Consumer<ServerPlayer>> handlers;

	private ChestGui(int id, Inventory inventory, SimpleContainer container, Map<Integer, Consumer<ServerPlayer>> handlers) {
		super(MenuType.GENERIC_9x6, id, inventory, container, ROWS);
		this.handlers = handlers;
	}

	@Override
	public void clicked(int slot, int button, ClickType type, Player player) {
		sendAllDataToRemote();
		if (type != ClickType.PICKUP || !(player instanceof ServerPlayer serverPlayer) || slot < 0 || slot >= SIZE) {
			return;
		}
		Consumer<ServerPlayer> handler = handlers.get(slot);
		if (handler != null) {
			handler.accept(serverPlayer);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	public static void open(ServerPlayer player, Component title, Map<Integer, Entry> entries) {
		SimpleContainer container = new SimpleContainer(SIZE);
		Map<Integer, Consumer<ServerPlayer>> handlers = new HashMap<>();
		for (Map.Entry<Integer, Entry> entry : entries.entrySet()) {
			if (entry.getKey() < 0 || entry.getKey() >= SIZE) {
				continue;
			}
			container.setItem(entry.getKey(), entry.getValue().icon());
			if (entry.getValue().onClick() != null) {
				handlers.put(entry.getKey(), entry.getValue().onClick());
			}
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestGui(id, inventory, container, handlers), title));
	}

	public static void openPaged(ServerPlayer player, Component title, List<Entry> entries, int page, Consumer<ServerPlayer> back) {
		int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int current = Math.max(0, Math.min(page, pages - 1));
		Map<Integer, Entry> slots = new HashMap<>();
		int start = current * PAGE_SIZE;
		for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
			slots.put(i - start, entries.get(i));
		}
		if (current > 0) {
			slots.put(45, new Entry(icon(Items.ARROW, Msg.GUI_PREVIOUS.text(), ChatFormatting.YELLOW),
				p -> openPaged(p, title, entries, current - 1, back)));
		}
		if (current < pages - 1) {
			slots.put(53, new Entry(icon(Items.ARROW, Msg.GUI_NEXT.text(), ChatFormatting.YELLOW),
				p -> openPaged(p, title, entries, current + 1, back)));
		}
		slots.put(49, new Entry(icon(Items.PAPER, Msg.GUI_PAGE.text(current + 1, pages), ChatFormatting.GRAY), null));
		if (back != null) {
			slots.put(48, new Entry(icon(Items.BARRIER, Msg.GUI_BACK.text(), ChatFormatting.RED), back));
		}
		open(player, title, slots);
	}

	public static ItemStack icon(Item item, String name, ChatFormatting color, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name, color));
		return withLore(stack, lore);
	}

	public static ItemStack withLore(ItemStack stack, String... lore) {
		if (lore.length == 0) {
			return stack;
		}
		List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		for (String line : lore) {
			lines.add(plain(line, ChatFormatting.GRAY));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return stack;
	}

	public static Component plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
	}
}
