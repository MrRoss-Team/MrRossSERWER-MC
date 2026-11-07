package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.data.DataStore;
import pl.easytp.data.Location;
import pl.easytp.teleport.Locations;
import pl.easytp.util.Names;

import java.util.Map;

public final class HomeCommands {
	private static final SuggestionProvider<CommandSourceStack> HOME_NAMES = (context, builder) -> {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return builder.buildFuture();
		}
		return SharedSuggestionProvider.suggest(EasyTP.store().homesOf(player.getUUID()).keySet(), builder);
	};

	private HomeCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("sethome")
			.executes(context -> setHome(context, Names.DEFAULT_HOME))
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(HOME_NAMES)
				.executes(context -> setHome(context, StringArgumentType.getString(context, "name")))));

		dispatcher.register(Commands.literal("home")
			.executes(context -> home(context, null))
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(HOME_NAMES)
				.executes(context -> home(context, StringArgumentType.getString(context, "name")))));

		dispatcher.register(Commands.literal("delhome")
			.executes(context -> delHome(context, Names.DEFAULT_HOME))
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(HOME_NAMES)
				.executes(context -> delHome(context, StringArgumentType.getString(context, "name")))));

		dispatcher.register(Commands.literal("homes")
			.executes(HomeCommands::list));
	}

	private static int setHome(CommandContext<CommandSourceStack> context, String rawName) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = Names.normalize(rawName);
		if (!Names.isValid(name)) {
			throw Chat.fail(Msg.HOME_BAD_NAME);
		}

		DataStore store = EasyTP.store();
		EasyTPConfig config = EasyTP.config();
		boolean exists = store.getHome(player.getUUID(), name).isPresent();
		if (!exists && !EasyTP.teleports().bypasses(player) && store.homeCount(player.getUUID()) >= config.maxHomes) {
			throw Chat.fail(Msg.HOME_LIMIT, config.maxHomes);
		}

		store.setHome(player.getUUID(), name, Locations.of(player));
		player.sendSystemMessage(Chat.ok(exists ? Msg.HOME_UPDATED : Msg.HOME_SET, name));
		return Command.SINGLE_SUCCESS;
	}

	private static int home(CommandContext<CommandSourceStack> context, String rawName) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Map<String, Location> homes = EasyTP.store().homesOf(player.getUUID());
		String name = rawName == null ? chooseDefault(homes) : Names.normalize(rawName);
		Location location = homes.get(name);
		if (location == null) {
			throw Chat.fail(Msg.HOME_NOT_FOUND, name);
		}

		MinecraftServer server = context.getSource().getServer();
		EasyTP.teleports().begin(player, () -> Locations.resolve(server, location, true), Chat.ok(Msg.HOME_TELEPORTED, name));
		return Command.SINGLE_SUCCESS;
	}

	private static String chooseDefault(Map<String, Location> homes) throws CommandSyntaxException {
		if (homes.isEmpty()) {
			throw Chat.fail(Msg.HOME_NONE);
		}
		if (homes.containsKey(Names.DEFAULT_HOME)) {
			return Names.DEFAULT_HOME;
		}
		if (homes.size() == 1) {
			return homes.keySet().iterator().next();
		}
		throw Chat.fail(Msg.HOME_PICK, String.join(", ", homes.keySet()));
	}

	private static int delHome(CommandContext<CommandSourceStack> context, String rawName) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = Names.normalize(rawName);
		if (!EasyTP.store().removeHome(player.getUUID(), name)) {
			throw Chat.fail(Msg.HOME_NOT_FOUND, name);
		}
		player.sendSystemMessage(Chat.ok(Msg.HOME_DELETED, name));
		return Command.SINGLE_SUCCESS;
	}

	private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Map<String, Location> homes = EasyTP.store().homesOf(player.getUUID());
		if (homes.isEmpty()) {
			throw Chat.fail(Msg.HOME_NONE);
		}

		String count = EasyTP.teleports().bypasses(player)
			? String.valueOf(homes.size())
			: homes.size() + "/" + EasyTP.config().maxHomes;
		player.sendSystemMessage(Component.literal(Msg.HOMES_HEADER.text(count)).withStyle(ChatFormatting.GOLD));
		for (Map.Entry<String, Location> home : homes.entrySet()) {
			MutableComponent line = Component.literal(" - ").withStyle(ChatFormatting.GRAY)
				.append(Chat.button(home.getKey(), ChatFormatting.AQUA, false, "/home " + home.getKey(), Msg.HOME_CLICK.text()))
				.append(Component.literal("  " + Locations.describe(home.getValue())).withStyle(ChatFormatting.GRAY));
			player.sendSystemMessage(line);
		}
		return Command.SINGLE_SUCCESS;
	}
}
