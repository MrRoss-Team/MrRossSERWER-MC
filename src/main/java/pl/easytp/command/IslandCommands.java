package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.economy.Shop;
import pl.easytp.gui.ChestGui;
import pl.easytp.oneblock.IslandStore;
import pl.easytp.oneblock.OneBlock;
import pl.easytp.oneblock.Themes;
import pl.easytp.player.PlayerStore;
import pl.easytp.session.Realms;
import pl.easytp.teleport.TeleportService.Destination;
import pl.easytp.teleport.TeleportService.TeleportException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IslandCommands {
	private static final int MAX_SIZE_LEVEL = 3;
	private static final int MAX_MEMBER_LEVEL = 8;
	private static final int SLOT_SIZE = 20;
	private static final int SLOT_MEMBERS = 24;
	private static final int SLOT_TREASURY = 49;

	private IslandCommands() {
	}

	private static boolean enabled(CommandSourceStack source) {
		return EasyTP.config().oneblock.enabled;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String name : List.of("is", "wyspa", "baza")) {
			dispatcher.register(island(name));
		}
		dispatcher.register(Commands.literal("motyw")
			.requires(IslandCommands::enabled)
			.executes(IslandCommands::listThemes)
			.then(Commands.argument("nazwa", StringArgumentType.word())
				.suggests((context, builder) -> {
					List<String> names = new ArrayList<>(List.of("auto"));
					Themes.all().forEach(theme -> names.add(theme.id()));
					return SharedSuggestionProvider.suggest(names, builder);
				})
				.executes(IslandCommands::setTheme)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> island(String name) {
		return Commands.literal(name)
			.requires(IslandCommands::enabled)
			.executes(IslandCommands::goHome)
			.then(Commands.literal("pomoc").executes(context -> {
				context.getSource().sendSuccess(() -> Chat.info(Msg.IS_HELP), false);
				return Command.SINGLE_SUCCESS;
			}))
			.then(Commands.literal("stworz").executes(IslandCommands::create))
			.then(Commands.literal("zapros")
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile()).executes(IslandCommands::invite)))
			.then(Commands.literal("dolacz").executes(IslandCommands::join))
			.then(Commands.literal("wyrzuc")
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile()).executes(IslandCommands::kick)))
			.then(Commands.literal("opusc").executes(IslandCommands::leave))
			.then(Commands.literal("usun")
				.executes(context -> {
					context.getSource().sendSuccess(() -> Chat.info(Msg.IS_DELETE_CONFIRM), false);
					return Command.SINGLE_SUCCESS;
				})
				.then(Commands.literal("potwierdz").executes(IslandCommands::delete)))
			.then(Commands.literal("info")
				.executes(context -> info(context.getSource(), own(context.getSource())))
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
					.executes(context -> info(context.getSource(), ofPlayer(context)))))
			.then(Commands.literal("odwiedz")
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile()).executes(IslandCommands::visit)))
			.then(Commands.literal("warp")
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile()).executes(IslandCommands::visit)))
			.then(Commands.literal("ulepszenia").executes(context -> {
				openUpgrades(context.getSource().getPlayerOrException());
				return Command.SINGLE_SUCCESS;
			}))
			.then(Commands.literal("skarbiec")
				.executes(context -> {
					IslandStore.Island island = own(context.getSource());
					context.getSource().sendSuccess(() -> Chat.info(Msg.IS_TREASURY, Shop.money(island.treasury)), false);
					return Command.SINGLE_SUCCESS;
				})
				.then(Commands.literal("wplac")
					.then(Commands.argument("kwota", LongArgumentType.longArg(1)).executes(IslandCommands::deposit))))
			.then(Commands.literal("topki").executes(IslandCommands::top));
	}

	private static IslandStore.Island own(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return EasyTP.islands().islandOf(player.getUUID()).orElseThrow(() -> Chat.fail(Msg.IS_NONE));
	}

	private static NameAndId target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Collection<NameAndId> found = GameProfileArgument.getGameProfiles(context, "gracz");
		if (found.size() != 1) {
			throw Chat.fail(Msg.MOD_ONE_PLAYER);
		}
		return found.iterator().next();
	}

	private static IslandStore.Island ofPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		NameAndId target = target(context);
		return EasyTP.islands().islandOf(target.id()).orElseThrow(() -> Chat.fail(Msg.IS_PLAYER_NO_ISLAND, target.name()));
	}

	private static Destination destinationOf(MinecraftServer server, IslandStore.Island island) throws TeleportException {
		Optional<OneBlock.Spot> spot = OneBlock.spotOf(server, island);
		if (spot.isEmpty()) {
			throw new TeleportException(Msg.IS_NO_WORLD.text());
		}
		return new Destination(spot.get().level(), spot.get().pos(), 0, 0, false);
	}

	private static int goHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Optional<IslandStore.Island> island = EasyTP.islands().islandOf(player.getUUID());
		if (island.isEmpty()) {
			context.getSource().sendSuccess(() -> Chat.info(Msg.IS_NONE), false);
			context.getSource().sendSuccess(() -> Chat.info(Msg.IS_HELP), false);
			return Command.SINGLE_SUCCESS;
		}
		MinecraftServer server = context.getSource().getServer();
		EasyTP.teleports().begin(player, () -> destinationOf(server, island.get()), Chat.ok(Msg.IS_TELEPORTED));
		return Command.SINGLE_SUCCESS;
	}

	private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		IslandStore store = EasyTP.islands();
		if (store.islandOf(player.getUUID()).isPresent()) {
			throw Chat.fail(Msg.IS_ALREADY);
		}
		MinecraftServer server = context.getSource().getServer();
		ServerLevel level = server.getLevel(Realms.ONEBLOCK_LEVEL);
		if (level == null) {
			throw Chat.fail(Msg.IS_NO_WORLD);
		}
		IslandStore.Island island = store.create(player.getUUID(), player.getPlainTextName());
		OneBlock.build(level, island);
		EasyTP.teleports().begin(player, () -> destinationOf(server, island), Chat.ok(Msg.IS_CREATED));
		return Command.SINGLE_SUCCESS;
	}

	private static void check(IslandStore.Result result, String name) throws CommandSyntaxException {
		switch (result) {
			case OK -> {
			}
			case ALREADY_HAS -> throw Chat.fail(Msg.IS_ALREADY);
			case NONE -> throw Chat.fail(Msg.IS_NONE);
			case NOT_OWNER -> throw Chat.fail(Msg.IS_NOT_OWNER);
			case SELF -> throw Chat.fail(Msg.CLAN_SELF);
			case TARGET_HAS -> throw Chat.fail(Msg.IS_TARGET_HAS, name);
			case NOT_MEMBER -> throw Chat.fail(Msg.IS_NOT_MEMBER, name);
			case NOT_INVITED -> throw Chat.fail(Msg.IS_NOT_INVITED);
			case FULL -> throw Chat.fail(Msg.IS_FULL);
			case OWNER_CANNOT_LEAVE -> throw Chat.fail(Msg.IS_OWNER_LEAVE);
		}
	}

	private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		int seconds = EasyTP.config().oneblock.inviteSeconds;
		check(EasyTP.islands().invite(player.getUUID(), target.id(), seconds * 1000L), target.name());
		context.getSource().sendSuccess(() -> Chat.ok(Msg.IS_INVITED, target.name(), seconds), false);
		ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.info(Msg.IS_INVITE_NOTICE, player.getPlainTextName()));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int join(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		IslandStore store = EasyTP.islands();
		check(store.join(player.getUUID(), player.getPlainTextName()), player.getPlainTextName());
		IslandStore.Island island = store.islandOf(player.getUUID()).orElseThrow();
		MinecraftServer server = context.getSource().getServer();
		OneBlock.broadcast(server, island, Chat.ok(Msg.IS_JOINED, player.getPlainTextName()));
		EasyTP.teleports().begin(player, () -> destinationOf(server, island), Chat.ok(Msg.IS_TELEPORTED));
		return Command.SINGLE_SUCCESS;
	}

	private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		IslandStore store = EasyTP.islands();
		Optional<IslandStore.Island> island = store.islandOf(player.getUUID());
		check(store.kick(player.getUUID(), target.id()), target.name());
		MinecraftServer server = context.getSource().getServer();
		OneBlock.broadcast(server, island.orElseThrow(), Chat.info(Msg.IS_KICKED, target.name()));
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.error(Msg.IS_KICK_NOTICE));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		IslandStore store = EasyTP.islands();
		Optional<IslandStore.Island> island = store.islandOf(player.getUUID());
		check(store.leave(player.getUUID()), player.getPlainTextName());
		OneBlock.broadcast(context.getSource().getServer(), island.orElseThrow(), Chat.info(Msg.IS_LEFT, player.getPlainTextName()));
		context.getSource().sendSuccess(() -> Chat.info(Msg.IS_LEFT, player.getPlainTextName()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int delete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		IslandStore store = EasyTP.islands();
		Optional<IslandStore.Island> island = store.islandOf(player.getUUID());
		check(store.delete(player.getUUID()), player.getPlainTextName());
		MinecraftServer server = context.getSource().getServer();
		OneBlock.broadcast(server, island.orElseThrow(), Chat.info(Msg.IS_DELETED));
		ServerLevel level = server.getLevel(Realms.ONEBLOCK_LEVEL);
		if (level != null) {
			OneBlock.clear(level, island.get());
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int info(CommandSourceStack source, IslandStore.Island island) {
		int level = island.level();
		int half = IslandStore.halfSize(island.sizeLevel) * 2;
		source.sendSuccess(() -> Chat.info(Msg.IS_INFO_HEADER, island.ownerName(), level, IslandStore.progressOf(island.blocks), IslandStore.blocksForLevel(level), Themes.active(island).name().text()), false);
		source.sendSuccess(() -> Chat.info(Msg.IS_INFO_MEMBERS, island.members.size(), IslandStore.memberLimit(island.memberLevel), String.join(", ", island.members.values())), false);
		source.sendSuccess(() -> Chat.info(Msg.IS_INFO_SIZE, half, half, Shop.money(island.treasury), island.blocks), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int visit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		IslandStore.Island island = ofPlayer(context);
		MinecraftServer server = context.getSource().getServer();
		String owner = island.ownerName();
		EasyTP.teleports().begin(player, () -> destinationOf(server, island), Chat.ok(Msg.IS_VISIT, owner));
		return Command.SINGLE_SUCCESS;
	}

	private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		IslandStore.Island island = own(context.getSource());
		long amount = LongArgumentType.getLong(context, "kwota");
		PlayerStore players = EasyTP.players();
		long have = players.get(player.getUUID()).money;
		if (!players.withdraw(player.getUUID(), amount)) {
			throw Chat.fail(Msg.NO_MONEY, Shop.money(amount), Shop.money(have));
		}
		EasyTP.islands().deposit(island, amount);
		context.getSource().sendSuccess(() -> Chat.ok(Msg.IS_DEPOSIT, Shop.money(amount), Shop.money(island.treasury)), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int top(CommandContext<CommandSourceStack> context) {
		List<IslandStore.Island> islands = EasyTP.islands().top();
		context.getSource().sendSuccess(() -> Chat.info(Msg.IS_TOP_TITLE), false);
		if (islands.isEmpty()) {
			context.getSource().sendSuccess(() -> Chat.info(Msg.TOP_EMPTY), false);
		}
		for (int i = 0; i < Math.min(10, islands.size()); i++) {
			int place = i + 1;
			IslandStore.Island island = islands.get(i);
			context.getSource().sendSuccess(() -> Chat.info(Msg.IS_TOP_LINE, place, island.ownerName(), island.level(), island.blocks), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int listThemes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		IslandStore.Island island = own(context.getSource());
		int level = island.level();
		Themes.Theme active = Themes.active(island);
		context.getSource().sendSuccess(() -> Chat.info(Msg.IS_THEME_HEADER), false);
		for (Themes.Theme theme : Themes.all()) {
			String mark = theme == active ? Msg.IS_THEME_ACTIVE.text() : level < theme.level() ? Msg.IS_THEME_LOCKED.text() : "";
			context.getSource().sendSuccess(() -> Chat.info(Msg.IS_THEME_LINE, theme.id(), theme.name().text(), theme.level(), mark), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int setTheme(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		IslandStore.Island island = own(context.getSource());
		String name = StringArgumentType.getString(context, "nazwa");
		if (name.equalsIgnoreCase("auto")) {
			island.theme = "";
			EasyTP.islands().touch();
			context.getSource().sendSuccess(() -> Chat.ok(Msg.IS_THEME_AUTO), false);
			return Command.SINGLE_SUCCESS;
		}
		Optional<Themes.Theme> theme = Themes.byId(name).filter(found -> island.level() >= found.level());
		if (theme.isEmpty()) {
			throw Chat.fail(Msg.IS_THEME_UNKNOWN, name);
		}
		island.theme = theme.get().id();
		EasyTP.islands().touch();
		context.getSource().sendSuccess(() -> Chat.ok(Msg.IS_THEME_SET, theme.get().name().text()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static long sizeCost(int level) {
		return 2000L * (long) Math.pow(4, level);
	}

	private static long memberCost(int level) {
		return 1000L * (level + 1) * (level + 1);
	}

	private static void openUpgrades(ServerPlayer player) throws CommandSyntaxException {
		IslandStore.Island island = EasyTP.islands().islandOf(player.getUUID()).orElseThrow(() -> Chat.fail(Msg.IS_NONE));
		Map<Integer, ChestGui.Entry> slots = new HashMap<>();
		int size = IslandStore.halfSize(island.sizeLevel) * 2;
		int nextSize = IslandStore.halfSize(island.sizeLevel + 1) * 2;
		ItemStack sizeIcon = island.sizeLevel >= MAX_SIZE_LEVEL
			? ChestGui.icon(Items.GRASS_BLOCK, Msg.IS_UPGRADE_MAX.text(), ChatFormatting.GOLD)
			: ChestGui.icon(Items.GRASS_BLOCK, Msg.IS_UPGRADE_SIZE.text(size, size, nextSize, nextSize), ChatFormatting.GREEN, Msg.IS_UPGRADE_COST.text(Shop.money(sizeCost(island.sizeLevel))));
		slots.put(SLOT_SIZE, new ChestGui.Entry(sizeIcon, p -> upgrade(p, true)));
		int limit = IslandStore.memberLimit(island.memberLevel);
		ItemStack memberIcon = island.memberLevel >= MAX_MEMBER_LEVEL
			? ChestGui.icon(Items.PLAYER_HEAD, Msg.IS_UPGRADE_MAX.text(), ChatFormatting.GOLD)
			: ChestGui.icon(Items.PLAYER_HEAD, Msg.IS_UPGRADE_MEMBERS.text(limit, IslandStore.memberLimit(island.memberLevel + 1)), ChatFormatting.GREEN, Msg.IS_UPGRADE_COST.text(Shop.money(memberCost(island.memberLevel))));
		slots.put(SLOT_MEMBERS, new ChestGui.Entry(memberIcon, p -> upgrade(p, false)));
		slots.put(SLOT_TREASURY, new ChestGui.Entry(ChestGui.icon(Items.GOLD_INGOT, Msg.IS_TREASURY.text(Shop.money(island.treasury)), ChatFormatting.YELLOW), null));
		ChestGui.open(player, Component.literal(Msg.IS_UPGRADES_TITLE.text()), slots);
	}

	private static void upgrade(ServerPlayer player, boolean size) {
		Optional<IslandStore.Island> found = EasyTP.islands().islandOf(player.getUUID());
		if (found.isEmpty()) {
			return;
		}
		IslandStore.Island island = found.get();
		int level = size ? island.sizeLevel : island.memberLevel;
		if (level >= (size ? MAX_SIZE_LEVEL : MAX_MEMBER_LEVEL)) {
			return;
		}
		long cost = size ? sizeCost(level) : memberCost(level);
		if (!EasyTP.islands().spend(island, cost)) {
			player.sendSystemMessage(Chat.error(Msg.IS_TREASURY_LOW, Shop.money(cost), Shop.money(island.treasury)));
			return;
		}
		if (size) {
			island.sizeLevel++;
		} else {
			island.memberLevel++;
		}
		EasyTP.islands().touch();
		player.sendSystemMessage(Chat.ok(Msg.IS_UPGRADE_DONE));
		try {
			openUpgrades(player);
		} catch (CommandSyntaxException e) {
			EasyTP.LOGGER.warn("EasyTP: nie można odświeżyć okna ulepszeń", e);
		}
	}
}
