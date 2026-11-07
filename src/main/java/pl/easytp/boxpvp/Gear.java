package pl.easytp.boxpvp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import pl.easytp.Msg;
import pl.easytp.gui.ChestGui;

import java.util.Locale;
import java.util.Optional;

public final class Gear {
	public static final int MAX_STARS = 10;
	private static final String KEY_TYPE = "easytp_box";
	private static final String KEY_STARS = "easytp_stars";

	public enum Piece {
		HELMET(Msg.BOX_PIECE_HELMET, EquipmentSlot.HEAD, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET),
		CHESTPLATE(Msg.BOX_PIECE_CHESTPLATE, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE),
		LEGGINGS(Msg.BOX_PIECE_LEGGINGS, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS),
		BOOTS(Msg.BOX_PIECE_BOOTS, EquipmentSlot.FEET, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS),
		SWORD(Msg.BOX_PIECE_SWORD, null, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD),
		PICKAXE(Msg.BOX_PIECE_PICKAXE, null, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);

		public final Msg title;
		public final EquipmentSlot slot;
		private final Item[] tiers;

		Piece(Msg title, EquipmentSlot slot, Item... tiers) {
			this.title = title;
			this.slot = slot;
			this.tiers = tiers;
		}

		Item item(int stars) {
			return tiers[tierOf(stars)];
		}
	}

	private Gear() {
	}

	public static int tierOf(int stars) {
		return stars < 3 ? 0 : stars < 6 ? 1 : 2;
	}

	public static long cost(long base, int stars) {
		return base * (stars + 1L) * (stars + 1L);
	}

	public static int chance(int baseChance, int penalty, int stars) {
		return Math.max(Math.min(baseChance, 5), Math.min(100, baseChance - penalty * stars));
	}

	public static double miningBonus(int stars) {
		return 1.0 + 0.25 * stars;
	}

	public static String starsText(int stars) {
		return stars == 0 ? "" : " " + "★".repeat(stars);
	}

	public static ItemStack build(Piece piece, int stars, RegistryAccess registries) {
		ItemStack stack = ChestGui.icon(piece.item(stars), piece.title.text() + starsText(stars), stars >= 6 ? ChatFormatting.GOLD : stars >= 3 ? ChatFormatting.AQUA : ChatFormatting.WHITE);
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		if (stars > 0) {
			switch (piece) {
				case SWORD -> {
					stack.enchant(enchantment(registries, Enchantments.SHARPNESS), stars);
					if (stars >= 5) {
						stack.enchant(enchantment(registries, Enchantments.FIRE_ASPECT), stars >= 10 ? 2 : 1);
					}
				}
				case PICKAXE -> stack.enchant(enchantment(registries, Enchantments.EFFICIENCY), stars);
				default -> stack.enchant(enchantment(registries, Enchantments.PROTECTION), stars);
			}
		}
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY_TYPE, piece.name().toLowerCase(Locale.ROOT));
		tag.putInt(KEY_STARS, stars);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static Holder<Enchantment> enchantment(RegistryAccess registries, ResourceKey<Enchantment> key) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	public static Optional<Piece> pieceOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		String type = data.copyTag().getStringOr(KEY_TYPE, "");
		for (Piece piece : Piece.values()) {
			if (piece.name().toLowerCase(Locale.ROOT).equals(type)) {
				return Optional.of(piece);
			}
		}
		return Optional.empty();
	}

	public static int starsOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(KEY_STARS, 0);
	}

	public static int findSlot(ServerPlayer player, Piece piece) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && pieceOf(stack).filter(found -> found == piece).isPresent()) {
				return slot;
			}
		}
		return -1;
	}
}
