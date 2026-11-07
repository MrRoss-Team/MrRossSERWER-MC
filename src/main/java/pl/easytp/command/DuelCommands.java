package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.duel.DuelManager;
import pl.easytp.duel.Elo;
import pl.easytp.duel.Kits;
import pl.easytp.duel.Lobby;
import pl.easytp.player.PlayerData;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class DuelCommands {
	private DuelCommands() {
	}

	private static boolean enabled(CommandSourceStack source) {
		return EasyTP.config().duels.enabled;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("duel")
			.requires(DuelCommands::enabled)
			.then(Commands.literal("akceptuj").then(player().executes(DuelCommands::accept)))
			.then(Commands.literal("accept").then(player().executes(DuelCommands::accept)))
			.then(Commands.literal("odrzuc").then(player().executes(DuelCommands::deny)))
			.then(Commands.literal("toggle").executes(DuelCommands::toggle))
			.then(player()
				.executes(context -> challenge(context, Kits.DEFAULT, 1))
				.then(kit()
					.executes(context -> challenge(context, StringArgumentType.getString(context, "zestaw"), 1))
					.then(Commands.argument("rundy", IntegerArgumentType.integer(1, 9))
						.executes(context -> challenge(context, StringArgumentType.getString(context, "zestaw"), IntegerArgumentType.getInteger(context, "rundy")))))));

		for (String name : List.of("ranked", "queue", "kolejka")) {
			dispatcher.register(Commands.literal(name)
				.requires(DuelCommands::enabled)
				.executes(context -> queue(context.getSource(), Kits.DEFAULT))
				.then(Commands.literal("leave").executes(context -> leaveQueue(context.getSource())))
				.then(Commands.literal("wyjdz").executes(context -> leaveQueue(context.getSource())))
				.then(kit().executes(context -> queue(context.getSource(), StringArgumentType.getString(context, "zestaw")))));
		}

		for (String name : List.of("surrender", "ff", "poddaj")) {
			dispatcher.register(Commands.literal(name)
				.requires(DuelCommands::enabled)
				.executes(context -> {
					if (!EasyTP.duels().surrender(context.getSource().getPlayerOrException())) {
						throw Chat.fail(Msg.DUEL_NOT_IN);
					}
					return Command.SINGLE_SUCCESS;
				}));
		}

		dispatcher.register(Commands.literal("previewkit")
			.requires(DuelCommands::enabled)
			.then(kit().executes(context -> {
				Kits.preview(context.getSource().getPlayerOrException(), kitOf(StringArgumentType.getString(context, "zestaw")));
				return Command.SINGLE_SUCCESS;
			})));

		dispatcher.register(Commands.literal("kity")
			.requires(DuelCommands::enabled)
			.executes(context -> {
				context.getSource().sendSuccess(() -> Chat.info(Msg.DUEL_KITS, String.join(", ", Kits.ids())), false);
				return Command.SINGLE_SUCCESS;
			}));

		dispatcher.register(Commands.literal("elo")
			.requires(DuelCommands::enabled)
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				return sendElo(context.getSource(), EasyTP.players().get(player.getUUID()), player.getPlainTextName());
			})
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(context -> {
					Collection<NameAndId> found = GameProfileArgument.getGameProfiles(context, "gracz");
					if (found.size() != 1) {
						throw Chat.fail(Msg.MOD_ONE_PLAYER);
					}
					NameAndId target = found.iterator().next();
					return sendElo(context.getSource(), EasyTP.players().touch(target.id(), target.name()), target.name());
				})));

		for (String name : List.of("ranks", "dywizje")) {
			dispatcher.register(Commands.literal(name)
				.requires(DuelCommands::enabled)
				.executes(context -> {
					context.getSource().sendSuccess(() -> Chat.info(Msg.DUEL_RANKS_HEADER), false);
					for (Elo.Division division : Elo.Division.values()) {
						String range = division.max == Integer.MAX_VALUE ? division.min + "+" : division.min + "-" + division.max;
						context.getSource().sendSuccess(() -> Chat.info(Msg.DUEL_RANKS_LINE, division.title.text(), range), false);
					}
					return Command.SINGLE_SUCCESS;
				}));
		}
	}

	private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
		return Commands.argument("gracz", EntityArgument.player());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> kit() {
		return Commands.argument("zestaw", StringArgumentType.word())
			.suggests((context, builder) -> SharedSuggestionProvider.suggest(Kits.ids(), builder));
	}

	private static Kits.Kit kitOf(String id) throws CommandSyntaxException {
		Optional<Kits.Kit> kit = Kits.byId(id);
		if (kit.isEmpty()) {
			throw Chat.fail(Msg.DUEL_UNKNOWN_KIT, id, String.join(", ", Kits.ids()));
		}
		return kit.get();
	}

	private static int challenge(CommandContext<CommandSourceStack> context, String kitId, int rounds) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
		Kits.Kit kit = kitOf(kitId);
		DuelManager duels = EasyTP.duels();
		if (player == target) {
			throw Chat.fail(Msg.DUEL_SELF);
		}
		if (duels.inDuel(target.getUUID())) {
			throw Chat.fail(Msg.DUEL_BUSY_OTHER, target.getPlainTextName());
		}
		if (duels.lobby().isRefusing(target.getUUID())) {
			throw Chat.fail(Msg.DUEL_REFUSES, target.getPlainTextName());
		}
		int bestOf = rounds % 2 == 0 ? rounds + 1 : rounds;
		int seconds = EasyTP.config().duels.challengeSeconds;
		int wins = bestOf / 2 + 1;
		duels.lobby().challenge(player.getUUID(), target.getUUID(), kit.id(), bestOf, seconds * 1000L);
		context.getSource().sendSuccess(() -> Chat.ok(Msg.DUEL_SENT, target.getPlainTextName(), kit.id(), wins, seconds), false);
		String name = player.getPlainTextName();
		target.sendSystemMessage(Chat.info(Msg.DUEL_RECEIVED, name, kit.id(), wins)
			.append(Component.literal(" "))
			.append(Chat.button(Msg.DUEL_ACCEPT_BUTTON.text(), ChatFormatting.GREEN, true, "/duel akceptuj " + name, Msg.DUEL_ACCEPT_BUTTON.text()))
			.append(Component.literal(" "))
			.append(Chat.button(Msg.DUEL_DENY_BUTTON.text(), ChatFormatting.RED, true, "/duel odrzuc " + name, Msg.DUEL_DENY_BUTTON.text())));
		return Command.SINGLE_SUCCESS;
	}

	private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerPlayer from = EntityArgument.getPlayer(context, "gracz");
		DuelManager duels = EasyTP.duels();
		Optional<Lobby.Challenge> challenge = duels.lobby().take(from.getUUID(), player.getUUID());
		if (challenge.isEmpty()) {
			throw Chat.fail(Msg.DUEL_NO_CHALLENGE, from.getPlainTextName());
		}
		if (duels.inDuel(from.getUUID())) {
			throw Chat.fail(Msg.DUEL_BUSY_OTHER, from.getPlainTextName());
		}
		Msg error = duels.start(context.getSource().getServer(), from, player, kitOf(challenge.get().kit()), false, challenge.get().bestOf());
		if (error != null) {
			throw Chat.fail(error);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int deny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerPlayer from = EntityArgument.getPlayer(context, "gracz");
		if (EasyTP.duels().lobby().take(from.getUUID(), player.getUUID()).isEmpty()) {
			throw Chat.fail(Msg.DUEL_NO_CHALLENGE, from.getPlainTextName());
		}
		context.getSource().sendSuccess(() -> Chat.info(Msg.DUEL_DENIED, from.getPlainTextName()), false);
		from.sendSystemMessage(Chat.info(Msg.DUEL_DENIED_NOTICE, player.getPlainTextName()));
		return Command.SINGLE_SUCCESS;
	}

	private static int toggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean refusing = EasyTP.duels().lobby().toggleRefusing(player.getUUID());
		context.getSource().sendSuccess(() -> Chat.info(refusing ? Msg.DUEL_TOGGLE_OFF : Msg.DUEL_TOGGLE_ON), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int queue(CommandSourceStack source, String kitId) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Kits.Kit kit = kitOf(kitId);
		DuelManager duels = EasyTP.duels();
		if (duels.lobby().isQueued(player.getUUID())) {
			throw Chat.fail(Msg.DUEL_QUEUE_ALREADY);
		}
		duels.lobby().enqueue(player.getUUID(), kit.id(), EasyTP.players().touch(player.getUUID(), player.getPlainTextName()).elo);
		source.sendSuccess(() -> Chat.info(Msg.DUEL_QUEUE_JOIN, kit.id()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int leaveQueue(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!EasyTP.duels().lobby().dequeue(player.getUUID())) {
			throw Chat.fail(Msg.DUEL_QUEUE_NOT);
		}
		source.sendSuccess(() -> Chat.info(Msg.DUEL_QUEUE_LEFT), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int sendElo(CommandSourceStack source, PlayerData data, String name) {
		source.sendSuccess(() -> Chat.info(Msg.DUEL_STATS, name, data.elo, Elo.divisionName(data.elo), data.duelWins, data.duelLosses), false);
		return Command.SINGLE_SUCCESS;
	}
}
