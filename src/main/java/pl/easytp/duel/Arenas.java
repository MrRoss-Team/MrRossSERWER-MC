package pl.easytp.duel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.BitSet;

public final class Arenas {
	public static final int FLOOR_Y = 64;
	private static final int SPACING = 256;
	private static final int ORIGIN = 512;
	private static final int RADIUS = 16;
	private static final int SUMO_RADIUS = 4;
	private static final int WALL_HEIGHT = 16;
	private static final int CLEAR_RADIUS = RADIUS + 8;
	private static final int CLEAR_HEIGHT = WALL_HEIGHT + 16;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final BitSet used = new BitSet();

	public int acquire(int max) {
		int slot = used.nextClearBit(0);
		if (slot >= max) {
			return -1;
		}
		used.set(slot);
		return slot;
	}

	public void release(int slot) {
		used.clear(slot);
	}

	private static BlockPos center(int slot) {
		return new BlockPos(ORIGIN + slot * SPACING, FLOOR_Y, ORIGIN);
	}

	public static Vec3 spawn(int slot, int side, boolean sumo) {
		BlockPos center = center(slot);
		int offset = (sumo ? SUMO_RADIUS : RADIUS) - 2;
		return new Vec3(center.getX() + 0.5 + (side == 0 ? -offset : offset), FLOOR_Y + 1, center.getZ() + 0.5);
	}

	public static float yaw(int side) {
		return side == 0 ? -90 : 90;
	}

	public static void clearEntities(ServerLevel level, int slot) {
		BlockPos center = center(slot);
		AABB box = new AABB(center.getX() - CLEAR_RADIUS, FLOOR_Y - 8, center.getZ() - CLEAR_RADIUS,
			center.getX() + CLEAR_RADIUS + 1, FLOOR_Y + CLEAR_HEIGHT + 8, center.getZ() + CLEAR_RADIUS + 1);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box, entity -> !(entity instanceof Player))) {
			entity.discard();
		}
	}

	public static void prepare(ServerLevel level, int slot, boolean sumo) {
		BlockPos center = center(slot);
		int radius = sumo ? SUMO_RADIUS : RADIUS;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState floor = (sumo ? Blocks.SMOOTH_STONE : Blocks.BEDROCK).defaultBlockState();
		BlockState wall = Blocks.BARRIER.defaultBlockState();
		for (int cx = (center.getX() - CLEAR_RADIUS) >> 4; cx <= (center.getX() + CLEAR_RADIUS) >> 4; cx++) {
			for (int cz = (center.getZ() - CLEAR_RADIUS) >> 4; cz <= (center.getZ() + CLEAR_RADIUS) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
			for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
				boolean inside = Math.abs(dx) <= radius && Math.abs(dz) <= radius;
				boolean border = !sumo && Math.max(Math.abs(dx), Math.abs(dz)) == radius + 1;
				for (int y = FLOOR_Y - 1; y <= FLOOR_Y + CLEAR_HEIGHT; y++) {
					BlockState wanted = air;
					if (y == FLOOR_Y && inside) {
						wanted = floor;
					} else if (border && y >= FLOOR_Y && y <= FLOOR_Y + WALL_HEIGHT) {
						wanted = wall;
					}
					pos.set(center.getX() + dx, y, center.getZ() + dz);
					if (level.getBlockState(pos) != wanted) {
						level.setBlock(pos, wanted, FLAGS);
					}
				}
			}
		}
		clearEntities(level, slot);
	}
}
