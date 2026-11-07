package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.boxpvp.BoxPvp;
import pl.easytp.player.PlayerData;
import pl.easytp.session.PlayerState;
import pl.easytp.session.Realms;
import pl.easytp.teleport.TeleportService.Destination;
import pl.easytp.teleport.TeleportService.TeleportException;

public final class BoxCommands {
	private BoxCommands() {
	}

	private static boolean enabled(CommandSourceStack source) {
		return EasyTP.config().boxpvp.enabled;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("box")
			.requires(BoxCommands::enabled)
			.executes(BoxCommands::enter)
			.then(Commands.literal("wyjdz").executes(BoxCommands::leave))
			.then(Commands.literal("staty").executes(BoxCommands::stats)));

		dispatcher.register(Commands.literal("kit")
			.requires(BoxCommands::enabled)
			.executes(context -> {
				ServerPlayer player = inside(context);
				return EasyTP.box().claimKit(player) ? Command.SINGLE_SUCCESS : 0;
			}));

		dispatcher.register(Commands.literal("ulepsz")
			.requires(BoxCommands::enabled)
			.executes(context -> {
				EasyTP.box().openUpgrades(inside(context));
				return Command.SINGLE_SUCCESS;
			}));

		dispatcher.register(Commands.literal("warpy")
			.requires(BoxCommands::enabled)
			.executes(context -> {
				EasyTP.box().openWarps(inside(context));
				return Command.SINGLE_SUCCESS;
			}));
	}

	private static ServerPlayer inside(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		if (Realms.of(player) != Realms.Realm.BOXPVP) {
			throw Chat.fail(Msg.BOX_ONLY_INSIDE);
		}
		return player;
	}

	private static Destination destination(MinecraftServer server) throws TeleportException {
		BoxPvp.Spot spot = EasyTP.box().spawn(server).orElseThrow(() -> new TeleportException(Msg.BOX_NO_WORLD.text()));
		return new Destination(spot.level(), spot.pos(), 0, 0, false);
	}

	private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		EasyTP.teleports().begin(player, () -> destination(server), Chat.ok(Msg.BOX_WELCOME));
		return Command.SINGLE_SUCCESS;
	}

	private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		PlayerState.teleportToWorldSpawn(inside(context));
		return Command.SINGLE_SUCCESS;
	}

	private static int stats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		PlayerData data = EasyTP.players().touch(player.getUUID(), player.getPlainTextName());
		context.getSource().sendSuccess(() -> Chat.info(Msg.BOX_STATS, data.boxKills, data.boxDeaths, data.boxStreak, data.boxBestStreak), false);
		return Command.SINGLE_SUCCESS;
	}
}
