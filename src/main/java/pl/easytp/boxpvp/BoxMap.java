package pl.easytp.boxpvp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BoxMap {
	public static final int FLOOR_Y = 64;
	public static final int MINE_TOP = FLOOR_Y + 5;
	private static final int WALL_HEIGHT = 8;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	public enum Zone {
		SPAWN(-10, 10, -10, 10, FLOOR_Y + 1),
		MINE(30, 60, -15, 15, MINE_TOP + 1),
		PVP(-60, -30, -15, 15, FLOOR_Y + 1),
		AFK(-8, 8, 30, 46, FLOOR_Y + 1);

		public final int minX;
		public final int maxX;
		public final int minZ;
		public final int maxZ;
		public final int standY;

		Zone(int minX, int maxX, int minZ, int maxZ, int standY) {
			this.minX = minX;
			this.maxX = maxX;
			this.minZ = minZ;
			this.maxZ = maxZ;
			this.standY = standY;
		}

		public boolean contains(double x, double z) {
			return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
		}

		public Vec3 spawn() {
			return new Vec3((minX + maxX) / 2 + 0.5, standY, (minZ + maxZ) / 2 + 0.5);
		}
	}

	private static final class MineMix {
		static final Map<Block, Long> PRICES = new LinkedHashMap<>();
		static final List<Block> BLOCKS = new ArrayList<>();

		static {
			add(Blocks.STONE, 1, 50);
			add(Blocks.COAL_ORE, 3, 16);
			add(Blocks.IRON_ORE, 6, 12);
			add(Blocks.REDSTONE_ORE, 5, 6);
			add(Blocks.LAPIS_ORE, 7, 5);
			add(Blocks.GOLD_ORE, 12, 6);
			add(Blocks.DIAMOND_ORE, 40, 3);
			add(Blocks.EMERALD_ORE, 60, 2);
		}

		private static void add(Block block, long price, int weight) {
			PRICES.put(block, price);
			for (int i = 0; i < weight; i++) {
				BLOCKS.add(block);
			}
		}
	}

	private BoxMap() {
	}

	public static long priceOf(BlockState state) {
		return MineMix.PRICES.getOrDefault(state.getBlock(), 0L);
	}

	public static Zone zoneAt(double x, double z) {
		for (Zone zone : Zone.values()) {
			if (zone.contains(x, z)) {
				return zone;
			}
		}
		return null;
	}

	public static boolean inMineVolume(BlockPos pos) {
		return Zone.MINE.contains(pos.getX(), pos.getZ()) && pos.getY() > FLOOR_Y && pos.getY() <= MINE_TOP;
	}

	public static void ensureBuilt(ServerLevel level, RandomSource random) {
		level.getChunk(0, 0);
		if (level.getBlockState(new BlockPos(0, FLOOR_Y, 0)).is(Blocks.SMOOTH_QUARTZ)) {
			return;
		}
		platform(level, Zone.SPAWN, Blocks.SMOOTH_QUARTZ);
		platform(level, Zone.PVP, Blocks.RED_CONCRETE);
		platform(level, Zone.AFK, Blocks.LIME_CONCRETE);
		platform(level, Zone.MINE, Blocks.BEDROCK);
		resetMine(level, random);
	}

	private static void platform(ServerLevel level, Zone zone, Block floor) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState floorState = floor.defaultBlockState();
		BlockState wall = Blocks.BARRIER.defaultBlockState();
		int top = zone == Zone.MINE ? MINE_TOP + WALL_HEIGHT : FLOOR_Y + WALL_HEIGHT;
		for (int x = zone.minX - 1; x <= zone.maxX + 1; x++) {
			for (int z = zone.minZ - 1; z <= zone.maxZ + 1; z++) {
				level.getChunk(x >> 4, z >> 4);
				boolean border = x < zone.minX || x > zone.maxX || z < zone.minZ || z > zone.maxZ;
				if (border) {
					for (int y = FLOOR_Y; y <= top; y++) {
						level.setBlock(pos.set(x, y, z), wall, FLAGS);
					}
				} else {
					level.setBlock(pos.set(x, FLOOR_Y, z), floorState, FLAGS);
				}
			}
		}
	}

	public static void resetMine(ServerLevel level, RandomSource random) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Zone.MINE.minX; x <= Zone.MINE.maxX; x++) {
			for (int z = Zone.MINE.minZ; z <= Zone.MINE.maxZ; z++) {
				level.getChunk(x >> 4, z >> 4);
				for (int y = FLOOR_Y + 1; y <= MINE_TOP; y++) {
					level.setBlock(pos.set(x, y, z), MineMix.BLOCKS.get(random.nextInt(MineMix.BLOCKS.size())).defaultBlockState(), FLAGS);
				}
			}
		}
	}
}
