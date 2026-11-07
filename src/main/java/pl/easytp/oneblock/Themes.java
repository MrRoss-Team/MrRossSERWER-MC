package pl.easytp.oneblock;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pl.easytp.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Themes {
	public record Loot(Item item, int min, int max) {
		ItemStack roll(RandomSource random) {
			return new ItemStack(item, min + random.nextInt(max - min + 1));
		}
	}

	public record Theme(String id, Msg name, int level, List<Block> blocks, List<EntityType<?>> mobs, List<Loot> loot) {
		public Block rollBlock(RandomSource random) {
			return blocks.get(random.nextInt(blocks.size()));
		}

		public EntityType<?> rollMob(RandomSource random) {
			return mobs.get(random.nextInt(mobs.size()));
		}

		public List<ItemStack> rollLoot(RandomSource random) {
			List<ItemStack> items = new ArrayList<>();
			int rolls = 3 + random.nextInt(4);
			for (int i = 0; i < rolls; i++) {
				items.add(loot.get(random.nextInt(loot.size())).roll(random));
			}
			return items;
		}
	}

	private static List<Block> weighted(Object... blocksAndWeights) {
		List<Block> blocks = new ArrayList<>();
		for (int i = 0; i < blocksAndWeights.length; i += 2) {
			for (int n = 0; n < (Integer) blocksAndWeights[i + 1]; n++) {
				blocks.add((Block) blocksAndWeights[i]);
			}
		}
		return List.copyOf(blocks);
	}

	private static final List<Theme> THEMES = List.of(
		new Theme("polana", Msg.THEME_MEADOW, 0,
			weighted(Blocks.GRASS_BLOCK, 10, Blocks.DIRT, 10, Blocks.OAK_LOG, 12, Blocks.BIRCH_LOG, 4, Blocks.OAK_LEAVES, 4, Blocks.STONE, 4, Blocks.COAL_ORE, 2, Blocks.MELON, 1, Blocks.PUMPKIN, 1, Blocks.HAY_BLOCK, 1),
			List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN),
			List.of(new Loot(Items.BREAD, 2, 6), new Loot(Items.WHEAT_SEEDS, 2, 8), new Loot(Items.OAK_SAPLING, 1, 3), new Loot(Items.APPLE, 1, 4), new Loot(Items.WOODEN_PICKAXE, 1, 1), new Loot(Items.STONE_AXE, 1, 1), new Loot(Items.TORCH, 4, 16), new Loot(Items.BONE_MEAL, 2, 6))),
		new Theme("kopalnia", Msg.THEME_MINE, 5,
			weighted(Blocks.STONE, 20, Blocks.COBBLESTONE, 6, Blocks.ANDESITE, 3, Blocks.GRAVEL, 3, Blocks.COAL_ORE, 8, Blocks.COPPER_ORE, 5, Blocks.IRON_ORE, 6, Blocks.REDSTONE_ORE, 2, Blocks.LAPIS_ORE, 1, Blocks.OAK_LOG, 3),
			List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.SHEEP),
			List.of(new Loot(Items.IRON_INGOT, 1, 6), new Loot(Items.COAL, 2, 12), new Loot(Items.STONE_PICKAXE, 1, 1), new Loot(Items.IRON_PICKAXE, 1, 1), new Loot(Items.BUCKET, 1, 2), new Loot(Items.RAW_COPPER, 2, 8), new Loot(Items.COOKED_BEEF, 2, 6))),
		new Theme("las", Msg.THEME_FOREST, 12,
			weighted(Blocks.SPRUCE_LOG, 8, Blocks.DARK_OAK_LOG, 6, Blocks.JUNGLE_LOG, 4, Blocks.MOSS_BLOCK, 4, Blocks.PODZOL, 3, Blocks.BROWN_MUSHROOM_BLOCK, 2, Blocks.STONE, 8, Blocks.IRON_ORE, 4, Blocks.GOLD_ORE, 2, Blocks.DIAMOND_ORE, 1),
			List.of(EntityType.WOLF, EntityType.FOX, EntityType.RABBIT, EntityType.WITCH, EntityType.CREEPER),
			List.of(new Loot(Items.SPRUCE_SAPLING, 1, 3), new Loot(Items.JUNGLE_SAPLING, 1, 3), new Loot(Items.SWEET_BERRIES, 3, 12), new Loot(Items.GOLDEN_APPLE, 1, 2), new Loot(Items.BOW, 1, 1), new Loot(Items.ARROW, 8, 24), new Loot(Items.GOLD_INGOT, 2, 6), new Loot(Items.NAME_TAG, 1, 1))),
		new Theme("pustynia", Msg.THEME_DESERT, 20,
			weighted(Blocks.SAND, 14, Blocks.SANDSTONE, 8, Blocks.RED_SAND, 4, Blocks.TERRACOTTA, 4, Blocks.GOLD_ORE, 4, Blocks.IRON_ORE, 3, Blocks.EMERALD_ORE, 2, Blocks.DIAMOND_ORE, 2),
			List.of(EntityType.HUSK, EntityType.RABBIT, EntityType.CAMEL),
			List.of(new Loot(Items.CACTUS, 1, 4), new Loot(Items.SUGAR_CANE, 1, 6), new Loot(Items.GOLD_INGOT, 3, 10), new Loot(Items.EMERALD, 1, 6), new Loot(Items.DIAMOND, 1, 2), new Loot(Items.GOLDEN_CARROT, 2, 8))),
		new Theme("ocean", Msg.THEME_OCEAN, 30,
			weighted(Blocks.PRISMARINE, 8, Blocks.DARK_PRISMARINE, 4, Blocks.SEA_LANTERN, 2, Blocks.CLAY, 6, Blocks.SAND, 6, Blocks.WET_SPONGE, 1, Blocks.BLUE_ICE, 2, Blocks.DIAMOND_ORE, 2, Blocks.LAPIS_ORE, 4),
			List.of(EntityType.TURTLE, EntityType.DROWNED, EntityType.AXOLOTL),
			List.of(new Loot(Items.HEART_OF_THE_SEA, 1, 1), new Loot(Items.NAUTILUS_SHELL, 1, 3), new Loot(Items.TRIDENT, 1, 1), new Loot(Items.PRISMARINE_SHARD, 2, 10), new Loot(Items.DIAMOND, 1, 4), new Loot(Items.COOKED_COD, 4, 12))),
		new Theme("pieklo", Msg.THEME_NETHER, 45,
			weighted(Blocks.NETHERRACK, 16, Blocks.SOUL_SAND, 4, Blocks.BLACKSTONE, 6, Blocks.NETHER_QUARTZ_ORE, 6, Blocks.NETHER_GOLD_ORE, 4, Blocks.GLOWSTONE, 3, Blocks.MAGMA_BLOCK, 2, Blocks.NETHER_BRICKS, 4, Blocks.ANCIENT_DEBRIS, 1),
			List.of(EntityType.BLAZE, EntityType.PIGLIN, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON),
			List.of(new Loot(Items.BLAZE_ROD, 1, 4), new Loot(Items.NETHER_WART, 2, 8), new Loot(Items.ANCIENT_DEBRIS, 1, 2), new Loot(Items.GOLD_BLOCK, 1, 2), new Loot(Items.DIAMOND, 2, 6), new Loot(Items.GHAST_TEAR, 1, 2), new Loot(Items.OBSIDIAN, 4, 10))),
		new Theme("kres", Msg.THEME_END, 60,
			weighted(Blocks.END_STONE, 16, Blocks.PURPUR_BLOCK, 8, Blocks.OBSIDIAN, 4, Blocks.END_STONE_BRICKS, 6, Blocks.ANCIENT_DEBRIS, 1, Blocks.DIAMOND_ORE, 2),
			List.of(EntityType.ENDERMAN, EntityType.SHULKER, EntityType.ENDERMITE),
			List.of(new Loot(Items.ELYTRA, 1, 1), new Loot(Items.NETHERITE_SCRAP, 1, 3), new Loot(Items.NETHERITE_INGOT, 1, 1), new Loot(Items.ENDER_PEARL, 2, 8), new Loot(Items.SHULKER_SHELL, 1, 4), new Loot(Items.DRAGON_BREATH, 1, 3), new Loot(Items.DIAMOND_BLOCK, 1, 2)))
	);

	private Themes() {
	}

	public static List<Theme> all() {
		return THEMES;
	}

	public static Optional<Theme> byId(String id) {
		String lower = id.toLowerCase(Locale.ROOT);
		return THEMES.stream().filter(theme -> theme.id().equals(lower)).findFirst();
	}

	public static Theme best(int level) {
		Theme best = THEMES.get(0);
		for (Theme theme : THEMES) {
			if (level >= theme.level()) {
				best = theme;
			}
		}
		return best;
	}

	public static Theme active(IslandStore.Island island) {
		int level = island.level();
		return byId(island.theme).filter(theme -> level >= theme.level()).orElseGet(() -> best(level));
	}
}
