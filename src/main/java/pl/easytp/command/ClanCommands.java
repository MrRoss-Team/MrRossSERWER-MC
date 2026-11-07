package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.clan.ClanStore;
import pl.easytp.clan.ClanTeams;
import pl.easytp.clan.ClanTop;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.economy.Shop;
import pl.easytp.moderation.ChatGuard;
import pl.easytp.player.PlayerStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ClanCommands {
	private ClanCommands() {
	}

	private static boolean enabled(CommandSourceStack source) {
		return EasyTP.config().clans.enabled;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("klan")
			.requires(ClanCommands::enabled)
			.executes(ClanCommands::showHome);
		for (ArgumentBuilder<CommandSourceStack, ?> node : List.of(
			create("zaloz"), invite("zapros"), join("dolacz"), kick("wyrzuc"), leave("opusc"), deputy("zastepca"), transfer("lider"), disband("rozwiaz"),
			Commands.literal("lista").executes(ClanCommands::list),
			Commands.literal("info")
				.executes(ClanCommands::showOwn)
				.then(Commands.argument("tag", StringArgumentType.word())
					.executes(context -> showTag(context.getSource(), StringArgumentType.getString(context, "tag")))))) {
			root.then(node);
		}
		dispatcher.register(root);

		for (LiteralArgumentBuilder<CommandSourceStack> alias : List.of(
			create("zaloz"), invite("zapros"), join("dolacz"), kick("wyrzuc"), leave("opusc"), deputy("zastepca"))) {
			dispatcher.register(alias.requires(ClanCommands::enabled));
		}

		dispatcher.register(Commands.literal("k")
			.requires(ClanCommands::enabled)
			.then(Commands.argument("wiadomosc", StringArgumentType.greedyString())
				.executes(ClanCommands::chat)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> create(String name) {
		return Commands.literal(name)
			.then(Commands.argument("tag", StringArgumentType.word())
				.then(Commands.argument("nazwa", StringArgumentType.greedyString())
					.executes(ClanCommands::create)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> invite(String name) {
		return Commands.literal(name)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ClanCommands::invite));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> join(String name) {
		return Commands.literal(name)
			.then(Commands.argument("tag", StringArgumentType.word())
				.executes(ClanCommands::join));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> kick(String name) {
		return Commands.literal(name)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ClanCommands::kick));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> leave(String name) {
		return Commands.literal(name).executes(ClanCommands::leave);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> deputy(String name) {
		return Commands.literal(name)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ClanCommands::deputy));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> transfer(String name) {
		return Commands.literal(name)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(ClanCommands::transfer));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> disband(String name) {
		return Commands.literal(name).executes(ClanCommands::disband);
	}

	private static int showHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayer();
		context.getSource().sendSuccess(() -> Chat.info(Msg.CLAN_HELP), false);
		if (player != null) {
			EasyTP.clans().clanOf(player.getUUID()).ifPresent(clan -> sendInfo(context.getSource(), clan));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int showOwn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ClanStore.Clan clan = EasyTP.clans().clanOf(player.getUUID()).orElseThrow(() -> Chat.fail(Msg.CLAN_NOT_IN));
		sendInfo(context.getSource(), clan);
		return Command.SINGLE_SUCCESS;
	}

	private static int showTag(CommandSourceStack source, String tag) throws CommandSyntaxException {
		ClanStore.Clan clan = EasyTP.clans().byTag(tag).orElseThrow(() -> Chat.fail(Msg.CLAN_UNKNOWN, tag));
		sendInfo(source, clan);
		return Command.SINGLE_SUCCESS;
	}

	private static void sendInfo(CommandSourceStack source, ClanStore.Clan clan) {
		ClanTop.Score score = ClanTop.score(clan);
		List<String> deputies = new ArrayList<>();
		clan.deputies.forEach(id -> deputies.add(clan.members.get(id)));
		source.sendSuccess(() -> Chat.info(Msg.CLAN_INFO_HEADER, clan.tag, clan.name), false);
		source.sendSuccess(() -> Chat.info(Msg.CLAN_INFO_LEADER, clan.members.get(clan.leader)), false);
		if (!deputies.isEmpty()) {
			source.sendSuccess(() -> Chat.info(Msg.CLAN_INFO_DEPUTIES, String.join(", ", deputies)), false);
		}
		source.sendSuccess(() -> Chat.info(Msg.CLAN_INFO_MEMBERS, clan.members.size(), EasyTP.config().clans.maxMembers, String.join(", ", clan.members.values())), false);
		source.sendSuccess(() -> Chat.info(Msg.CLAN_INFO_POINTS, score.kills(), score.deaths()), false);
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		List<ClanStore.Clan> clans = ClanTop.sorted();
		if (clans.isEmpty()) {
			context.getSource().sendSuccess(() -> Chat.info(Msg.CLAN_LIST_EMPTY), false);
			return Command.SINGLE_SUCCESS;
		}
		context.getSource().sendSuccess(() -> Chat.info(Msg.CLAN_LIST_HEADER, clans.size()), false);
		for (ClanStore.Clan clan : clans) {
			long kills = ClanTop.score(clan).kills();
			context.getSource().sendSuccess(() -> Chat.info(Msg.CLAN_LIST_LINE, clan.tag, clan.name, clan.members.size(), kills), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		EasyTPConfig config = EasyTP.config();
		long cost = config.economy.enabled ? config.clans.createCost : 0;
		PlayerStore players = EasyTP.players();
		long have = players.get(player.getUUID()).money;
		if (have < cost) {
			throw Chat.fail(Msg.NO_MONEY, Shop.money(cost), Shop.money(have));
		}
		String tag = StringArgumentType.getString(context, "tag");
		String name = StringArgumentType.getString(context, "nazwa");
		ClanStore store = EasyTP.clans();
		check(store.create(tag, name, player.getUUID(), player.getPlainTextName()), tag, name);
		if (cost > 0) {
			players.withdraw(player.getUUID(), cost);
		}
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElseThrow();
		ClanTeams.update(context.getSource().getServer(), clan);
		context.getSource().getServer().getPlayerList().broadcastSystemMessage(Chat.ok(Msg.CLAN_CREATED, clan.tag, clan.name, player.getPlainTextName()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		ClanStore store = EasyTP.clans();
		int seconds = EasyTP.config().clans.inviteSeconds;
		check(store.invite(player.getUUID(), target.id(), seconds * 1000L), target.name());
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElseThrow();
		context.getSource().sendSuccess(() -> Chat.ok(Msg.CLAN_INVITED, target.name(), seconds), false);
		ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.info(Msg.CLAN_INVITE_NOTICE, clan.tag, clan.name, clan.tag));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int join(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String tag = StringArgumentType.getString(context, "tag");
		ClanStore store = EasyTP.clans();
		check(store.join(tag, player.getUUID(), player.getPlainTextName(), EasyTP.config().clans.maxMembers), tag);
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElseThrow();
		ClanTeams.update(context.getSource().getServer(), clan);
		broadcast(context.getSource().getServer(), clan, Chat.ok(Msg.CLAN_JOINED, player.getPlainTextName()));
		return Command.SINGLE_SUCCESS;
	}

	private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		ClanStore store = EasyTP.clans();
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElse(null);
		check(store.kick(player.getUUID(), target.id()), target.name());
		MinecraftServer server = context.getSource().getServer();
		ClanTeams.update(server, clan);
		broadcast(server, clan, Chat.info(Msg.CLAN_KICKED, target.name()));
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.error(Msg.CLAN_KICK_NOTICE, clan.tag));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ClanStore store = EasyTP.clans();
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElse(null);
		check(store.leave(player.getUUID()));
		MinecraftServer server = context.getSource().getServer();
		ClanTeams.update(server, clan);
		broadcast(server, clan, Chat.info(Msg.CLAN_LEFT, player.getPlainTextName()));
		context.getSource().sendSuccess(() -> Chat.ok(Msg.CLAN_LEFT, player.getPlainTextName()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int deputy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		ClanStore store = EasyTP.clans();
		check(store.toggleDeputy(player.getUUID(), target.id()), target.name());
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElseThrow();
		broadcast(context.getSource().getServer(), clan,
			Chat.info(clan.deputies.contains(target.id()) ? Msg.CLAN_DEPUTY_ON : Msg.CLAN_DEPUTY_OFF, target.name()));
		return Command.SINGLE_SUCCESS;
	}

	private static int transfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		ClanStore store = EasyTP.clans();
		check(store.transferLeader(player.getUUID(), target.id()), target.name());
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElseThrow();
		broadcast(context.getSource().getServer(), clan, Chat.ok(Msg.CLAN_LEADER_CHANGED, target.name()));
		return Command.SINGLE_SUCCESS;
	}

	private static int disband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ClanStore store = EasyTP.clans();
		ClanStore.Clan clan = store.clanOf(player.getUUID()).orElse(null);
		check(store.disband(player.getUUID()));
		MinecraftServer server = context.getSource().getServer();
		ClanTeams.remove(server, clan.tag);
		server.getPlayerList().broadcastSystemMessage(Chat.info(Msg.CLAN_DISBANDED, clan.tag), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int chat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ChatGuard.ensureNotMuted(player);
		ClanStore.Clan clan = EasyTP.clans().clanOf(player.getUUID()).orElseThrow(() -> Chat.fail(Msg.CLAN_NOT_IN));
		MutableComponent line = Component.literal(Msg.CLAN_CHAT.text(player.getPlainTextName(), StringArgumentType.getString(context, "wiadomosc")))
			.withStyle(ChatFormatting.AQUA);
		broadcast(context.getSource().getServer(), clan, line);
		EasyTP.LOGGER.info("[{}] {}", clan.tag, line.getString());
		return Command.SINGLE_SUCCESS;
	}

	private static void broadcast(MinecraftServer server, ClanStore.Clan clan, Component message) {
		for (UUID id : clan.members.keySet()) {
			ServerPlayer member = server.getPlayerList().getPlayer(id);
			if (member != null) {
				member.sendSystemMessage(message);
			}
		}
	}

	private static void check(ClanStore.Result result, Object... args) throws CommandSyntaxException {
		String first = args.length > 0 ? args[0].toString() : "";
		switch (result) {
			case OK -> {
			}
			case BAD_TAG -> throw Chat.fail(Msg.CLAN_BAD_TAG);
			case BAD_NAME -> throw Chat.fail(Msg.CLAN_BAD_NAME);
			case TAG_TAKEN -> throw Chat.fail(Msg.CLAN_TAG_TAKEN, first);
			case NAME_TAKEN -> throw Chat.fail(Msg.CLAN_NAME_TAKEN, args.length > 1 ? args[1].toString() : first);
			case ALREADY_IN_CLAN -> throw Chat.fail(Msg.CLAN_ALREADY);
			case NOT_IN_CLAN -> throw Chat.fail(Msg.CLAN_NOT_IN);
			case NO_PERMISSION -> throw Chat.fail(Msg.CLAN_NO_PERMISSION);
			case TARGET_IN_CLAN -> throw Chat.fail(Msg.CLAN_TARGET_IN_CLAN, first);
			case TARGET_NOT_MEMBER -> throw Chat.fail(Msg.CLAN_TARGET_NOT_MEMBER, first);
			case SELF -> throw Chat.fail(Msg.CLAN_SELF);
			case UNKNOWN_CLAN -> throw Chat.fail(Msg.CLAN_UNKNOWN, first);
			case NOT_INVITED -> throw Chat.fail(Msg.CLAN_NOT_INVITED, first);
			case FULL -> throw Chat.fail(Msg.CLAN_FULL, EasyTP.config().clans.maxMembers);
			case LEADER_MUST_TRANSFER -> throw Chat.fail(Msg.CLAN_LEADER_LEAVE);
		}
	}

	private static NameAndId target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Collection<NameAndId> found = GameProfileArgument.getGameProfiles(context, "gracz");
		if (found.size() != 1) {
			throw Chat.fail(Msg.MOD_ONE_PLAYER);
		}
		return found.iterator().next();
	}
}
