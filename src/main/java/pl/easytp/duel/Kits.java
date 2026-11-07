package pl.easytp.duel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import pl.easytp.Msg;
import pl.easytp.gui.ChestGui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class Kits {
	public static final String DEFAULT = "standard";

	public record Kit(String id, Item icon, boolean building, boolean sumo, BiConsumer<Loadout, RegistryAccess> content) {
		public Loadout loadout(RegistryAccess registries) {
			Loadout loadout = new Loadout(registries);
			content.accept(loadout, registries);
			return loadout;
		}
	}

	public static final class Loadout {
		private final RegistryAccess registries;
		final Map<EquipmentSlot, ItemStack> armor = new LinkedHashMap<>();
		final List<ItemStack> main = new ArrayList<>();
		ItemStack offhand = ItemStack.EMPTY;

		Loadout(RegistryAccess registries) {
			this.registries = registries;
		}

		Loadout armor(Item head, Item chest, Item legs, Item feet, int protection) {
			armor.put(EquipmentSlot.HEAD, protected_(head, protection));
			armor.put(EquipmentSlot.CHEST, protected_(chest, protection));
			armor.put(EquipmentSlot.LEGS, protected_(legs, protection));
			armor.put(EquipmentSlot.FEET, protected_(feet, protection));
			return this;
		}

		Loadout item(Item item, int count) {
			main.add(new ItemStack(item, count));
			return this;
		}

		@SafeVarargs
		final Loadout enchanted(Item item, Map.Entry<ResourceKey<Enchantment>, Integer>... enchantments) {
			ItemStack stack = new ItemStack(item);
			for (Map.Entry<ResourceKey<Enchantment>, Integer> entry : enchantments) {
				stack.enchant(holder(entry.getKey()), entry.getValue());
			}
			main.add(stack);
			return this;
		}

		Loadout splash(int count) {
			for (int i = 0; i < count; i++) {
				main.add(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING));
			}
			return this;
		}

		Loadout offhand(Item item) {
			offhand = new ItemStack(item);
			return this;
		}

		private ItemStack protected_(Item item, int protection) {
			ItemStack stack = new ItemStack(item);
			if (protection > 0) {
				stack.enchant(holder(Enchantments.PROTECTION), protection);
			}
			return stack;
		}

		private Holder<Enchantment> holder(ResourceKey<Enchantment> key) {
			return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		}

		void apply(ServerPlayer player) {
			armor.forEach((slot, stack) -> player.setItemSlot(slot, stack.copy()));
			if (!offhand.isEmpty()) {
				player.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
			}
			for (int i = 0; i < main.size() && i < 36; i++) {
				player.getInventory().setItem(i, main.get(i).copy());
			}
			player.getInventory().setSelectedSlot(0);
		}
	}

	private static final Map<String, Kit> KITS = new LinkedHashMap<>();

	static {
		add("standard", Items.NETHERITE_SWORD, false, false, (l, r) -> l
			.armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, 3)
			.enchanted(Items.NETHERITE_SWORD, Map.entry(Enchantments.SHARPNESS, 2))
			.item(Items.GOLDEN_APPLE, 3));
		add("siekiera", Items.DIAMOND_AXE, false, false, (l, r) -> l
			.armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, 2)
			.offhand(Items.SHIELD)
			.item(Items.DIAMOND_AXE, 1)
			.item(Items.DIAMOND_SWORD, 1)
			.enchanted(Items.BOW, Map.entry(Enchantments.POWER, 1))
			.item(Items.CROSSBOW, 1)
			.item(Items.ARROW, 16));
		add("sumo", Items.SLIME_BALL, false, true, (l, r) -> {
		});
		add("trojzab", Items.TRIDENT, false, false, (l, r) -> l
			.armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, 2)
			.enchanted(Items.TRIDENT, Map.entry(Enchantments.LOYALTY, 3), Map.entry(Enchantments.IMPALING, 2))
			.item(Items.GOLDEN_APPLE, 2));
		add("totemy", Items.TOTEM_OF_UNDYING, false, false, (l, r) -> l
			.armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, 4)
			.offhand(Items.TOTEM_OF_UNDYING)
			.enchanted(Items.DIAMOND_SWORD, Map.entry(Enchantments.SHARPNESS, 3))
			.item(Items.TOTEM_OF_UNDYING, 1)
			.item(Items.TOTEM_OF_UNDYING, 1)
			.item(Items.GOLDEN_APPLE, 6));
		add("mace", Items.MACE, false, false, (l, r) -> l
			.armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, 3)
			.enchanted(Items.MACE, Map.entry(Enchantments.DENSITY, 2), Map.entry(Enchantments.WIND_BURST, 1))
			.item(Items.WIND_CHARGE, 32)
			.item(Items.DIAMOND_SWORD, 1)
			.item(Items.GOLDEN_APPLE, 3));
		add("krysztal", Items.END_CRYSTAL, true, false, (l, r) -> l
			.armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, 4)
			.offhand(Items.TOTEM_OF_UNDYING)
			.enchanted(Items.NETHERITE_SWORD, Map.entry(Enchantments.SHARPNESS, 5))
			.item(Items.END_CRYSTAL, 32)
			.item(Items.OBSIDIAN, 32)
			.item(Items.ENDER_PEARL, 8)
			.item(Items.TOTEM_OF_UNDYING, 1)
			.item(Items.GOLDEN_APPLE, 8));
		add("netheryt", Items.NETHERITE_CHESTPLATE, true, false, (l, r) -> l
			.armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, 4)
			.enchanted(Items.NETHERITE_SWORD, Map.entry(Enchantments.SHARPNESS, 5), Map.entry(Enchantments.FIRE_ASPECT, 2))
			.item(Items.ENDER_PEARL, 16)
			.item(Items.GOLDEN_APPLE, 16)
			.item(Items.OAK_PLANKS, 64)
			.splash(8));
		add("mikstury", Items.SPLASH_POTION, false, false, (l, r) -> l
			.armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, 2)
			.enchanted(Items.DIAMOND_SWORD, Map.entry(Enchantments.SHARPNESS, 2))
			.splash(20));
		add("uhc", Items.GOLDEN_APPLE, true, false, (l, r) -> l
			.armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, 1)
			.enchanted(Items.DIAMOND_SWORD, Map.entry(Enchantments.SHARPNESS, 2))
			.enchanted(Items.BOW, Map.entry(Enchantments.POWER, 1))
			.item(Items.ARROW, 16)
			.item(Items.GOLDEN_APPLE, 4)
			.item(Items.COBBLESTONE, 64)
			.item(Items.WATER_BUCKET, 1)
			.item(Items.LAVA_BUCKET, 1)
			.item(Items.DIAMOND_PICKAXE, 1)
			.item(Items.DIAMOND_AXE, 1));
		add("start", Items.STONE_SWORD, true, false, (l, r) -> {
			l.armor.put(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			l.armor.put(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
			l.offhand(Items.SHIELD)
				.item(Items.STONE_SWORD, 1)
				.item(Items.STONE_AXE, 1)
				.item(Items.STONE_PICKAXE, 1)
				.item(Items.COOKED_BEEF, 3)
				.item(Items.WATER_BUCKET, 1)
				.item(Items.COBBLESTONE, 64);
		});
	}

	private Kits() {
	}

	private static void add(String id, Item icon, boolean building, boolean sumo, BiConsumer<Loadout, RegistryAccess> content) {
		KITS.put(id, new Kit(id, icon, building, sumo, content));
	}

	public static Optional<Kit> byId(String id) {
		return Optional.ofNullable(KITS.get(id.toLowerCase(java.util.Locale.ROOT)));
	}

	public static List<String> ids() {
		return List.copyOf(KITS.keySet());
	}

	public static void preview(ServerPlayer player, Kit kit) {
		Loadout loadout = kit.loadout(player.level().registryAccess());
		Map<Integer, ChestGui.Entry> slots = new HashMap<>();
		int slot = 0;
		for (ItemStack stack : loadout.armor.values()) {
			slots.put(slot++, new ChestGui.Entry(stack, null));
		}
		if (!loadout.offhand.isEmpty()) {
			slots.put(8, new ChestGui.Entry(loadout.offhand, null));
		}
		slot = 9;
		for (ItemStack stack : loadout.main) {
			if (slot < 54) {
				slots.put(slot++, new ChestGui.Entry(stack, null));
			}
		}
		if (loadout.armor.isEmpty() && loadout.main.isEmpty()) {
			slots.put(22, new ChestGui.Entry(ChestGui.icon(kit.icon(), kit.id(), ChatFormatting.YELLOW), null));
		}
		ChestGui.open(player, net.minecraft.network.chat.Component.literal(Msg.DUEL_PREVIEW_TITLE.text(kit.id())), slots);
	}
}
