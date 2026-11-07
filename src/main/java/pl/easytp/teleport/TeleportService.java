package pl.easytp.teleport;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pl.easytp.Chat;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.util.Cooldowns;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TeleportService {
	private static final double MOVE_LIMIT_SQ = 0.5 * 0.5;

	public record Destination(ServerLevel level, Vec3 pos, float yaw, float pitch, boolean checkSafety) {
	}

	@FunctionalInterface
	public interface DestinationResolver {
		Destination resolve() throws TeleportException;
	}

	public static final class TeleportException extends Exception {
		public TeleportException(String message) {
			super(message, null, false, false);
		}
	}

	private static final class Warmup {
		final DestinationResolver resolver;
		final Component done;
		final ResourceKey<Level> dimension;
		final Vec3 start;
		final long endsAt;
		float health;

		Warmup(DestinationResolver resolver, Component done, ResourceKey<Level> dimension, Vec3 start, float health, long endsAt) {
			this.resolver = resolver;
			this.done = done;
			this.dimension = dimension;
			this.start = start;
			this.health = health;
			this.endsAt = endsAt;
		}
	}

	private final Supplier<EasyTPConfig> config;
	private final Cooldowns cooldowns = new Cooldowns(System::currentTimeMillis);
	private final Map<UUID, Warmup> warmups = new HashMap<>();

	public TeleportService(Supplier<EasyTPConfig> config) {
		this.config = config;
	}

	public static boolean isOperator(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public boolean bypasses(ServerPlayer player) {
		return config.get().opsBypassLimits && isOperator(player);
	}

	public void begin(ServerPlayer player, DestinationResolver resolver, Component done) {
		UUID id = player.getUUID();
		if (warmups.containsKey(id)) {
			player.sendSystemMessage(Chat.error(Msg.WARMUP_ALREADY));
			return;
		}

		boolean bypass = bypasses(player);
		if (!bypass) {
			long left = cooldowns.remainingMillis(id);
			if (left > 0) {
				player.sendSystemMessage(Chat.error(Msg.COOLDOWN, (int) Math.ceil(left / 1000.0)));
				return;
			}
		}

		Destination destination;
		try {
			destination = resolver.resolve();
		} catch (TeleportException e) {
			player.sendSystemMessage(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
			return;
		}

		int warmupSeconds = bypass ? 0 : config.get().warmupSeconds;
		if (warmupSeconds <= 0) {
			perform(player, destination, done);
			return;
		}
		long endsAt = System.currentTimeMillis() + warmupSeconds * 1000L;
		warmups.put(id, new Warmup(resolver, done, player.level().dimension(), player.position(), player.getHealth(), endsAt));
		player.sendSystemMessage(Chat.info(Msg.WARMUP_STARTED, warmupSeconds));
	}

	public void tick(MinecraftServer server) {
		if (warmups.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, Warmup>> it = warmups.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Warmup> entry = it.next();
			Warmup warmup = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			if (player.level().dimension() != warmup.dimension || player.position().distanceToSqr(warmup.start) > MOVE_LIMIT_SQ) {
				it.remove();
				player.sendSystemMessage(Chat.error(Msg.WARMUP_CANCELLED_MOVED));
				continue;
			}
			if (player.getHealth() < warmup.health - 0.001f) {
				it.remove();
				player.sendSystemMessage(Chat.error(Msg.WARMUP_CANCELLED_DAMAGE));
				continue;
			}
			warmup.health = player.getHealth();

			if (now >= warmup.endsAt) {
				it.remove();
				try {
					perform(player, warmup.resolver.resolve(), warmup.done);
				} catch (TeleportException e) {
					player.sendSystemMessage(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	public void forget(UUID player) {
		warmups.remove(player);
	}

	private void perform(ServerPlayer player, Destination destination, Component done) {
		Vec3 target = destination.pos();
		if (destination.checkSafety()) {
			Optional<Vec3> safe = SafeSpot.find(destination.level(), target);
			if (safe.isEmpty()) {
				player.sendSystemMessage(Chat.error(Msg.UNSAFE_DESTINATION));
				return;
			}
			target = safe.get();
		}
		if (!Level.isInSpawnableBounds(BlockPos.containing(target.x, target.y, target.z))) {
			player.sendSystemMessage(Chat.error(Msg.UNSAFE_DESTINATION));
			return;
		}

		float yaw = Mth.wrapDegrees(destination.yaw());
		float pitch = Mth.clamp(destination.pitch(), -90.0F, 90.0F);
		boolean teleported = player.teleportTo(destination.level(), target.x, target.y, target.z,
			EnumSet.noneOf(Relative.class), yaw, pitch, true);
		if (!teleported) {
			player.sendSystemMessage(Chat.error(Msg.UNSAFE_DESTINATION));
			return;
		}

		player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
		player.setOnGround(true);
		player.resetFallDistance();

		if (!bypasses(player)) {
			cooldowns.start(player.getUUID(), config.get().cooldownSeconds * 1000L);
		}
		player.sendSystemMessage(done);
	}
}
