package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.data.Location;
import pl.easytp.teleport.Locations;
import pl.easytp.teleport.SafeSpot;
import pl.easytp.teleport.TeleportService.Destination;
import pl.easytp.teleport.TeleportService.TeleportException;

import java.util.Optional;

public final class SpawnCommands {
	private SpawnCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawn")
			.executes(SpawnCommands::spawn));

		dispatcher.register(Commands.literal("setspawn")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.executes(SpawnCommands::setSpawn));
	}

	private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		EasyTP.teleports().begin(player, () -> destination(server), Chat.ok(Msg.SPAWN_TELEPORTED));
		return Command.SINGLE_SUCCESS;
	}

	private static int setSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		EasyTP.store().setSpawn(Locations.of(player));
		player.sendSystemMessage(Chat.ok(Msg.SPAWN_SET));
		return Command.SINGLE_SUCCESS;
	}

	private static Destination destination(MinecraftServer server) throws TeleportException {
		Optional<Location> custom = EasyTP.store().spawn();
		if (custom.isPresent()) {
			return Locations.resolve(server, custom.get(), true);
		}

		LevelData.RespawnData respawn = server.getRespawnData();
		ServerLevel level = server.getLevel(respawn.dimension());
		if (level == null) {
			level = server.overworld();
		}
		BlockPos pos = respawn.pos();
		Vec3 spot = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (SafeSpot.find(level, spot).isEmpty()) {
			spot = SafeSpot.surface(level, spot.x, spot.z).orElseThrow(() -> new TeleportException(Msg.SPAWN_MISSING.text()));
		}
		return new Destination(level, spot, respawn.yaw(), 0.0F, true);
	}
}
