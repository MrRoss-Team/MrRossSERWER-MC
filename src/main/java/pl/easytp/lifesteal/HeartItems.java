package pl.easytp.lifesteal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.gui.ChestGui;

import java.util.ArrayList;
import java.util.List;

public final class HeartItems {
	private static final String KEY = "easytp_item";
	private static final String HEART = "heart";
	private static final String LEGENDARY = "legendary_heart";

	private HeartItems() {
	}

	public static ItemStack heart(int count) {
		ItemStack stack = ChestGui.icon(Items.HEART_OF_THE_SEA, Msg.ITEM_HEART.text(), ChatFormatting.RED, Msg.ITEM_HEART_LORE.text());
		mark(stack, HEART);
		stack.setCount(count);
		return stack;
	}

	public static ItemStack legendaryHeart() {
		ItemStack stack = ChestGui.icon(Items.NETHER_STAR, Msg.ITEM_LEGENDARY_HEART.text(), ChatFormatting.GOLD,
			Msg.ITEM_LEGENDARY_LORE.text(EasyTP.config().lifesteal.legendaryHeartBonus));
		mark(stack, LEGENDARY);
		return stack;
	}

	public static List<ItemStack> legendarySet(RegistryAccess registries) {
		List<ItemStack> set = new ArrayList<>();
		for (Item item : List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS)) {
			ItemStack stack = ChestGui.icon(item, Msg.ITEM_LEGENDARY_SET.text(), ChatFormatting.GOLD);
			stack.enchant(enchantment(registries, Enchantments.PROTECTION), 5);
			stack.enchant(enchantment(registries, Enchantments.UNBREAKING), 3);
			set.add(stack);
		}
		return set;
	}

	public static boolean isHeart(ItemStack stack) {
		return HEART.equals(markOf(stack));
	}

	public static boolean isLegendaryHeart(ItemStack stack) {
		return LEGENDARY.equals(markOf(stack));
	}

	public static int countHearts(ServerPlayer player) {
		return player.getInventory().clearOrCountMatchingItems(HeartItems::isHeart, 0, player.inventoryMenu.getCraftSlots());
	}

	public static void takeHearts(ServerPlayer player, int count) {
		player.getInventory().clearOrCountMatchingItems(HeartItems::isHeart, count, player.inventoryMenu.getCraftSlots());
	}

	private static Holder<Enchantment> enchantment(RegistryAccess registries, ResourceKey<Enchantment> key) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static void mark(ItemStack stack, String value) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, value);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static String markOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(KEY, "");
	}
}
