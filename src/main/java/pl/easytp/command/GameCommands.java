package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.clan.ClanStore;
import pl.easytp.clan.ClanTop;
import pl.easytp.economy.Market;
import pl.easytp.economy.Shop;
import pl.easytp.lifesteal.HeartItems;
import pl.easytp.lifesteal.HeartShop;
import pl.easytp.lifesteal.LifeSteal;
import pl.easytp.player.PlayerData;
import pl.easytp.player.PlayerStore;
import pl.easytp.stats.PlayerStats;
import pl.easytp.stats.Topki;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class GameCommands {
	private GameCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		registerHearts(dispatcher);
		registerMoney(dispatcher);
		registerStats(dispatcher);
	}

	private static boolean hearts(CommandSourceStack source) {
		return EasyTP.config().lifesteal.enabled;
	}

	private static boolean economy(CommandSourceStack source) {
		return EasyTP.config().economy.enabled;
	}

	private static boolean admin(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static void registerHearts(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("serca")
			.requires(GameCommands::hearts)
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				PlayerData data = EasyTP.players().get(player.getUUID());
				context.getSource().sendSuccess(() -> Chat.info(Msg.HEARTS_SELF, LifeSteal.hearts(player.getUUID()), LifeSteal.cap(data)), false);
				return Command.SINGLE_SUCCESS;
			})
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(context -> {
					NameAndId target = target(context);
					PlayerData data = EasyTP.players().touch(target.id(), target.name());
					context.getSource().sendSuccess(() -> Chat.info(Msg.HEARTS_OTHER, target.name(), LifeSteal.hearts(target.id()), LifeSteal.cap(data)), false);
					return Command.SINGLE_SUCCESS;
				}))
			.then(Commands.literal("ustaw")
				.requires(GameCommands::admin)
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
					.then(Commands.argument("ile", IntegerArgumentType.integer(1, 1000))
						.executes(context -> {
							NameAndId target = target(context);
							PlayerData data = EasyTP.players().touch(target.id(), target.name());
							LifeSteal.setHearts(target.id(), IntegerArgumentType.getInteger(context, "ile"));
							ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(target.id());
							if (online != null) {
								LifeSteal.applyHealth(online, false);
							}
							context.getSource().sendSuccess(() -> Chat.ok(Msg.HEARTS_OTHER, target.name(), LifeSteal.hearts(target.id()), LifeSteal.cap(data)), true);
							return Command.SINGLE_SUCCESS;
						})))));

		dispatcher.register(Commands.literal("wyplacserce")
			.requires(GameCommands::hearts)
			.executes(context -> withdrawHearts(context.getSource(), 1))
			.then(Commands.argument("ile", IntegerArgumentType.integer(1, 1000))
				.executes(context -> withdrawHearts(context.getSource(), IntegerArgumentType.getInteger(context, "ile")))));

		dispatcher.register(Commands.literal("sklepzaserca")
			.requires(GameCommands::hearts)
			.executes(context -> {
				HeartShop.open(context.getSource().getPlayerOrException());
				return Command.SINGLE_SUCCESS;
			}));
	}

	private static int withdrawHearts(CommandSourceStack source, int count) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int min = EasyTP.config().lifesteal.minHearts;
		int hearts = LifeSteal.hearts(player.getUUID());
		if (hearts - count < min) {
			throw Chat.fail(Msg.WITHDRAW_TOO_MANY, count, min);
		}
		LifeSteal.setHearts(player.getUUID(), hearts - count);
		LifeSteal.applyHealth(player, false);
		int left = count;
		while (left > 0) {
			int part = Math.min(left, 64);
			Shop.give(player, HeartItems.heart(part));
			left -= part;
		}
		source.sendSuccess(() -> Chat.ok(Msg.WITHDRAW_OK, count, hearts - count), false);
		return count;
	}

	private static void registerMoney(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("stan")
			.requires(GameCommands::economy)
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				long money = EasyTP.players().get(player.getUUID()).money;
				context.getSource().sendSuccess(() -> Chat.info(Msg.MONEY_SELF, Shop.money(money)), false);
				return Command.SINGLE_SUCCESS;
			})
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(context -> {
					NameAndId target = target(context);
					long money = EasyTP.players().touch(target.id(), target.name()).money;
					context.getSource().sendSuccess(() -> Chat.info(Msg.MONEY_OTHER, target.name(), Shop.money(money)), false);
					return Command.SINGLE_SUCCESS;
				})));

		for (String name : List.of("pay", "przelej")) {
			dispatcher.register(Commands.literal(name)
				.requires(GameCommands::economy)
				.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
					.then(Commands.argument("kwota", LongArgumentType.longArg(1))
						.executes(GameCommands::pay))));
		}

		dispatcher.register(Commands.literal("baltop")
			.requires(GameCommands::economy)
			.executes(context -> sendTop(context.getSource(), Topki.Category.MONEY)));

		dispatcher.register(moneyAdmin("eco"));

		dispatcher.register(Commands.literal("sprzedaj")
			.requires(GameCommands::economy)
			.executes(context -> Shop.sellHeld(context.getSource().getPlayerOrException()) ? Command.SINGLE_SUCCESS : 0)
			.then(Commands.literal("wszystko")
				.executes(context -> Shop.sellAll(context.getSource().getPlayerOrException()) ? Command.SINGLE_SUCCESS : 0)));

		dispatcher.register(Commands.literal("sklep")
			.requires(GameCommands::economy)
			.executes(context -> {
				Shop.open(context.getSource().getPlayerOrException(), 0);
				return Command.SINGLE_SUCCESS;
			}));

		dispatcher.register(Commands.literal("rynek")
			.requires(GameCommands::economy)
			.executes(context -> {
				Market.open(context.getSource().getPlayerOrException(), 0);
				return Command.SINGLE_SUCCESS;
			})
			.then(Commands.literal("wystaw")
				.then(Commands.argument("cena", LongArgumentType.longArg(1))
					.executes(context -> Market.list(context.getSource().getPlayerOrException(), LongArgumentType.getLong(context, "cena")) ? Command.SINGLE_SUCCESS : 0))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> moneyAdmin(String name) {
		return Commands.literal(name)
			.requires(source -> economy(source) && admin(source))
			.then(moneyAction("daj", 1))
			.then(moneyAction("zabierz", -1))
			.then(moneyAction("ustaw", 0));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> moneyAction(String name, int mode) {
		return Commands.literal(name)
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.then(Commands.argument("kwota", LongArgumentType.longArg(0))
					.executes(context -> {
						NameAndId target = target(context);
						long amount = LongArgumentType.getLong(context, "kwota");
						PlayerStore players = EasyTP.players();
						long current = players.touch(target.id(), target.name()).money;
						long result;
						if (mode > 0) {
							result = players.deposit(target.id(), amount);
						} else if (mode < 0) {
							result = players.setMoney(target.id(), current - amount);
						} else {
							result = players.setMoney(target.id(), amount);
						}
						context.getSource().sendSuccess(() -> Chat.ok(Msg.MONEY_OTHER, target.name(), Shop.money(result)), true);
						return Command.SINGLE_SUCCESS;
					})));
	}

	private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		NameAndId target = target(context);
		long amount = LongArgumentType.getLong(context, "kwota");
		if (player.getUUID().equals(target.id())) {
			throw Chat.fail(Msg.PAY_SELF);
		}
		PlayerStore players = EasyTP.players();
		long have = players.get(player.getUUID()).money;
		if (have < amount) {
			throw Chat.fail(Msg.NO_MONEY, Shop.money(amount), Shop.money(have));
		}
		players.touch(target.id(), target.name());
		if (!players.transfer(player.getUUID(), target.id(), amount)) {
			throw Chat.fail(Msg.PAY_FAILED);
		}
		context.getSource().sendSuccess(() -> Chat.ok(Msg.PAY_OK, Shop.money(amount), target.name()), false);
		ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Chat.ok(Msg.PAY_RECEIVED, player.getPlainTextName(), Shop.money(amount)));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void registerStats(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("statystyki")
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				return sendStats(context.getSource(), player.getUUID(), player.getPlainTextName());
			})
			.then(Commands.argument("gracz", GameProfileArgument.gameProfile())
				.executes(context -> {
					NameAndId target = target(context);
					return sendStats(context.getSource(), target.id(), target.name());
				})));

		LiteralArgumentBuilder<CommandSourceStack> topki = Commands.literal("topki")
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayer();
				if (player == null) {
					context.getSource().sendFailure(Chat.error(Msg.TOP_CONSOLE));
					return 0;
				}
				Topki.open(player);
				return Command.SINGLE_SUCCESS;
			});
		for (Topki.Category category : Topki.Category.values()) {
			topki.then(Commands.literal(category.command)
				.executes(context -> sendTop(context.getSource(), category)));
		}
		topki.then(Commands.literal("klany")
			.requires(source -> EasyTP.config().clans.enabled)
			.executes(context -> sendClanTop(context.getSource())));
		dispatcher.register(topki);
	}

	private static int sendClanTop(CommandSourceStack source) {
		PlayerStats.syncAll(source.getServer());
		source.sendSuccess(() -> Chat.info(Msg.TOP_HEADER, Msg.TOP_CLANS.text()), false);
		List<ClanStore.Clan> clans = ClanTop.sorted();
		if (clans.isEmpty()) {
			source.sendSuccess(() -> Chat.info(Msg.TOP_EMPTY), false);
		}
		for (int i = 0; i < Math.min(clans.size(), Topki.TEXT_SIZE); i++) {
			int place = i + 1;
			ClanStore.Clan clan = clans.get(i);
			source.sendSuccess(() -> Chat.info(Msg.TOP_LINE, place, "[" + clan.tag + "] " + clan.name, Long.toString(ClanTop.score(clan).kills())), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int sendStats(CommandSourceStack source, java.util.UUID id, String name) {
		PlayerStats.syncAll(source.getServer());
		PlayerData data = EasyTP.players().touch(id, name);
		String ratio = String.format(Locale.ROOT, "%.2f", data.deaths == 0 ? (double) data.kills : (double) data.kills / data.deaths);
		source.sendSuccess(() -> Chat.info(Msg.STATS_HEADER, name), false);
		if (EasyTP.config().lifesteal.enabled) {
			source.sendSuccess(() -> Chat.info(Msg.STATS_HEARTS, LifeSteal.hearts(id)), false);
		}
		if (EasyTP.config().economy.enabled) {
			source.sendSuccess(() -> Chat.info(Msg.STATS_MONEY, Shop.money(data.money), Shop.money(data.earned), Shop.money(data.spent)), false);
		}
		source.sendSuccess(() -> Chat.info(Msg.STATS_KILLS, data.kills, data.deaths, ratio, data.mobKills), false);
		source.sendSuccess(() -> Chat.info(Msg.STATS_TIME, PlayerStats.time(data.playTicks)), false);
		source.sendSuccess(() -> Chat.info(Msg.STATS_BLOCKS, data.blocksMined, data.stoneMined, data.blocksPlaced), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int sendTop(CommandSourceStack source, Topki.Category category) {
		PlayerStats.syncAll(source.getServer());
		source.sendSuccess(() -> Chat.info(Msg.TOP_HEADER, category.title()), false);
		List<PlayerStore.Entry> top = Topki.top(category, Topki.TEXT_SIZE);
		if (top.isEmpty()) {
			source.sendSuccess(() -> Chat.info(Msg.TOP_EMPTY), false);
		}
		for (int i = 0; i < top.size(); i++) {
			int place = i + 1;
			PlayerData data = top.get(i).data();
			source.sendSuccess(() -> Chat.info(Msg.TOP_LINE, place, data.name, category.format(data)), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static NameAndId target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Collection<NameAndId> found = GameProfileArgument.getGameProfiles(context, "gracz");
		if (found.size() != 1) {
			throw Chat.fail(Msg.MOD_ONE_PLAYER);
		}
		return found.iterator().next();
	}
}
