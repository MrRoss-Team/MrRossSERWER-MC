package pl.easytp.oneblock;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.economy.Shop;
import pl.easytp.session.PlayerState;
import pl.easytp.session.Realms;

import java.util.List;
import java.util.Optional;

public final class OneBlock {
	public static final int CENTER_Y = 100;
	private static final int RESCUE_Y = 40;
	private static final int CHEST_PERCENT = 4;
	private static final int MOB_PERCENT = 8;
	private static final int CHEST_SLOTS = 27;

	public record Spot(ServerLevel level, Vec3 pos) {
	}

	private OneBlock() {
	}

	private static boolean isOneBlock(Level level) {
		return level.dimension().equals(Realms.ONEBLOCK_LEVEL);
	}

	private static boolean active() {
		return EasyTP.islandsOrNull() != null && EasyTP.config().oneblock.enabled;
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!isOneBlock(level) || !active() || canBuild(player, pos)) {
				return true;
			}
			denied(player);
			return false;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (isOneBlock(level) && active() && player instanceof ServerPlayer serverPlayer) {
				onBreak((ServerLevel) level, serverPlayer, pos);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !isOneBlock(level) || !active()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (canBuild(player, pos) && canBuild(player, pos.relative(hit.getDirection()))) {
				return InteractionResult.PASS;
			}
			denied(player);
			return InteractionResult.FAIL;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !isOneBlock(level) || !active() || !(player.getItemInHand(hand).getItem() instanceof BucketItem)) {
				return InteractionResult.PASS;
			}
			if (canBuild(player, player.blockPosition())) {
				return InteractionResult.PASS;
			}
			denied(player);
			return InteractionResult.FAIL;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> guardEntity(player, level));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> guardEntity(player, level));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(isOneBlock(entity.level()) && entity instanceof Player && source.getEntity() instanceof Player));
	}

	private static InteractionResult guardEntity(Player player, Level level) {
		if (level.isClientSide() || !isOneBlock(level) || !active() || canBuild(player, player.blockPosition())) {
			return InteractionResult.PASS;
		}
		denied(player);
		return InteractionResult.FAIL;
	}

	private static void denied(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Chat.error(Msg.IS_NO_BUILD), true);
		}
	}

	public static boolean canBuild(Player player, BlockPos pos) {
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return true;
		}
		Optional<IslandStore.Island> island = EasyTP.islands().islandOf(player.getUUID());
		return island.isPresent() && IslandStore.inside(island.get(), pos.getX(), pos.getZ());
	}

	public static BlockPos centerOf(IslandStore.Island island) {
		IslandStore.Position center = IslandStore.center(island.index);
		return new BlockPos(center.x(), CENTER_Y, center.z());
	}

	public static Optional<Spot> spotOf(MinecraftServer server, IslandStore.Island island) {
		ServerLevel level = server.getLevel(Realms.ONEBLOCK_LEVEL);
		if (level == null) {
			return Optional.empty();
		}
		BlockPos center = centerOf(island);
		level.getChunk(center.getX() >> 4, center.getZ() >> 4);
		BlockPos stand = center.above();
		while (stand.getY() < CENTER_Y + 64 && !(level.getBlockState(stand).isAir() && level.getBlockState(stand.above()).isAir())) {
			stand = stand.above();
		}
		return Optional.of(new Spot(level, Vec3.atBottomCenterOf(stand)));
	}

	public static Optional<Spot> homeOf(ServerPlayer player) {
		return EasyTP.islands().islandOf(player.getUUID()).flatMap(island -> spotOf(player.level().getServer(), island));
	}

	public static void build(ServerLevel level, IslandStore.Island island) {
		BlockPos center = centerOf(island);
		level.getChunk(center.getX() >> 4, center.getZ() >> 4);
		level.setBlockAndUpdate(center.below(), Blocks.BEDROCK.defaultBlockState());
		placeChest(level, center, List.of(
			new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.BREAD, 8), new ItemStack(Items.OAK_SAPLING, 2),
			new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.TORCH, 8)));
	}

	public static void clear(ServerLevel level, IslandStore.Island island) {
		BlockPos center = centerOf(island);
		level.getChunk(center.getX() >> 4, center.getZ() >> 4);
		if (level.getBlockEntity(center) instanceof ChestBlockEntity chest) {
			chest.clearContent();
		}
		level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(center.below(), Blocks.AIR.defaultBlockState());
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (IslandStore.inside(island, player.getBlockX(), player.getBlockZ())) {
				PlayerState.teleportToWorldSpawn(player);
			}
		}
	}

	private static void placeChest(ServerLevel level, BlockPos pos, List<ItemStack> items) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			int slot = 0;
			for (ItemStack item : items) {
				if (slot < CHEST_SLOTS) {
					chest.setItem(slot++, item);
				}
			}
		}
	}

	private static void onBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Optional<IslandStore.Island> found = EasyTP.islands().islandAt(pos.getX(), pos.getZ());
		if (found.isEmpty() || !centerOf(found.get()).equals(pos)) {
			return;
		}
		IslandStore.Island island = found.get();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1))) {
			item.setNoPickUpDelay();
			item.setDeltaMovement(Vec3.ZERO);
			item.setPos(player.position());
		}
		int before = island.level();
		island.blocks++;
		regenerate(level, island, pos);
		int after = island.level();
		if (after > before) {
			levelUp(level.getServer(), island, before, after);
		}
		if (island.blocks % 20 == 0) {
			EasyTP.islands().touch();
		}
	}

	private static void levelUp(MinecraftServer server, IslandStore.Island island, int before, int after) {
		long reward = EasyTP.config().oneblock.levelRewardPerLevel * after;
		EasyTP.islands().deposit(island, reward);
		broadcast(server, island, Chat.ok(Msg.IS_LEVEL_UP, after, Shop.money(reward)));
		Themes.Theme previous = Themes.best(before);
		Themes.Theme current = Themes.best(after);
		if (previous != current) {
			broadcast(server, island, Chat.ok(Msg.IS_NEW_THEME, current.name().text()));
		}
	}

	public static void broadcast(MinecraftServer server, IslandStore.Island island, net.minecraft.network.chat.Component message) {
		for (java.util.UUID id : island.members.keySet()) {
			ServerPlayer member = server.getPlayerList().getPlayer(id);
			if (member != null) {
				member.sendSystemMessage(message);
			}
		}
	}

	private static void regenerate(ServerLevel level, IslandStore.Island island, BlockPos center) {
		RandomSource random = level.getRandom();
		Themes.Theme theme = Themes.active(island);
		if (!level.getBlockState(center.below()).is(Blocks.BEDROCK)) {
			level.setBlockAndUpdate(center.below(), Blocks.BEDROCK.defaultBlockState());
		}
		int roll = random.nextInt(100);
		if (roll < CHEST_PERCENT) {
			placeChest(level, center, theme.rollLoot(random));
			return;
		}
		BlockState next = theme.rollBlock(random).defaultBlockState();
		if (next.hasProperty(LeavesBlock.PERSISTENT)) {
			next = next.setValue(LeavesBlock.PERSISTENT, true);
		}
		level.setBlockAndUpdate(center, next);
		if (roll < CHEST_PERCENT + MOB_PERCENT) {
			theme.rollMob(random).spawn(level, center.above(), EntitySpawnReason.TRIGGERED);
		}
	}

	public static void tick(MinecraftServer server) {
		if (!active() || server.getTickCount() % 10 != 0) {
			return;
		}
		ServerLevel level = server.getLevel(Realms.ONEBLOCK_LEVEL);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.getY() < RESCUE_Y) {
				rescue(server, player);
			}
		}
		if (server.getTickCount() % 40 == 0) {
			for (IslandStore.Island island : EasyTP.islands().all()) {
				BlockPos center = centerOf(island);
				if (level.isLoaded(center) && level.getBlockState(center).isAir()) {
					regenerate(level, island, center);
				}
			}
		}
	}

	private static void rescue(MinecraftServer server, ServerPlayer player) {
		Optional<IslandStore.Island> island = EasyTP.islands().islandAt(player.getBlockX(), player.getBlockZ())
			.or(() -> EasyTP.islands().islandOf(player.getUUID()));
		Optional<Spot> spot = island.flatMap(found -> spotOf(server, found));
		if (spot.isPresent()) {
			PlayerState.teleport(player, spot.get().level(), spot.get().pos(), player.getYRot(), 0);
			player.sendSystemMessage(Chat.info(Msg.IS_RESCUED));
		} else {
			PlayerState.teleportToWorldSpawn(player);
		}
	}
}
