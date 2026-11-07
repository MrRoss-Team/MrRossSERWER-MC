package pl.easytp.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class SafeSpot {
	private static final int SEARCH_RANGE = 4;
	private static final int MAX_FALL = 5;

	private SafeSpot() {
	}

	public static Optional<Vec3> find(ServerLevel level, Vec3 wanted) {
		if (isSafe(level, wanted)) {
			return Optional.of(wanted);
		}
		for (int i = 1; i <= SEARCH_RANGE; i++) {
			Vec3 up = wanted.add(0, i, 0);
			if (isSafe(level, up)) {
				return Optional.of(up);
			}
			Vec3 down = wanted.add(0, -i, 0);
			if (isSafe(level, down)) {
				return Optional.of(down);
			}
		}
		return Optional.empty();
	}

	public static Optional<Vec3> surface(ServerLevel level, double x, double z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		Vec3 spot = new Vec3(x, y, z);
		return isSafe(level, spot) ? Optional.of(spot) : Optional.empty();
	}

	static boolean isSafe(ServerLevel level, Vec3 pos) {
		BlockPos feet = BlockPos.containing(pos.x, pos.y, pos.z);
		if (!Level.isInSpawnableBounds(feet) || !Level.isInSpawnableBounds(feet.above())) {
			return false;
		}
		if (!level.noCollision(EntityType.PLAYER.getDimensions().makeBoundingBox(pos))) {
			return false;
		}
		if (isHazard(level, feet) || isHazard(level, feet.above())) {
			return false;
		}

		BlockPos.MutableBlockPos below = feet.mutable();
		for (int i = 0; i < MAX_FALL; i++) {
			below.move(Direction.DOWN);
			if (below.getY() < level.getMinY()) {
				return false;
			}
			BlockState state = level.getBlockState(below);
			if (state.getFluidState().is(FluidTags.LAVA)) {
				return false;
			}
			if (!state.getCollisionShape(level, below).isEmpty()) {
				return !isHarmfulGround(state);
			}
			if (!state.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isHazard(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.FIRE)
			|| state.is(BlockTags.CAMPFIRES)
			|| state.getFluidState().is(FluidTags.LAVA)
			|| state.is(Blocks.CACTUS)
			|| state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.WITHER_ROSE)
			|| state.is(Blocks.POWDER_SNOW);
	}

	private static boolean isHarmfulGround(BlockState state) {
		return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS);
	}
}
