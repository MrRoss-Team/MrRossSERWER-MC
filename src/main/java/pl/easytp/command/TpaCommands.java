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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.teleport.TeleportService.Destination;
import pl.easytp.teleport.TeleportService.TeleportException;
import pl.easytp.tpa.TpaManager;
import pl.easytp.tpa.TpaManager.Request;
import pl.easytp.tpa.TpaManager.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TpaCommands {
	private static final long REQUEST_SPAM_GUARD_MILLIS = 3000;

	private static final SuggestionProvider<CommandSourceStack> SENDERS = (context, builder) -> {
		ServerPlayer me = context.getSource().getPlayer();
		if (me == null) {
			return builder.buildFuture();
		}
		MinecraftServer server = context.getSource().getServer();
		List<String> names = new ArrayList<>();
		for (Request request : EasyTP.tpa().incoming(me.getUUID())) {
			ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
			if (sender != null) {
				names.add(sender.getPlainTextName());
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private TpaCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tpa")
			.then(Commands.argument("player", EntityArgument.player())
				.executes(context -> send(context, Type.TPA))));

		dispatcher.register(Commands.literal("tpahere")
			.then(Commands.argument("player", EntityArgument.player())
				.executes(context -> send(context, Type.TPAHERE))));

		dispatcher.register(Commands.literal("tpaccept")
			.executes(context -> accept(context, null))
			.then(Commands.argument("player", StringArgumentType.word())
				.suggests(SENDERS)
				.executes(context -> accept(context, StringArgumentType.getString(context, "player")))));

		dispatcher.register(Commands.literal("tpdeny")
			.executes(context -> deny(context, null))
			.then(Commands.argument("player", StringArgumentType.word())
				.suggests(SENDERS)
				.executes(context -> deny(context, StringArgumentType.getString(context, "player")))));

		dispatcher.register(Commands.literal("tpcancel")
			.executes(TpaCommands::cancel));
	}

	private static int send(CommandContext<CommandSourceStack> context, Type type) throws CommandSyntaxException {
		ServerPlayer from = context.getSource().getPlayerOrException();
		ServerPlayer to = EntityArgument.getPlayer(context, "player");
		if (from.getUUID().equals(to.getUUID())) {
			throw Chat.fail(Msg.TPA_SELF);
		}
		if (EasyTP.requestGuard().remainingMillis(from.getUUID()) > 0) {
			throw Chat.fail(Msg.TPA_TOO_FAST);
		}
		EasyTP.requestGuard().start(from.getUUID(), REQUEST_SPAM_GUARD_MILLIS);

		int seconds = EasyTP.config().tpaTimeoutSeconds;
		EasyTP.tpa().create(from.getUUID(), to.getUUID(), type, seconds * 1000L);

		String fromName = from.getPlainTextName();
		String toName = to.getPlainTextName();
		from.sendSystemMessage(Chat.ok(type == Type.TPA ? Msg.TPA_SENT : Msg.TPAHERE_SENT, toName, seconds));

		MutableComponent notice = Chat.info(type == Type.TPA ? Msg.TPA_INCOMING : Msg.TPAHERE_INCOMING, fromName)
			.append(Component.literal(" "))
			.append(Chat.button(Msg.TPA_ACCEPT_BUTTON.text(), ChatFormatting.GREEN, true, "/tpaccept " + fromName, Msg.TPA_ACCEPT_HOVER.text()))
			.append(Component.literal(" "))
			.append(Chat.button(Msg.TPA_DENY_BUTTON.text(), ChatFormatting.RED, true, "/tpdeny " + fromName, Msg.TPA_DENY_HOVER.text()));
		to.sendSystemMessage(notice);
		return Command.SINGLE_SUCCESS;
	}

	private static int accept(CommandContext<CommandSourceStack> context, String senderName) throws CommandSyntaxException {
		ServerPlayer me = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		Request request = findIncoming(server, me, senderName);
		EasyTP.tpa().remove(request.sender());

		ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
		if (sender == null) {
			throw Chat.fail(Msg.PLAYER_OFFLINE);
		}
		me.sendSystemMessage(Chat.ok(Msg.TPA_ACCEPTED_BY_YOU, sender.getPlainTextName()));
		sender.sendSystemMessage(Chat.ok(Msg.TPA_ACCEPTED_NOTICE, me.getPlainTextName()));

		boolean senderMoves = request.type() == Type.TPA;
		ServerPlayer mover = senderMoves ? sender : me;
		ServerPlayer anchor = senderMoves ? me : sender;
		UUID anchorId = anchor.getUUID();
		EasyTP.teleports().begin(mover, () -> positionOf(server, anchorId),
			Chat.ok(Msg.TELEPORTED_TO_PLAYER, anchor.getPlainTextName()));
		return Command.SINGLE_SUCCESS;
	}

	private static int deny(CommandContext<CommandSourceStack> context, String senderName) throws CommandSyntaxException {
		ServerPlayer me = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		Request request = findIncoming(server, me, senderName);
		EasyTP.tpa().remove(request.sender());

		ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
		me.sendSystemMessage(Chat.ok(Msg.TPA_DENIED_BY_YOU, sender != null ? sender.getPlainTextName() : String.valueOf(senderName)));
		if (sender != null) {
			sender.sendSystemMessage(Chat.info(Msg.TPA_DENIED_NOTICE, me.getPlainTextName()));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer me = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		Request request = EasyTP.tpa().remove(me.getUUID())
			.orElseThrow(() -> Chat.fail(Msg.TPA_CANCEL_NONE));

		ServerPlayer target = server.getPlayerList().getPlayer(request.target());
		me.sendSystemMessage(Chat.ok(Msg.TPA_CANCELLED, target != null ? target.getPlainTextName() : "?"));
		if (target != null) {
			target.sendSystemMessage(Chat.info(Msg.TPA_CANCEL_NOTICE, me.getPlainTextName()));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static Request findIncoming(MinecraftServer server, ServerPlayer me, String senderName) throws CommandSyntaxException {
		TpaManager tpa = EasyTP.tpa();
		if (senderName == null) {
			return tpa.latestIncoming(me.getUUID()).orElseThrow(() -> Chat.fail(Msg.TPA_NONE));
		}
		ServerPlayer other = server.getPlayerList().getPlayerByName(senderName);
		if (other == null) {
			throw Chat.fail(Msg.TPA_NONE_FROM, senderName);
		}
		return tpa.incomingFrom(me.getUUID(), other.getUUID()).orElseThrow(() -> Chat.fail(Msg.TPA_NONE_FROM, senderName));
	}

	private static Destination positionOf(MinecraftServer server, UUID id) throws TeleportException {
		ServerPlayer anchor = server.getPlayerList().getPlayer(id);
		if (anchor == null) {
			throw new TeleportException(Msg.PLAYER_OFFLINE.text());
		}
		return new Destination(anchor.level(), anchor.position(), anchor.getYRot(), anchor.getXRot(), false);
	}
}
