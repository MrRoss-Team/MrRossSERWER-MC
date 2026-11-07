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
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.moderation.BanSupport;
import pl.easytp.moderation.ChatGuard;
import pl.easytp.moderation.Durations;
import pl.easytp.moderation.ModerationStore;
import pl.easytp.moderation.ModerationStore.HistoryEntry;
import pl.easytp.moderation.ModerationStore.Mute;
import pl.easytp.moderation.ModerationStore.Type;
import pl.easytp.moderation.ModerationStore.Warning;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ModerationCommands {
	private static final int MAX_REASON_LENGTH = 200;
	private static final int HISTORY_LINES = 20;
	private static final DateTimeFormatter DATE =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

	private static final SuggestionProvider<CommandSourceStack> DURATIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(List.of("10m", "1h", "1d", "7d", "30d", "perm"), builder);
	private static final SuggestionProvider<CommandSourceStack> BANNED = (context, builder) ->
		SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerList().getBans().getUserList(), builder);
	private static final SuggestionProvider<CommandSourceStack> MUTED = (context, builder) ->
		SharedSuggestionProvider.suggest(EasyTP.moderation().activeMutes().stream().map(Mute::name).toList(), builder);

	private ModerationCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tempban")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.then(Commands.argument("czas", StringArgumentType.word())
					.suggests(DURATIONS)
					.executes(context -> tempBan(context, null))
					.then(Commands.argument("powod", StringArgumentType.greedyString())
						.executes(context -> tempBan(context, StringArgumentType.getString(context, "powod")))))));

		dispatcher.register(Commands.literal("unban")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.suggests(BANNED)
				.executes(ModerationCommands::unban)));

		dispatcher.register(Commands.literal("mute")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(context -> mute(context, Durations.PERMANENT, null))
				.then(Commands.argument("powod", StringArgumentType.greedyString())
					.executes(context -> mute(context, Durations.PERMANENT, StringArgumentType.getString(context, "powod"))))));

		dispatcher.register(Commands.literal("tempmute")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.then(Commands.argument("czas", StringArgumentType.word())
					.suggests(DURATIONS)
					.executes(context -> mute(context, duration(StringArgumentType.getString(context, "czas")), null))
					.then(Commands.argument("powod", StringArgumentType.greedyString())
						.executes(context -> mute(context, duration(StringArgumentType.getString(context, "czas")),
							StringArgumentType.getString(context, "powod")))))));

		dispatcher.register(Commands.literal("unmute")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.suggests(MUTED)
				.executes(ModerationCommands::unmute)));

		dispatcher.register(Commands.literal("mutes")
			.requires(ModerationCommands::isStaff)
			.executes(ModerationCommands::listMutes));

		dispatcher.register(Commands.literal("warn")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.then(Commands.argument("powod", StringArgumentType.greedyString())
					.executes(ModerationCommands::warn))));

		dispatcher.register(Commands.literal("warnings")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ModerationCommands::warnings)));

		dispatcher.register(Commands.literal("clearwarnings")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ModerationCommands::clearWarnings)));

		dispatcher.register(Commands.literal("history")
			.requires(ModerationCommands::isStaff)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ModerationCommands::history)));
	}

	public static boolean isStaff(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
	}

	private static int tempBan(CommandContext<CommandSourceStack> context, String rawReason) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, true);
		long duration = duration(StringArgumentType.getString(context, "czas"));
		String reason = reason(rawReason);
		String by = source.getTextName();

		MinecraftServer server = source.getServer();
		BanSupport.purgeExpired(server);
		UserBanList bans = server.getPlayerList().getBans();
		if (bans.isBanned(target)) {
			throw Chat.fail(Msg.BAN_ALREADY, target.name());
		}

		Date now = new Date();
		Date expires = duration == Durations.PERMANENT ? null : new Date(now.getTime() + duration);
		bans.add(new UserBanListEntry(target, now, by, expires, reason));
		EasyTP.moderation().log(Type.BAN, target.id(), target.name(), by, reason, storedDuration(duration));

		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.connection.disconnect(BanSupport.kickMessage(reason, expires));
		}

		String length = describe(duration);
		source.sendSuccess(() -> Chat.ok(Msg.BAN_DONE, target.name(), length, reason), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int unban(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, false);

		MinecraftServer server = source.getServer();
		BanSupport.purgeExpired(server);
		UserBanList bans = server.getPlayerList().getBans();
		if (!bans.isBanned(target)) {
			throw Chat.fail(Msg.UNBAN_NOT_BANNED, target.name());
		}
		bans.remove(target);
		EasyTP.moderation().log(Type.UNBAN, target.id(), target.name(), source.getTextName(), "", 0);

		source.sendSuccess(() -> Chat.ok(Msg.UNBAN_DONE, target.name()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int mute(CommandContext<CommandSourceStack> context, long duration, String rawReason) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, true);
		String reason = reason(rawReason);
		String by = source.getTextName();

		ModerationStore store = EasyTP.moderation();
		store.mute(target.id(), target.name(), by, reason, storedDuration(duration));
		store.log(Type.MUTE, target.id(), target.name(), by, reason, storedDuration(duration));

		String length = describe(duration);
		ServerPlayer online = source.getServer().getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.error(Msg.MUTE_NOTICE, length, reason));
		}
		source.sendSuccess(() -> Chat.ok(Msg.MUTE_DONE, target.name(), length, reason), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int unmute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, false);

		ModerationStore store = EasyTP.moderation();
		if (!store.unmute(target.id())) {
			throw Chat.fail(Msg.UNMUTE_NOT_MUTED, target.name());
		}
		store.log(Type.UNMUTE, target.id(), target.name(), source.getTextName(), "", 0);

		ServerPlayer online = source.getServer().getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.ok(Msg.UNMUTE_NOTICE));
		}
		source.sendSuccess(() -> Chat.ok(Msg.UNMUTE_DONE, target.name()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int listMutes(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		List<Mute> mutes = EasyTP.moderation().activeMutes();
		if (mutes.isEmpty()) {
			source.sendSuccess(() -> Chat.info(Msg.MUTES_NONE), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal(Msg.MUTES_HEADER.text(mutes.size())).withStyle(ChatFormatting.GOLD), false);
		for (Mute mute : mutes) {
			String line = " - " + Msg.MUTES_ENTRY.text(mute.name(), ChatGuard.remaining(mute), mute.reason());
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return mutes.size();
	}

	private static int warn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, true);
		String reason = reason(StringArgumentType.getString(context, "powod"));
		String by = source.getTextName();

		ModerationStore store = EasyTP.moderation();
		int total = store.warn(target.id(), by, reason);
		store.log(Type.WARN, target.id(), target.name(), by, reason, 0);

		ServerPlayer online = source.getServer().getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Component.literal(Msg.WARN_NOTICE.text(by, reason, total)).withStyle(ChatFormatting.GOLD));
		}
		source.sendSuccess(() -> Chat.ok(Msg.WARN_DONE, target.name(), total, reason), true);
		return total;
	}

	private static int warnings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, false);
		List<Warning> list = EasyTP.moderation().warningsOf(target.id());
		if (list.isEmpty()) {
			source.sendSuccess(() -> Chat.info(Msg.WARNINGS_NONE, target.name()), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal(Msg.WARNINGS_HEADER.text(target.name(), list.size())).withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < list.size(); i++) {
			Warning warning = list.get(i);
			String line = (i + 1) + ". " + date(warning.time()) + " " + warning.by() + ": " + warning.reason();
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return list.size();
	}

	private static int clearWarnings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, false);

		ModerationStore store = EasyTP.moderation();
		int removed = store.clearWarnings(target.id());
		if (removed == 0) {
			source.sendSuccess(() -> Chat.info(Msg.WARNINGS_NONE, target.name()), false);
			return 0;
		}
		store.log(Type.CLEAR_WARNINGS, target.id(), target.name(), source.getTextName(), "", 0);
		source.sendSuccess(() -> Chat.ok(Msg.WARNINGS_CLEARED, target.name(), removed), true);
		return removed;
	}

	private static int history(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = target(context, false);
		List<HistoryEntry> all = EasyTP.moderation().historyOf(target.id());
		if (all.isEmpty()) {
			source.sendSuccess(() -> Chat.info(Msg.HISTORY_NONE, target.name()), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal(Msg.HISTORY_HEADER.text(target.name(), all.size())).withStyle(ChatFormatting.GOLD), false);
		int from = Math.max(0, all.size() - HISTORY_LINES);
		if (from > 0) {
			source.sendSuccess(() -> Chat.info(Msg.HISTORY_MORE, from), false);
		}
		for (int i = from; i < all.size(); i++) {
			HistoryEntry entry = all.get(i);
			source.sendSuccess(() -> historyLine(entry), false);
		}
		return all.size();
	}

	private static MutableComponent historyLine(HistoryEntry entry) {
		ChatFormatting color = switch (entry.type()) {
			case BAN -> ChatFormatting.RED;
			case MUTE -> ChatFormatting.GOLD;
			case WARN -> ChatFormatting.YELLOW;
			case UNBAN, UNMUTE, CLEAR_WARNINGS -> ChatFormatting.GREEN;
		};
		boolean hasLength = entry.type() == Type.BAN || entry.type() == Type.MUTE;
		String label = entry.type().name() + (hasLength
			? " (" + (entry.durationMillis() > 0 ? Durations.format(entry.durationMillis()) : Msg.MOD_PERMANENT.text()) + ")"
			: "");
		String details = " - " + entry.by() + (entry.reason().isEmpty() ? "" : ": " + entry.reason());
		return Component.literal(date(entry.time()) + " ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(label).withStyle(color))
			.append(Component.literal(details).withStyle(ChatFormatting.GRAY));
	}

	private static NameAndId target(CommandContext<CommandSourceStack> context, boolean forbidSelf) throws CommandSyntaxException {
		Collection<NameAndId> found = GameProfileArgument.getGameProfiles(context, "gracz");
		if (found.size() != 1) {
			throw Chat.fail(Msg.MOD_ONE_PLAYER);
		}
		NameAndId target = found.iterator().next();
		ServerPlayer self = context.getSource().getPlayer();
		if (forbidSelf && self != null && self.getUUID().equals(target.id())) {
			throw Chat.fail(Msg.MOD_SELF);
		}
		return target;
	}

	private static long duration(String text) throws CommandSyntaxException {
		long millis;
		try {
			millis = Durations.parse(text);
		} catch (IllegalArgumentException e) {
			throw Chat.fail(Msg.MOD_BAD_DURATION);
		}
		long max = EasyTP.config().maxPunishmentDays * 86_400_000L;
		if (millis != Durations.PERMANENT && millis > max) {
			throw Chat.fail(Msg.MOD_DURATION_TOO_LONG, Durations.format(max));
		}
		return millis;
	}

	private static long storedDuration(long duration) {
		return duration == Durations.PERMANENT ? 0 : duration;
	}

	private static String describe(long duration) {
		return duration == Durations.PERMANENT ? Msg.MOD_PERMANENT.text() : Durations.format(duration);
	}

	private static String reason(String raw) {
		if (raw == null) {
			return Msg.MOD_NO_REASON.text();
		}
		String cleaned = raw.replaceAll("\\s+", " ").trim();
		if (cleaned.isEmpty()) {
			return Msg.MOD_NO_REASON.text();
		}
		return cleaned.length() > MAX_REASON_LENGTH ? cleaned.substring(0, MAX_REASON_LENGTH) : cleaned;
	}

	private static String date(long millis) {
		return DATE.format(Instant.ofEpochMilli(millis));
	}
}
