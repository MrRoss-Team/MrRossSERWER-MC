package pl.easytp.teleport;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import pl.easytp.Msg;
import pl.easytp.data.Location;
import pl.easytp.teleport.TeleportService.Destination;
import pl.easytp.teleport.TeleportService.TeleportException;

import java.util.Locale;

public final class Locations {
	private Locations() {
	}

	public static Location of(ServerPlayer player) {
		return new Location(
			player.level().dimension().identifier().toString(),
			player.getX(), player.getY(), player.getZ(),
			player.getYRot(), player.getXRot());
	}

	public static Destination resolve(MinecraftServer server, Location location, boolean checkSafety) throws TeleportException {
		ServerLevel level = null;
		Identifier id = Identifier.tryParse(location.dimension());
		if (id != null) {
			level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}
		if (level == null) {
			throw new TeleportException(Msg.DIMENSION_MISSING.text());
		}
		return new Destination(level, new Vec3(location.x(), location.y(), location.z()), location.yaw(), location.pitch(), checkSafety);
	}

	public static String dimensionLabel(String dimensionId) {
		return switch (dimensionId) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimensionId;
		};
	}

	public static String describe(Location location) {
		return String.format(Locale.ROOT, "%s (%d, %d, %d)",
			dimensionLabel(location.dimension()),
			(int) Math.floor(location.x()), (int) Math.floor(location.y()), (int) Math.floor(location.z()));
	}
}
